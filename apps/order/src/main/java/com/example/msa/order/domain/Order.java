package com.example.msa.order.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private boolean paymentCompleted = false;
    private boolean inventoryReserved = false;

    @Builder
    public Order(String sku, Integer quantity) {
        this.sku = sku;
        this.quantity = quantity;
        this.status = OrderStatus.PENDING;
    }

    public void completePayment() {
        this.paymentCompleted = true;
        if (this.inventoryReserved) {
            this.status = OrderStatus.CONFIRMED;
        }
    }

    public void reserveInventory() {
        this.inventoryReserved = true;
        if (this.paymentCompleted) {
            this.status = OrderStatus.CONFIRMED;
        }
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public void cancelByPaymentFailure() {
        this.status = OrderStatus.CANCELLED;
        this.paymentCompleted = false; // 결제 상태 롤백
    }

    public void cancelByInventoryFailure() {
        this.status = OrderStatus.CANCELLED;
        this.inventoryReserved = false; // 재고 상태 롤백
    }
}