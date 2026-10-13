package com.example.promotion.service;

import com.example.promotion.dto.PromotionUpdateMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionPublisher {

    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final ChannelTopic promotionTopic;
    private final ObjectMapper objectMapper;

    /**
     * Gửi thông điệp cập nhật khuyến mãi vào Redis channel 'promotion-updates'.
     *
     * @param message Thông điệp chứa product_id và thông tin khuyến mãi
     */
    public void publishPromotionUpdate(PromotionUpdateMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            stringRedisTemplate.convertAndSend(promotionTopic.getTopic(), jsonMessage);
            log.info("Published promotion update for product {} to channel {}: {}",
                    message.getProductId(), promotionTopic.getTopic(), jsonMessage);
        } catch (Exception e) {
            log.error("Failed to publish promotion update for product {}", message.getProductId(), e);
            throw new RuntimeException("Error publishing promotion update", e);
        }
    }
}
