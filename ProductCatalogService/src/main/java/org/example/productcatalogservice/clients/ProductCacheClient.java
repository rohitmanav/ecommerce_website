package org.example.productcatalogservice.clients;

import lombok.extern.slf4j.Slf4j;
import org.example.productcatalogservice.dtos.ProductDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

// When Redis is unreachable the cache is skipped for a while so requests fall back to MySQL without waiting on Redis.
// Entries expire after an hour, which bounds staleness if an eviction was missed during a Redis outage.
@Slf4j
@Component
public class ProductCacheClient {

    private static final String KEY_PREFIX = "PRODUCT::";
    private static final Duration TIME_TO_LIVE = Duration.ofHours(1);
    private static final long RETRY_AFTER_MILLIS = 30_000;

    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

    private volatile long unavailableUntil = 0;

    public ProductDto get(Long productId) {
        if (isSkipped()) {
            return null;
        }
        try {
            Object cached = redisTemplate.opsForValue().get(KEY_PREFIX + productId);
            if (cached instanceof ProductDto) {
                return (ProductDto) cached;
            }
            return null;
        } catch (Exception exception) {
            markUnavailable(exception);
            return null;
        }
    }

    public void put(ProductDto productDto) {
        if (isSkipped() || productDto == null || productDto.getId() == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + productDto.getId(), productDto, TIME_TO_LIVE);
        } catch (Exception exception) {
            markUnavailable(exception);
        }
    }

    public void evict(Long productId) {
        if (isSkipped()) {
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + productId);
        } catch (Exception exception) {
            markUnavailable(exception);
        }
    }

    private boolean isSkipped() {
        return System.currentTimeMillis() < unavailableUntil;
    }

    private void markUnavailable(Exception exception) {
        unavailableUntil = System.currentTimeMillis() + RETRY_AFTER_MILLIS;
        log.warn("Redis unavailable, product cache disabled for {}s: {}", RETRY_AFTER_MILLIS / 1000, exception.getMessage());
    }
}
