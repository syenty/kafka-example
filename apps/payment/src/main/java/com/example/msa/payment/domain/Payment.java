package com.example.msa.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String reason;

    @Builder
    public Payment(Long orderId, PaymentStatus status, String reason) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
    }

    public void cancel(String reason) {
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }
}