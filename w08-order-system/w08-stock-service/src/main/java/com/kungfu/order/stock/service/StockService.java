package com.kungfu.order.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kungfu.order.stock.entity.Stock;
import com.kungfu.order.stock.mapper.StockMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockMapper stockMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String STOCK_CACHE_KEY = "stock:";
    private static final String STOCK_LOCK_KEY = "stock:lock:";
    private static final long LOCK_TIMEOUT = 5;

    @Transactional
    public boolean deductStock(Long productId, Integer quantity, String orderNo) {
        String lockKey = STOCK_LOCK_KEY + productId;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, orderNo, LOCK_TIMEOUT, TimeUnit.SECONDS);

        if (locked == null || !locked) {
            log.warn("Failed to acquire stock lock for productId={}, orderNo={}", productId, orderNo);
            return false;
        }

        try {
            int rows = stockMapper.deductStock(productId, quantity);
            if (rows > 0) {
                log.info("Stock deducted: productId={}, quantity={}, orderNo={}", productId, quantity, orderNo);
                // Update Redis cache
                Stock stock = getStockFromDb(productId);
                if (stock != null) {
                    cacheStock(stock);
                }
                return true;
            } else {
                log.warn("Insufficient stock: productId={}, requested={}, orderNo={}", productId, quantity, orderNo);
                return false;
            }
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    public Stock getStock(Long productId) {
        String cacheKey = STOCK_CACHE_KEY + productId;
        String json = redisTemplate.opsForValue().get(cacheKey);

        if (json != null) {
            try {
                return objectMapper.readValue(json, Stock.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize stock cache for productId={}", productId, e);
            }
        }

        // Cache miss, read from DB
        Stock stock = getStockFromDb(productId);
        if (stock != null) {
            cacheStock(stock);
        }
        return stock;
    }

    private Stock getStockFromDb(Long productId) {
        LambdaQueryWrapper<Stock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Stock::getProductId, productId);
        return stockMapper.selectOne(wrapper);
    }

    private void cacheStock(Stock stock) {
        try {
            String json = objectMapper.writeValueAsString(stock);
            redisTemplate.opsForValue().set(STOCK_CACHE_KEY + stock.getProductId(), json, 30, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize stock for cache, productId={}", stock.getProductId(), e);
        }
    }
}
