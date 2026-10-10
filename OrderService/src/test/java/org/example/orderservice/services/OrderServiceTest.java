package org.example.orderservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.orderservice.clients.CartClient;
import org.example.orderservice.clients.KafkaProducerClient;
import org.example.orderservice.clients.PaymentClient;
import org.example.orderservice.dtos.CartDto;
import org.example.orderservice.dtos.PaymentDto;
import org.example.orderservice.dtos.PaymentEventDto;
import org.example.orderservice.dtos.PaymentRequestDto;
import org.example.orderservice.dtos.UserDto;
import org.example.orderservice.exceptions.EmptyCartException;
import org.example.orderservice.exceptions.ForbiddenException;
import org.example.orderservice.exceptions.InvalidOrderStatusException;
import org.example.orderservice.models.Address;
import org.example.orderservice.models.Order;
import org.example.orderservice.models.OrderStatus;
import org.example.orderservice.models.PaymentMethod;
import org.example.orderservice.repos.OrderRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final String AUTH = "Bearer token";

    @Mock
    private OrderRepo orderRepo;

    @Mock
    private CartClient cartClient;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private KafkaProducerClient kafkaProducerClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "paymentLinkWaitMillis", 0L);
        ReflectionTestUtils.setField(orderService, "mailFrom", "no-reply@test.local");
    }

    @Test
    void checkoutWithEmptyCartFails() {
        when(cartClient.getCart(AUTH)).thenReturn(new CartDto());

        assertThrows(EmptyCartException.class, () -> orderService.checkout(user(1L), AUTH, address(), PaymentMethod.UPI));
        verify(orderRepo, never()).save(any());
    }

    @Test
    void checkoutCreatesPendingOrderWithTotalsAndPublishesOrderCreated() {
        when(cartClient.getCart(AUTH)).thenReturn(cart());
        when(orderRepo.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(100L);
            return order;
        });
        when(kafkaProducerClient.sendMessageAndWait(eq(OrderService.ORDER_CREATED_TOPIC), anyString())).thenReturn(true);
        when(orderRepo.findById(100L)).thenAnswer(invocation -> Optional.empty());

        Order order = orderService.checkout(user(1L), AUTH, address(), PaymentMethod.CARD);

        assertEquals(OrderStatus.PAYMENT_PENDING, order.getStatus());
        assertEquals(2, order.getItems().size());
        assertEquals(1039.97, order.getTotalAmount());
        assertEquals(1, order.getStatusHistory().size());
        verifyNoInteractions(paymentClient);
        verify(kafkaProducerClient).sendMessage(eq(OrderService.ORDER_STATUS_UPDATES_TOPIC),
                argThat(message -> message.contains("\"PAYMENT_PENDING\"") && message.contains("\"userId\":1")));
    }

    @Test
    void checkoutFallsBackToRestWhenKafkaIsDown() {
        when(cartClient.getCart(AUTH)).thenReturn(cart());
        when(orderRepo.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(100L);
            return order;
        });
        when(kafkaProducerClient.sendMessageAndWait(eq(OrderService.ORDER_CREATED_TOPIC), anyString())).thenReturn(false);
        PaymentDto paymentDto = new PaymentDto();
        paymentDto.setId(55L);
        paymentDto.setPaymentLink("https://rzp.io/test");
        when(paymentClient.createPayment(any(PaymentRequestDto.class), eq(AUTH))).thenReturn(paymentDto);

        Order order = orderService.checkout(user(1L), AUTH, address(), PaymentMethod.CARD);

        assertEquals("https://rzp.io/test", order.getPaymentLink());
        ArgumentCaptor<PaymentRequestDto> request = ArgumentCaptor.forClass(PaymentRequestDto.class);
        verify(paymentClient).createPayment(request.capture(), eq(AUTH));
        assertEquals(103997L, request.getValue().getAmount());
        assertEquals("CARD", request.getValue().getPaymentMethod());
        verify(cartClient).clearCart(AUTH);
    }

    @Test
    void paymentSuccessConfirmsOrderAndSendsConfirmationEmail() {
        Order order = order(OrderStatus.PAYMENT_PENDING, 1L);
        when(orderRepo.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepo.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentEventDto paymentEventDto = new PaymentEventDto();
        paymentEventDto.setType("SUCCESS");
        paymentEventDto.setOrderId(100L);
        paymentEventDto.setPaymentId(55L);

        orderService.handlePaymentEvent(paymentEventDto);

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        verify(kafkaProducerClient).sendMessage(eq(OrderService.ORDER_NOTIFICATIONS_TOPIC), anyString());
        verify(kafkaProducerClient).sendMessage(eq(OrderService.ORDER_STATUS_UPDATES_TOPIC),
                argThat(message -> message.contains("\"CONFIRMED\"")));
        verify(kafkaProducerClient, never()).sendMessage(eq(OrderService.SMS_NOTIFICATIONS_TOPIC), anyString());
    }

    @Test
    void confirmationIsAlsoSentBySmsWhenDeliveryPhoneIsKnown() {
        Order order = order(OrderStatus.PAYMENT_PENDING, 1L);
        order.setDeliveryAddress(address());
        when(orderRepo.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepo.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentEventDto paymentEventDto = new PaymentEventDto();
        paymentEventDto.setType("SUCCESS");
        paymentEventDto.setOrderId(100L);

        orderService.handlePaymentEvent(paymentEventDto);

        verify(kafkaProducerClient).sendMessage(eq(OrderService.SMS_NOTIFICATIONS_TOPIC),
                argThat(message -> message.contains("9876543210") && message.contains("#100") && message.contains("confirmed")));
    }

    @Test
    void paymentForWrongAmountDoesNotConfirmOrder() {
        Order order = order(OrderStatus.PAYMENT_PENDING, 1L);
        when(orderRepo.findById(100L)).thenReturn(Optional.of(order));
        PaymentEventDto paymentEventDto = new PaymentEventDto();
        paymentEventDto.setType("SUCCESS");
        paymentEventDto.setOrderId(100L);
        paymentEventDto.setAmountInPaise(100L);

        orderService.handlePaymentEvent(paymentEventDto);

        assertEquals(OrderStatus.PAYMENT_PENDING, order.getStatus());
        verify(orderRepo, never()).save(any());
    }

    @Test
    void adminCannotSkipFromPendingPaymentToShipped() {
        when(orderRepo.findById(100L)).thenReturn(Optional.of(order(OrderStatus.PAYMENT_PENDING, 1L)));

        assertThrows(InvalidOrderStatusException.class,
                () -> orderService.updateStatus(100L, OrderStatus.SHIPPED, null));
    }

    @Test
    void shippedOrderCannotBeCancelled() {
        when(orderRepo.findById(100L)).thenReturn(Optional.of(order(OrderStatus.SHIPPED, 1L)));

        assertThrows(InvalidOrderStatusException.class, () -> orderService.cancelOrder(100L, user(1L)));
    }

    @Test
    void usersCannotSeeOtherUsersOrders() {
        when(orderRepo.findById(100L)).thenReturn(Optional.of(order(OrderStatus.CONFIRMED, 1L)));

        assertThrows(ForbiddenException.class, () -> orderService.getOrder(100L, user(2L)));
    }

    private UserDto user(Long id) {
        UserDto userDto = new UserDto();
        userDto.setUserId(id);
        userDto.setEmail("user" + id + "@test.local");
        userDto.setName("User " + id);
        return userDto;
    }

    private Address address() {
        Address address = new Address();
        address.setFullName("Test User");
        address.setPhoneNumber("9876543210");
        address.setLine1("1 Main Road");
        address.setCity("Pune");
        address.setState("Maharashtra");
        address.setPostalCode("411001");
        address.setCountry("India");
        return address;
    }

    private CartDto cart() {
        CartDto.CartItemDto phoneCartItemDto = new CartDto.CartItemDto();
        phoneCartItemDto.setProductId(1L);
        phoneCartItemDto.setProductName("Phone");
        phoneCartItemDto.setUnitPrice(999.99);
        phoneCartItemDto.setQuantity(1);
        CartDto.CartItemDto cableCartItemDto = new CartDto.CartItemDto();
        cableCartItemDto.setProductId(2L);
        cableCartItemDto.setProductName("Cable");
        cableCartItemDto.setUnitPrice(19.99);
        cableCartItemDto.setQuantity(2);
        CartDto cartDto = new CartDto();
        cartDto.setItems(List.of(phoneCartItemDto, cableCartItemDto));
        return cartDto;
    }

    private Order order(OrderStatus status, Long userId) {
        Order order = new Order();
        order.setId(100L);
        order.setUserId(userId);
        order.setUserEmail("user@test.local");
        order.setUserName("User");
        order.setTotalAmount(100.0);
        order.changeStatus(status, "test");
        return order;
    }
}
