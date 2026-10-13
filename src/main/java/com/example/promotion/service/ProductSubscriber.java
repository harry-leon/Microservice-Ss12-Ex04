package com.example.promotion.service;

import com.example.promotion.dto.PromotionUpdateMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSubscriber {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PRODUCT_CACHE_PREFIX = "product:price:";

    // Giả lập cơ sở dữ liệu lưu giá của các sản phẩm
    private final Map<Long, BigDecimal> productDatabase = new ConcurrentHashMap<>(Map.of(
            1L, new BigDecimal("100000"),
            2L, new BigDecimal("250000"),
            3L, new BigDecimal("500000")
    ));

    /**
     * Lắng nghe thông điệp từ channel 'promotion-updates' và xóa cache Redis của sản phẩm tương ứng.
     * Phương thức này được gọi bởi MessageListenerAdapter khi có tin nhắn mới.
     *
     * @param messageBody Chuỗi JSON nhận được từ Redis channel
     */
    public void handleMessage(String messageBody) {
        log.info("Received message from Redis channel: {}", messageBody);
        try {
            PromotionUpdateMessage updateMessage;
            if (messageBody.trim().startsWith("{")) {
                updateMessage = objectMapper.readValue(messageBody, PromotionUpdateMessage.class);
            } else {
                // Hỗ trợ trường hợp tin nhắn gửi chỉ là productId dạng text
                Long prodId = Long.parseLong(messageBody.trim());
                updateMessage = PromotionUpdateMessage.builder().productId(prodId).build();
            }

            Long productId = updateMessage.getProductId();
            if (productId != null) {
                // Cập nhật giá mới vào database (nếu có thông tin giá mới)
                if (updateMessage.getPromotionalPrice() != null) {
                    productDatabase.put(productId, updateMessage.getPromotionalPrice());
                    log.info("Updated product {} price in DB to {}", productId, updateMessage.getPromotionalPrice());
                }

                // XÓA CACHE REDIS để lần truy vấn tiếp theo buộc phải lấy giá mới từ DB
                evictProductCache(productId);
            }
        } catch (Exception e) {
            log.error("Error processing message from Redis channel: {}", messageBody, e);
        }
    }

    /**
     * Xóa cache Redis của sản phẩm.
     */
    public void evictProductCache(Long productId) {
        String cacheKey = PRODUCT_CACHE_PREFIX + productId;
        Boolean deleted = stringRedisTemplate.delete(cacheKey);
        log.info("Evicted cache for product {} (Key: {}). Cache deleted: {}", productId, cacheKey, deleted);
    }

    /**
     * Lấy giá sản phẩm với mô hình Cache-Aside:
     * 1. Kiểm tra cache Redis trước.
     * 2. Nếu miss cache -> Lấy từ Database, ghi vào cache và trả về.
     */
    public BigDecimal getProductPrice(Long productId) {
        String cacheKey = PRODUCT_CACHE_PREFIX + productId;
        String cachedPrice = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cachedPrice != null) {
            log.debug("Cache HIT for product {}: {}", productId, cachedPrice);
            return new BigDecimal(cachedPrice);
        }

        log.debug("Cache MISS for product {}. Fetching from DB...", productId);
        BigDecimal dbPrice = productDatabase.getOrDefault(productId, BigDecimal.ZERO);

        // Ghi lại vào Cache Redis
        stringRedisTemplate.opsForValue().set(cacheKey, dbPrice.toPlainString());
        return dbPrice;
    }

    /**
     * Kiểm tra xem cache của sản phẩm hiện có tồn tại trong Redis không.
     */
    public boolean isProductCached(Long productId) {
        String cacheKey = PRODUCT_CACHE_PREFIX + productId;
        Boolean hasKey = stringRedisTemplate.hasKey(cacheKey);
        return Boolean.TRUE.equals(hasKey);
    }

    /**
     * Cập nhật trực tiếp vào DB (giả lập quản trị viên sửa DB).
     */
    public void updateDbPrice(Long productId, BigDecimal newPrice) {
        productDatabase.put(productId, newPrice);
    }
}
