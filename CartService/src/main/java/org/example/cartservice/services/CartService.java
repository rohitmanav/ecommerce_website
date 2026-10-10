package org.example.cartservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cartservice.clients.CartCacheClient;
import org.example.cartservice.clients.KafkaProducerClient;
import org.example.cartservice.clients.ProductClient;
import org.example.cartservice.dtos.CartEventDto;
import org.example.cartservice.dtos.ProductDto;
import org.example.cartservice.exceptions.CartItemNotFoundException;
import org.example.cartservice.models.Cart;
import org.example.cartservice.models.CartItem;
import org.example.cartservice.repos.CartRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Service
public class CartService implements ICartService {

    public static final String CART_EVENTS_TOPIC = "cart_events";

    @Autowired
    private CartRepo cartRepo;

    @Autowired
    private CartCacheClient cartCacheClient;

    @Autowired
    private ProductClient productClient;

    @Autowired
    private KafkaProducerClient kafkaProducerClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.cart.max-quantity-per-item:10}")
    private Integer maxQuantityPerItem;

    @Override
    public Cart getCart(Long userId) {
        Cart cached = cartCacheClient.get(userId);
        if (cached != null) {
            return cached;
        }

        Cart cart = findOrCreateCart(userId);
        cartCacheClient.put(cart);
        return cart;
    }

    @Override
    public Cart addItem(Long userId, Long productId, Integer quantity) {
        ProductDto productDto = productClient.getProduct(productId);
        Cart cart = findOrCreateCart(userId);

        Optional<CartItem> optionalCartItem = cart.findItem(productId);
        CartItem item;
        if (optionalCartItem.isPresent()) {
            item = optionalCartItem.get();
            item.setQuantity(item.getQuantity() + quantity);
        } else {
            item = new CartItem();
            item.setProductId(productId);
            item.setQuantity(quantity);
            cart.getItems().add(item);
        }
        checkQuantity(item.getQuantity());

        // Refresh the copied product details so the cart shows the current price.
        item.setProductName(productDto.getName());
        item.setImageUrl(productDto.getImageUrl());
        item.setUnitPrice(productDto.getPrice());

        Cart saved = save(cart);
        publishEvent(userId, productId, item.getQuantity(), "ITEM_ADDED");
        return saved;
    }

    @Override
    public Cart updateItemQuantity(Long userId, Long productId, Integer quantity) {
        if (quantity == 0) {
            return removeItem(userId, productId);
        }
        checkQuantity(quantity);

        Cart cart = findOrCreateCart(userId);
        Optional<CartItem> optionalCartItem = cart.findItem(productId);
        if (optionalCartItem.isEmpty()) {
            throw new CartItemNotFoundException("Product " + productId + " is not in the cart");
        }
        CartItem item = optionalCartItem.get();
        item.setQuantity(quantity);

        Cart saved = save(cart);
        publishEvent(userId, productId, quantity, "ITEM_UPDATED");
        return saved;
    }

    @Override
    public Cart removeItem(Long userId, Long productId) {
        Cart cart = findOrCreateCart(userId);
        boolean removed = cart.getItems().removeIf(item -> item.getProductId().equals(productId));
        if (!removed) {
            throw new CartItemNotFoundException("Product " + productId + " is not in the cart");
        }

        Cart saved = save(cart);
        publishEvent(userId, productId, 0, "ITEM_REMOVED");
        return saved;
    }

    @Override
    public void clearCart(Long userId) {
        Optional<Cart> optionalCart = cartRepo.findByUserId(userId);
        if (optionalCart.isPresent()) {
            Cart cart = optionalCart.get();
            cart.getItems().clear();
            save(cart);
        } else {
            cartCacheClient.evict(userId);
        }
        publishEvent(userId, null, 0, "CART_CLEARED");
    }

    private Cart save(Cart cart) {
        cart.setLastUpdatedAt(new Date());
        Cart saved = cartRepo.save(cart);
        cartCacheClient.put(saved);
        return saved;
    }

    private void checkQuantity(Integer quantity) {
        if (quantity > maxQuantityPerItem) {
            throw new IllegalArgumentException("You can add at most " + maxQuantityPerItem + " units of a product");
        }
    }

    private Cart findOrCreateCart(Long userId) {
        Optional<Cart> optionalCart = cartRepo.findByUserId(userId);
        if (optionalCart.isPresent()) {
            return optionalCart.get();
        }

        Cart cart = new Cart();
        cart.setUserId(userId);
        return cart;
    }

    private void publishEvent(Long userId, Long productId, Integer quantity, String action) {
        CartEventDto cartEventDto = new CartEventDto();
        cartEventDto.setUserId(userId);
        cartEventDto.setProductId(productId);
        cartEventDto.setQuantity(quantity);
        cartEventDto.setAction(action);
        try {
            kafkaProducerClient.sendMessage(CART_EVENTS_TOPIC, objectMapper.writeValueAsString(cartEventDto));
        } catch (JsonProcessingException exception) {
            throw new RuntimeException(exception);
        }
    }
}
