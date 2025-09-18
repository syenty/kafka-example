package com.example.msa.payment.service;

import com.example.msa.common.dto.OrderCreatedEvent;
import com.example.msa.common.dto.PaymentCompletedEvent;
import com.example.msa.common.dto.PaymentFailedEvent;
import com.example.msa.payment.domain.Payment;
import com.example.msa.payment.domain.PaymentStatus;
import com.example.msa.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String ORDER_CREATED_TOPIC = "order-created";
    private static final String PAYMENT_COMPLETED_TOPIC = "payment-completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment-failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentRepository paymentRepository;

    @Transactional
    @KafkaListener(topics = ORDER_CREATED_TOPIC, groupId = "payment-group", containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Received order created event for orderId: {}", event.getOrderId());

        // 결제 처리 시뮬레이션
        boolean paymentSuccessful = processPayment(event);

        if (paymentSuccessful) {
            Payment payment = Payment.builder()
                .orderId(event.getOrderId())
                .status(PaymentStatus.COMPLETED)
                .build();
            paymentRepository.save(payment);
            log.info("결제 정보 저장 완료. paymentId: {}, orderId: {}", payment.getId(), payment.getOrderId());

            PaymentCompletedEvent completedEvent = new PaymentCompletedEvent(event.getOrderId());
            kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC, completedEvent);
            log.info("결제 성공. 'payment-completed' 이벤트 발행. orderId: {}", event.getOrderId());
        } else {
            String reason = "잔액 부족 또는 주문 수량 과다";
            Payment payment = Payment.builder()
                .orderId(event.getOrderId())
                .status(PaymentStatus.FAILED)
                .reason(reason)
                .build();
            paymentRepository.save(payment);
            log.info("결제 정보 저장 완료. paymentId: {}, orderId: {}", payment.getId(), payment.getOrderId());

            PaymentFailedEvent failedEvent = new PaymentFailedEvent(event.getOrderId(), reason);
            kafkaTemplate.send(PAYMENT_FAILED_TOPIC, failedEvent);
            log.info("결제 실패. 'payment-failed' 이벤트 발행. orderId: {}", event.getOrderId());
        }
    }

    private boolean processPayment(OrderCreatedEvent event) {
        log.info("결제 처리 중. orderId: {}, quantity: {}", event.getOrderId(), event.getQuantity());
        // 실제 애플리케이션에서는 결제 게이트웨이 연동, 잔고 확인 등의 로직이 포함됩니다.
        // 예제에서는 주문 수량이 10개를 초과하면 실패하는 것으로 시뮬레이션합니다.
        return event.getQuantity() <= 10;
    }

}
