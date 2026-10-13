package com.example.promotion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionUpdateMessage implements Serializable {
    private Long productId;
    private BigDecimal promotionalPrice;
    private String promotionName;
    private Long timestamp;
}
