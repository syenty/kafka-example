package com.example.msa.order.service;

import com.example.msa.common.dto.InventoryFailedEvent;
import com.example.msa.common.dto.InventoryReservedEvent;
import com.example.msa.common.dto.OrderCreatedEvent;
import com.example.msa.common.dto.PaymentFailedEvent;
import com.example.msa.common.dto.PaymentCompletedEvent;
import com.example.msa.order.domain.Order;
import com.example.msa.order.dto.CreateOrderRequest;
import com.example.msa.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String ORDER_CREATED_TOPIC = "order-created";
    private static final String PAYMENT_COMPLETED_TOPIC = "payment-completed";
    private static final String INVENTORY_RESERVED_TOPIC = "inventory-reserved";
    private static final String PAYMENT_FAILED_TOPIC = "payment-failed";
    private static final String INVENTORY_FAILED_TOPIC = "inventory-failed";

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public Long createOrder(CreateOrderRequest request) {
        // 1. 주문 정보 저장
        Order order = Order.builder()
                .sku(request.getSku())
                .quantity(request.getQuantity())
                .build();
        orderRepository.save(order);

        // 2. "주문 생성됨" 이벤트 발행
        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(),
                order.getSku(),
                order.getQuantity()
        );
        kafkaTemplate.send(ORDER_CREATED_TOPIC, event);

        return order.getId();
    }

    @Transactional
    @KafkaListener(topics = PAYMENT_COMPLETED_TOPIC, containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received payment completed event for orderId: {}", event.getOrderId());
        orderRepository.findById(event.getOrderId()).ifPresent(order -> {
            order.completePayment();
            orderRepository.save(order);
            log.info("Order payment status updated for orderId: {}. Current status: {}", order.getId(), order.getStatus());
        });
    }

    @Transactional
    @KafkaListener(topics = INVENTORY_RESERVED_TOPIC, containerFactory = "kafkaListenerContainerFactory")
    public void handleInventoryReserved(InventoryReservedEvent event) {
        log.info("Received inventory reserved event for orderId: {}", event.getOrderId());
        orderRepository.findById(event.getOrderId()).ifPresent(order -> {
            order.reserveInventory();
            orderRepository.save(order);
            log.info("Order inventory status updated for orderId: {}. Current status: {}", order.getId(), order.getStatus());
        });
    }

    @Transactional
    @KafkaListener(topics = PAYMENT_FAILED_TOPIC, containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("Received payment failed event for orderId: {}. Reason: {}", event.getOrderId(), event.getReason());
        orderRepository.findById(event.getOrderId()).ifPresent(order -> {
            order.cancelByPaymentFailure();
            orderRepository.save(order);
            log.info("Order cancelled for orderId: {}. Current status: {}", order.getId(), order.getStatus());
        });
    }

    @Transactional
    @KafkaListener(topics = INVENTORY_FAILED_TOPIC, containerFactory = "kafkaListenerContainerFactory")
    public void handleInventoryFailed(InventoryFailedEvent event) {
        log.info("Received inventory failed event for orderId: {}. Reason: {}", event.getOrderId(), event.getReason());
        orderRepository.findById(event.getOrderId()).ifPresent(order -> {
            order.cancelByInventoryFailure();
            orderRepository.save(order);
            log.info("Order cancelled for orderId: {}. Current status: {}", order.getId(), order.getStatus());
        });
    }
}