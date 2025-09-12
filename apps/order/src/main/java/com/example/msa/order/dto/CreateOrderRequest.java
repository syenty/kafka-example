package com.example.msa.order.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CreateOrderRequest {
    private String inventoryId;
    private Integer quantity;
}