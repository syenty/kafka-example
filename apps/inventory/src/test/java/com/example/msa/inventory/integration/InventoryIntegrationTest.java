package com.example.msa.inventory.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.msa.common.dto.OrderCreatedEvent;
import com.example.msa.common.dto.InventoryFailedEvent;
import com.example.msa.common.dto.InventoryReservedEvent;
import com.example.msa.inventory.domain.Inventory;
import com.example.msa.inventory.repository.InventoryRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers// 동적 속성 주입
public class InventoryIntegrationTest {
    
    // 테스트 실행 시 PostgreSQL 17 버전 컨테이너를 실행합니다.
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    // 테스트 실행 시 Kafka 컨테이너를 실행합니다.
    // 2. org.testcontainers.kafka.KafkaContainer 사용
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    // 3. @DynamicPropertySource를 사용하여 동적 속성을 더 깔끔하게 주입합니다.
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void databaseConnection_ShouldBeSuccessful() throws SQLException {
        // given & when: Testcontainers와 Spring context가 초기화됩니다.
        // then: 데이터베이스에 대한 연결을 설정할 수 있어야 합니다.
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(1), "데이터베이스 연결이 유효해야 합니다.");
        }
    }

    @Test
    void handleOrderCreated_ShouldReserveInventorySuccessfully() {
        // given: 충분한 재고가 있는 상품
        String sku = "SKU-SUCCESS-001";
        inventoryRepository.save(Inventory.builder().sku(sku).quantity(20).build());
        long orderId = System.currentTimeMillis();
        OrderCreatedEvent event = new OrderCreatedEvent(orderId, sku, 10);

        // when: 'order-created' 토픽으로 이벤트를 발행
        kafkaTemplate.send("order-created", event);

        // then: 잠시 후, 재고가 차감되고 'inventory-reserved' 이벤트가 발행되어야 함
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // 1. DB에서 재고가 정상적으로 차감되었는지 확인
            Optional<Inventory> inventoryOpt = inventoryRepository.findBySku(sku);
            assertTrue(inventoryOpt.isPresent(), "재고 정보가 DB에 존재해야 합니다.");
            assertEquals(10, inventoryOpt.get().getQuantity(), "재고가 10만큼 차감되어야 합니다.");

            // 2. 'inventory-reserved' 토픽에 이벤트가 발행되었는지 확인
            try (KafkaConsumer<String, InventoryReservedEvent> consumer = createConsumer("inventory-reserved-group", InventoryReservedEvent.class)) {
                consumer.subscribe(Collections.singletonList("inventory-reserved"));
                ConsumerRecords<String, InventoryReservedEvent> records = consumer.poll(Duration.ofSeconds(5));

                boolean eventFound = false;
                for (ConsumerRecord<String, InventoryReservedEvent> record : records) {
                    if (record.value().getOrderId().equals(orderId)) {
                        eventFound = true;
                        break;
                    }
                }
                assertTrue(eventFound, "발행된 이벤트의 orderId가 일치해야 합니다.");
            }
        });
    }

    @Test
    void handleOrderCreated_ShouldFailInventoryReservation_WhenStockIsInsufficient() {
        // given: 재고가 부족한 상품
        String sku = "SKU-FAIL-001";
        inventoryRepository.save(Inventory.builder().sku(sku).quantity(5).build());
        long orderId = System.currentTimeMillis();
        OrderCreatedEvent event = new OrderCreatedEvent(orderId, sku, 10);

        // when: 'order-created' 토픽으로 이벤트를 발행
        kafkaTemplate.send("order-created", event);

        // then: 잠시 후, 재고는 변동이 없고 'inventory-failed' 이벤트가 발행되어야 함
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // 1. DB에서 재고가 변동이 없는지 확인
            Optional<Inventory> inventoryOpt = inventoryRepository.findBySku(sku);
            assertTrue(inventoryOpt.isPresent(), "재고 정보가 DB에 존재해야 합니다.");
            assertEquals(5, inventoryOpt.get().getQuantity(), "재고가 변동되지 않아야 합니다.");

            // 2. 'inventory-failed' 토픽에 이벤트가 발행되었는지 확인
            try (KafkaConsumer<String, InventoryFailedEvent> consumer = createConsumer("inventory-failed-group", InventoryFailedEvent.class)) {
                consumer.subscribe(Collections.singletonList("inventory-failed"));
                ConsumerRecords<String, InventoryFailedEvent> records = consumer.poll(Duration.ofSeconds(5));

                boolean eventFound = false;
                for (ConsumerRecord<String, InventoryFailedEvent> record : records) {
                    if (record.value().getOrderId().equals(orderId)) {
                        assertEquals("재고 부족", record.value().getReason());
                        eventFound = true;
                        break;
                    }
                }
                assertTrue(eventFound, "발행된 이벤트의 orderId가 일치해야 합니다.");
            }
        });
    }

    private <T> KafkaConsumer<String, T> createConsumer(String groupId, Class<T> valueType) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-" + System.currentTimeMillis());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        JsonDeserializer<T> deserializer = new JsonDeserializer<>(valueType, false);
        deserializer.addTrustedPackages("*");
        return new KafkaConsumer<>(props, new StringDeserializer(), deserializer);
    }
}
