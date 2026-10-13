package com.example.promotion.controller;

import com.example.promotion.dto.PromotionUpdateMessage;
import com.example.promotion.service.ProductSubscriber;
import com.example.promotion.service.PromotionPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionPublisher promotionPublisher;
    private final ProductSubscriber productSubscriber;

    /**
     * Endpoint dành cho Quản trị viên thay đổi chương trình khuyến mãi (Publisher).
     */
    @PostMapping("/promotions/update")
    public ResponseEntity<Map<String, Object>> updatePromotion(
            @RequestParam Long productId,
            @RequestParam BigDecimal promotionalPrice,
            @RequestParam(defaultValue = "Flash Sale Campaign") String promotionName) {

        PromotionUpdateMessage message = PromotionUpdateMessage.builder()
                .productId(productId)
                .promotionalPrice(promotionalPrice)
                .promotionName(promotionName)
                .timestamp(System.currentTimeMillis())
                .build();

        promotionPublisher.publishPromotionUpdate(message);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã phát thông điệp cập nhật khuyến mãi thành công!",
                "data", message
        ));
    }

    /**
     * Endpoint truy vấn giá sản phẩm (Subscriber / Client).
     */
    @GetMapping("/products/{productId}/price")
    public ResponseEntity<Map<String, Object>> getProductPrice(@PathVariable Long productId) {
        boolean cachedBefore = productSubscriber.isProductCached(productId);
        BigDecimal price = productSubscriber.getProductPrice(productId);

        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "price", price,
                "wasCached", cachedBefore
        ));
    }
}
