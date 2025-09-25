package com.example.msa.inventory.service;

import java.util.Optional;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.msa.common.dto.InventoryFailedEvent;
import com.example.msa.common.dto.InventoryReservedEvent;
import com.example.msa.common.dto.OrderCreatedEvent;
import com.example.msa.inventory.domain.Inventory;
import com.example.msa.inventory.repository.InventoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {
    private static final String ORDER_CREATED_TOPIC = "order-created";
    private static final String INVENTORY_RESERVED_TOPIC = "inventory-reserved";
    private static final String INVENTORY_FAILED_TOPIC = "inventory-failed";

    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    @KafkaListener(topics = ORDER_CREATED_TOPIC, groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Received order created event for orderId: {}", event.getOrderId());

        Optional<Inventory> inventoryOpt = inventoryRepository.findBySku(event.getSku());

        inventoryOpt.filter(inv -> inv.getQuantity() >= event.getQuantity()).ifPresentOrElse(inventory -> {
            inventory.decreaseQuantity(event.getQuantity());
            inventoryRepository.save(inventory);
            kafkaTemplate.send(INVENTORY_RESERVED_TOPIC, new InventoryReservedEvent(event.getOrderId()));
            log.info("재고 예약 완료. 'inventory-reserved' 이벤트 발행. orderId: {}", event.getOrderId());
        }, () -> {
            String reason = inventoryOpt.map(inv -> "재고 부족").orElse("상품 없음");
            kafkaTemplate.send(INVENTORY_FAILED_TOPIC, new InventoryFailedEvent(event.getOrderId(), reason));
            log.warn("재고 예약 실패. reason: {}, orderId: {}", reason, event.getOrderId());
        });
    }
}
