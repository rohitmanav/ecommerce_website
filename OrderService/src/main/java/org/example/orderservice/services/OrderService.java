package org.example.orderservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.orderservice.clients.CartClient;
import org.example.orderservice.clients.KafkaProducerClient;
import org.example.orderservice.clients.PaymentClient;
import org.example.orderservice.dtos.*;
import org.example.orderservice.exceptions.EmptyCartException;
import org.example.orderservice.exceptions.ForbiddenException;
import org.example.orderservice.exceptions.InvalidOrderStatusException;
import org.example.orderservice.exceptions.OrderNotFoundException;
import org.example.orderservice.models.*;
import org.example.orderservice.repos.OrderRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class OrderService implements IOrderService {

    public static final String ORDER_CREATED_TOPIC = "order_created";
    public static final String ORDER_NOTIFICATIONS_TOPIC = "order_notifications";
    public static final String ORDER_STATUS_UPDATES_TOPIC = "order_status_updates";
    public static final String SMS_NOTIFICATIONS_TOPIC = "sms_notifications";

    @Autowired
    private OrderRepo orderRepo;

    @Autowired
    private CartClient cartClient;

    @Autowired
    private PaymentClient paymentClient;

    @Autowired
    private KafkaProducerClient kafkaProducerClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.checkout.payment-link-wait-ms:4000}")
    private Long paymentLinkWaitMillis;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Override
    public Order checkout(UserDto userDto, String authorizationHeader, Address deliveryAddress, PaymentMethod paymentMethod) {
        CartDto cartDto = cartClient.getCart(authorizationHeader);
        if (cartDto.getItems() == null || cartDto.getItems().isEmpty()) {
            throw new EmptyCartException("Your cart is empty, add products before checking out");
        }

        Order order = new Order();
        order.setUserId(userDto.getUserId());
        order.setUserEmail(userDto.getEmail());
        order.setUserName(userDto.getName());
        order.setDeliveryAddress(deliveryAddress);
        order.setPaymentMethod(paymentMethod);

        BigDecimal total = BigDecimal.ZERO;
        for (CartDto.CartItemDto cartItemDto : cartDto.getItems()) {
            OrderItem item = new OrderItem();
            item.setProductId(cartItemDto.getProductId());
            item.setProductName(cartItemDto.getProductName());
            item.setImageUrl(cartItemDto.getImageUrl());
            item.setUnitPrice(cartItemDto.getUnitPrice());
            item.setQuantity(cartItemDto.getQuantity());
            order.addItem(item);
            total = total.add(BigDecimal.valueOf(cartItemDto.getUnitPrice()).multiply(BigDecimal.valueOf(cartItemDto.getQuantity())));
        }
        order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP).doubleValue());
        order.changeStatus(OrderStatus.PAYMENT_PENDING, "Order placed, waiting for payment");
        Order saved = orderRepo.save(order);
        publishStatusUpdate(saved);

        // The payment service consumes order_created and creates the payment link; the cart service empties the cart.
        boolean published = kafkaProducerClient.sendMessageAndWait(ORDER_CREATED_TOPIC, toJson(toOrderCreatedEvent(saved)));
        if (published) {
            Optional<Order> optionalOrder = waitForPaymentLink(saved.getId());
            if (optionalOrder.isPresent()) {
                return optionalOrder.get();
            }
            return saved;
        }

        // Kafka is unavailable: create the payment over REST and empty the cart directly.
        PaymentDto paymentDto = paymentClient.createPayment(toPaymentRequest(saved), authorizationHeader);
        if (paymentDto != null) {
            saved.setPaymentId(paymentDto.getId());
            saved.setPaymentLink(paymentDto.getPaymentLink());
            saved = orderRepo.save(saved);
        }
        cartClient.clearCart(authorizationHeader);
        return saved;
    }

    @Override
    public List<Order> getOrdersForUser(Long userId) {
        return orderRepo.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public Order getOrder(Long orderId, UserDto userDto) {
        Order order = findOrder(orderId);
        if (!order.getUserId().equals(userDto.getUserId()) && !userDto.isAdmin()) {
            throw new ForbiddenException("You can only view your own orders");
        }
        return order;
    }

    @Override
    @Transactional
    public Order cancelOrder(Long orderId, UserDto userDto) {
        Order order = getOrder(orderId, userDto);
        if (!OrderStatus.CANCELLABLE.contains(order.getStatus())) {
            throw new InvalidOrderStatusException("Order " + orderId + " is " + order.getStatus() + " and can no longer be cancelled");
        }
        boolean wasPaid = order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PACKED;
        String cancelNote;
        if (wasPaid) {
            cancelNote = "Cancelled by customer, refund will be issued to the original payment method";
        } else {
            cancelNote = "Cancelled by customer";
        }
        order.changeStatus(OrderStatus.CANCELLED, cancelNote);
        Order saved = orderRepo.save(order);
        publishStatusUpdate(saved);
        sendStatusEmail(saved);
        return saved;
    }

    @Override
    @Transactional
    public Order updateStatus(Long orderId, OrderStatus newStatus, String note) {
        Order order = findOrder(orderId);
        if (!order.getStatus().canMoveTo(newStatus)) {
            throw new InvalidOrderStatusException("Cannot move order " + orderId + " from " + order.getStatus() + " to " + newStatus);
        }
        String statusNote;
        if (note != null && !note.isBlank()) {
            statusNote = note;
        } else {
            statusNote = "Status changed to " + newStatus;
        }
        order.changeStatus(newStatus, statusNote);
        Order saved = orderRepo.save(order);
        publishStatusUpdate(saved);
        sendStatusEmail(saved);
        return saved;
    }

    @Override
    @Transactional
    public void handlePaymentEvent(PaymentEventDto paymentEventDto) {
        Optional<Order> optionalOrder = orderRepo.findById(paymentEventDto.getOrderId());
        if (optionalOrder.isEmpty()) {
            log.warn("Received payment event for unknown order {}", paymentEventDto.getOrderId());
            return;
        }
        Order order = optionalOrder.get();

        String type = paymentEventDto.getType();
        if ("LINK_CREATED".equals(type)) {
            order.setPaymentId(paymentEventDto.getPaymentId());
            order.setPaymentLink(paymentEventDto.getPaymentLink());
            orderRepo.save(order);
        } else if ("SUCCESS".equals(type)) {
            if (order.getStatus() != OrderStatus.PAYMENT_PENDING && order.getStatus() != OrderStatus.PAYMENT_FAILED) {
                log.warn("Payment succeeded for order {} in status {}", order.getId(), order.getStatus());
                return;
            }
            if (paymentEventDto.getAmountInPaise() != null && !paymentEventDto.getAmountInPaise().equals(toPaise(order.getTotalAmount()))) {
                log.error("Payment {} for order {} was {} paise but the order total is {}; not confirming",
                        paymentEventDto.getPaymentId(), order.getId(), paymentEventDto.getAmountInPaise(), order.getTotalAmount());
                return;
            }
            order.setPaymentId(paymentEventDto.getPaymentId());
            String successNote = "Payment received";
            if (paymentEventDto.getGatewayPaymentId() != null) {
                successNote = successNote + " (" + paymentEventDto.getGatewayPaymentId() + ")";
            }
            order.changeStatus(OrderStatus.CONFIRMED, successNote);
            Order saved = orderRepo.save(order);
            publishStatusUpdate(saved);
            sendConfirmationEmail(saved);
        } else if ("FAILED".equals(type)) {
            if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
                return;
            }
            String failureNote;
            if (paymentEventDto.getFailureReason() != null) {
                failureNote = paymentEventDto.getFailureReason();
            } else {
                failureNote = "Payment failed";
            }
            order.changeStatus(OrderStatus.PAYMENT_FAILED, failureNote);
            Order saved = orderRepo.save(order);
            publishStatusUpdate(saved);
            sendStatusEmail(saved);
        } else {
            log.warn("Ignoring payment event of type {}", type);
        }
    }

    private Order findOrder(Long orderId) {
        Optional<Order> optionalOrder = orderRepo.findById(orderId);
        if (optionalOrder.isEmpty()) {
            throw new OrderNotFoundException("Order " + orderId + " not found");
        }
        return optionalOrder.get();
    }

    private Optional<Order> waitForPaymentLink(Long orderId) {
        long deadline = System.currentTimeMillis() + paymentLinkWaitMillis;
        Optional<Order> optionalOrder = orderRepo.findById(orderId);
        while (optionalOrder.isPresent() && optionalOrder.get().getPaymentLink() == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            optionalOrder = orderRepo.findById(orderId);
        }
        return optionalOrder;
    }

    private OrderCreatedEventDto toOrderCreatedEvent(Order order) {
        OrderCreatedEventDto orderCreatedEventDto = new OrderCreatedEventDto();
        orderCreatedEventDto.setOrderId(order.getId());
        orderCreatedEventDto.setUserId(order.getUserId());
        orderCreatedEventDto.setUserEmail(order.getUserEmail());
        orderCreatedEventDto.setUserName(order.getUserName());
        if (order.getDeliveryAddress() != null) {
            orderCreatedEventDto.setPhoneNumber(order.getDeliveryAddress().getPhoneNumber());
        }
        orderCreatedEventDto.setAmountInPaise(toPaise(order.getTotalAmount()));
        orderCreatedEventDto.setCurrency(order.getCurrency());
        if (order.getPaymentMethod() != null) {
            orderCreatedEventDto.setPaymentMethod(order.getPaymentMethod().name());
        }
        return orderCreatedEventDto;
    }

    private PaymentRequestDto toPaymentRequest(Order order) {
        PaymentRequestDto paymentRequestDto = new PaymentRequestDto();
        paymentRequestDto.setOrderId(order.getId());
        paymentRequestDto.setAmount(toPaise(order.getTotalAmount()));
        paymentRequestDto.setName(order.getUserName());
        paymentRequestDto.setEmail(order.getUserEmail());
        if (order.getDeliveryAddress() != null) {
            paymentRequestDto.setPhoneNumber(order.getDeliveryAddress().getPhoneNumber());
        }
        if (order.getPaymentMethod() != null) {
            paymentRequestDto.setPaymentMethod(order.getPaymentMethod().name());
        }
        return paymentRequestDto;
    }

    private void publishStatusUpdate(Order order) {
        OrderStatusEventDto orderStatusEventDto = new OrderStatusEventDto();
        orderStatusEventDto.setOrderId(order.getId());
        orderStatusEventDto.setUserId(order.getUserId());
        orderStatusEventDto.setStatus(order.getStatus().name());
        orderStatusEventDto.setTotalAmount(order.getTotalAmount());
        orderStatusEventDto.setCurrency(order.getCurrency());
        orderStatusEventDto.setOccurredAt(new Date());
        kafkaProducerClient.sendMessage(ORDER_STATUS_UPDATES_TOPIC, toJson(orderStatusEventDto));
    }

    private Long toPaise(Double amount) {
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private void sendConfirmationEmail(Order order) {
        String body = "Hi " + order.getUserName() + ",\n\n";
        body += "Thank you for your order! Your payment was received and order #" + order.getId() + " is confirmed.\n\n";
        body += "Items:\n";
        for (OrderItem item : order.getItems()) {
            body += "  - " + item.getProductName() + " x " + item.getQuantity()
                    + " @ " + order.getCurrency() + " " + item.getUnitPrice() + "\n";
        }
        body += "\nTotal: " + order.getCurrency() + " " + order.getTotalAmount() + "\n";
        Address address = order.getDeliveryAddress();
        if (address != null) {
            body += "\nDelivering to:\n  " + address.getFullName() + "\n  " + address.getLine1();
            if (address.getLine2() != null && !address.getLine2().isBlank()) {
                body += ", " + address.getLine2();
            }
            body += "\n  " + address.getCity() + ", " + address.getState() + " " + address.getPostalCode() + "\n";
        }
        body += "\nYou can track your order from your order history.";
        sendEmail(order, "Order #" + order.getId() + " confirmed", body);
        sendSms(order, "Your order #" + order.getId() + " for " + order.getCurrency() + " " + order.getTotalAmount()
                + " is confirmed. Thank you for shopping with us!");
    }

    private void sendStatusEmail(Order order) {
        String body = "Hi " + order.getUserName() + ",\n\nYour order #" + order.getId() + " is now "
                + order.getStatus().name().replace('_', ' ').toLowerCase() + ".";
        if (!order.getStatusHistory().isEmpty()) {
            String note = order.getStatusHistory().get(order.getStatusHistory().size() - 1).getNote();
            if (note != null) {
                body += "\n\n" + note;
            }
        }
        sendEmail(order, "Update on order #" + order.getId(), body);
        sendSms(order, "Your order #" + order.getId() + " is now "
                + order.getStatus().name().replace('_', ' ').toLowerCase() + ".");
    }

    private void sendSms(Order order, String message) {
        String phoneNumber = order.getDeliveryAddress() != null ? order.getDeliveryAddress().getPhoneNumber() : null;
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return;
        }
        SmsDto smsDto = new SmsDto();
        smsDto.setTo(phoneNumber);
        smsDto.setMessage(message);
        kafkaProducerClient.sendMessage(SMS_NOTIFICATIONS_TOPIC, toJson(smsDto));
    }

    private void sendEmail(Order order, String subject, String body) {
        if (order.getUserEmail() == null) {
            return;
        }
        EmailDto emailDto = new EmailDto();
        emailDto.setTo(order.getUserEmail());
        emailDto.setFrom(mailFrom);
        emailDto.setSubject(subject);
        emailDto.setBody(body);
        kafkaProducerClient.sendMessage(ORDER_NOTIFICATIONS_TOPIC, toJson(emailDto));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException(exception);
        }
    }
}
