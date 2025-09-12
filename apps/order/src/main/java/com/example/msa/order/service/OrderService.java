package com.example.msa.order.service;

import com.example.msa.common.dto.OrderCreatedEvent;
import com.example.msa.order.domain.Order;
import com.example.msa.order.dto.CreateOrderRequest;
import com.example.msa.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String ORDER_CREATED_TOPIC = "order-created";
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Transactional
    public Long createOrder(CreateOrderRequest request) {
        // 1. 주문 정보 저장
        Order order = Order.builder()
                .inventoryId(request.getInventoryId())
                .quantity(request.getQuantity())
                .build();
        orderRepository.save(order);

        // 2. "주문 생성됨" 이벤트 발행
        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(),
                order.getInventoryId(),
                order.getQuantity()
        );
        kafkaTemplate.send(ORDER_CREATED_TOPIC, event);

        return order.getId();
    }
}