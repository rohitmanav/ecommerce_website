package org.example.cartservice.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.cartservice.models.Cart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

// MongoDB stays the source of truth; when Redis is unreachable the cache is skipped for a while.
@Slf4j
@Component
public class CartCacheClient {

    private static final String KEY_PREFIX = "CART::";
    private static final Duration TIME_TO_LIVE = Duration.ofMinutes(30);
    private static final long RETRY_AFTER_MILLIS = 30_000;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private volatile long unavailableUntil = 0;

    public Cart get(Long userId) {
        if (isSkipped()) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, Cart.class);
        } catch (Exception exception) {
            markUnavailable(exception);
            return null;
        }
    }

    public void put(Cart cart) {
        if (isSkipped()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + cart.getUserId(),
                    objectMapper.writeValueAsString(cart), TIME_TO_LIVE);
        } catch (Exception exception) {
            markUnavailable(exception);
        }
    }

    public void evict(Long userId) {
        if (isSkipped()) {
            return;
        }
        try {
            stringRedisTemplate.delete(KEY_PREFIX + userId);
        } catch (Exception exception) {
            markUnavailable(exception);
        }
    }

    private boolean isSkipped() {
        return System.currentTimeMillis() < unavailableUntil;
    }

    private void markUnavailable(Exception exception) {
        unavailableUntil = System.currentTimeMillis() + RETRY_AFTER_MILLIS;
        log.warn("Redis unavailable, cart cache disabled for {}s: {}", RETRY_AFTER_MILLIS / 1000, exception.getMessage());
    }
}
