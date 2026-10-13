package com.example.promotion.service;

import com.example.promotion.dto.PromotionUpdateMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.listener.ChannelTopic;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealtimePromotionPubSubTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ChannelTopic promotionTopic;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PromotionPublisher promotionPublisher;

    private ProductSubscriber productSubscriber;

    @BeforeEach
    void setUp() {
        productSubscriber = new ProductSubscriber(stringRedisTemplate, objectMapper);
    }

    @Test
    @DisplayName("Publisher gửi đúng tin nhắn vào channel promotion-updates")
    void testPublishPromotionUpdate() throws Exception {
        when(promotionTopic.getTopic()).thenReturn("promotion-updates");

        PromotionUpdateMessage message = PromotionUpdateMessage.builder()
                .productId(1L)
                .promotionalPrice(new BigDecimal("79000"))
                .promotionName("Super Sale")
                .timestamp(System.currentTimeMillis())
                .build();

        promotionPublisher.publishPromotionUpdate(message);

        verify(stringRedisTemplate).convertAndSend(eq("promotion-updates"), anyString());
    }

    @Test
    @DisplayName("Subscriber nhận tin nhắn và xóa cache của product_id")
    void testSubscriberEvictsCacheOnMessage() throws Exception {
        PromotionUpdateMessage message = PromotionUpdateMessage.builder()
                .productId(1L)
                .promotionalPrice(new BigDecimal("79000"))
                .promotionName("Super Sale")
                .timestamp(System.currentTimeMillis())
                .build();
        String jsonMessage = objectMapper.writeValueAsString(message);

        when(stringRedisTemplate.delete("product:price:1")).thenReturn(true);

        productSubscriber.handleMessage(jsonMessage);

        // Kiểm tra cache Redis của sản phẩm 1 đã bị xóa
        verify(stringRedisTemplate).delete("product:price:1");
    }

    @Test
    @DisplayName("Subscriber hỗ trợ nhận tin nhắn dạng text thô chỉ chứa product_id")
    void testSubscriberHandlesRawProductIdMessage() {
        when(stringRedisTemplate.delete("product:price:2")).thenReturn(true);

        productSubscriber.handleMessage("2");

        verify(stringRedisTemplate).delete("product:price:2");
    }

    @Test
    @DisplayName("Sau khi xóa cache, lần truy vấn tiếp theo lấy giá mới từ DB")
    void testCacheEvictionForcesFetchFromDb() throws Exception {
        Long productId = 1L;

        // Giả lập lần 1: Miss cache -> lấy từ DB (100000) và lưu vào Cache
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("product:price:1")).thenReturn(null);

        BigDecimal price1 = productSubscriber.getProductPrice(productId);
        assertEquals(new BigDecimal("100000"), price1);
        verify(valueOperations).set("product:price:1", "100000");

        // Giả lập có sự kiện cập nhật giá mới thành 69000 qua Pub/Sub
        PromotionUpdateMessage updateMessage = PromotionUpdateMessage.builder()
                .productId(productId)
                .promotionalPrice(new BigDecimal("69000"))
                .promotionName("Flash Sale 12.12")
                .timestamp(System.currentTimeMillis())
                .build();

        when(stringRedisTemplate.delete("product:price:1")).thenReturn(true);
        productSubscriber.handleMessage(objectMapper.writeValueAsString(updateMessage));

        // Kiểm tra xóa cache thành công
        verify(stringRedisTemplate).delete("product:price:1");

        // Lần 2: Query lại sản phẩm -> vì cache đã bị xóa (null), hệ thống lấy giá mới từ DB (69000)
        when(valueOperations.get("product:price:1")).thenReturn(null);
        BigDecimal newPrice = productSubscriber.getProductPrice(productId);
        assertEquals(new BigDecimal("69000"), newPrice, "Giá trả về phải là giá khuyến mãi mới sau khi xóa cache");
        verify(valueOperations).set("product:price:1", "69000");
    }
}
