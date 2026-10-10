package org.example.cartservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cartservice.clients.CartCacheClient;
import org.example.cartservice.clients.KafkaProducerClient;
import org.example.cartservice.clients.ProductClient;
import org.example.cartservice.dtos.CartDto;
import org.example.cartservice.dtos.ProductDto;
import org.example.cartservice.exceptions.CartItemNotFoundException;
import org.example.cartservice.models.Cart;
import org.example.cartservice.models.CartItem;
import org.example.cartservice.repos.CartRepo;
import org.example.cartservice.utils.mappers.CartDtoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepo cartRepo;

    @Mock
    private CartCacheClient cartCacheClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private KafkaProducerClient kafkaProducerClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CartService cartService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cartService, "maxQuantityPerItem", 10);
    }

    @Test
    void addItemCreatesCartCopiesProductDetailsAndPublishesEvent() {
        when(productClient.getProduct(1L)).thenReturn(product(1L, "Phone", 500.0));
        when(cartRepo.findByUserId(42L)).thenReturn(Optional.empty());
        when(cartRepo.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cart cart = cartService.addItem(42L, 1L, 2);

        assertEquals(42L, cart.getUserId());
        assertEquals(1, cart.getItems().size());
        assertEquals("Phone", cart.getItems().get(0).getProductName());
        assertEquals(2, cart.getItems().get(0).getQuantity());
        verify(cartCacheClient).put(cart);
        verify(kafkaProducerClient).sendMessage(eq(CartService.CART_EVENTS_TOPIC), anyString());
    }

    @Test
    void addingSameProductAgainIncreasesQuantity() {
        Cart existing = cartWithItem(42L, 1L, 3);
        when(productClient.getProduct(1L)).thenReturn(product(1L, "Phone", 500.0));
        when(cartRepo.findByUserId(42L)).thenReturn(Optional.of(existing));
        when(cartRepo.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cart cart = cartService.addItem(42L, 1L, 2);

        assertEquals(1, cart.getItems().size());
        assertEquals(5, cart.getItems().get(0).getQuantity());
    }

    @Test
    void addItemRejectsQuantityAboveLimit() {
        Cart existing = cartWithItem(42L, 1L, 9);
        when(productClient.getProduct(1L)).thenReturn(product(1L, "Phone", 500.0));
        when(cartRepo.findByUserId(42L)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> cartService.addItem(42L, 1L, 2));
        verify(cartRepo, never()).save(any());
    }

    @Test
    void updateQuantityToZeroRemovesItem() {
        Cart existing = cartWithItem(42L, 1L, 3);
        when(cartRepo.findByUserId(42L)).thenReturn(Optional.of(existing));
        when(cartRepo.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cart cart = cartService.updateItemQuantity(42L, 1L, 0);

        assertTrue(cart.getItems().isEmpty());
    }

    @Test
    void removingProductNotInCartThrows() {
        when(cartRepo.findByUserId(42L)).thenReturn(Optional.of(cartWithItem(42L, 1L, 1)));

        assertThrows(CartItemNotFoundException.class, () -> cartService.removeItem(42L, 99L));
    }

    @Test
    void getCartReturnsCachedCartWithoutHittingMongo() {
        Cart cached = cartWithItem(42L, 1L, 1);
        when(cartCacheClient.get(42L)).thenReturn(cached);

        assertSame(cached, cartService.getCart(42L));
        verifyNoInteractions(cartRepo);
    }

    @Test
    void cartTotalsAreComputedFromItems() {
        Cart cart = cartWithItem(42L, 1L, 3);
        CartItem second = new CartItem();
        second.setProductId(2L);
        second.setProductName("Book");
        second.setUnitPrice(19.99);
        second.setQuantity(2);
        cart.getItems().add(second);

        CartDto cartDto = CartDtoMapper.from(cart);

        assertEquals(5, cartDto.getTotalItems());
        assertEquals(1539.98, cartDto.getTotalAmount());
    }

    private ProductDto product(Long id, String name, Double price) {
        ProductDto productDto = new ProductDto();
        productDto.setId(id);
        productDto.setName(name);
        productDto.setPrice(price);
        return productDto;
    }

    private Cart cartWithItem(Long userId, Long productId, int quantity) {
        Cart cart = new Cart();
        cart.setUserId(userId);
        CartItem item = new CartItem();
        item.setProductId(productId);
        item.setProductName("Phone");
        item.setUnitPrice(500.0);
        item.setQuantity(quantity);
        cart.getItems().add(item);
        return cart;
    }
}
