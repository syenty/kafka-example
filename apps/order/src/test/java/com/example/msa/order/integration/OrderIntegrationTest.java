package com.example.msa.order.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import com.example.msa.order.domain.Order;
import com.example.msa.order.dto.CreateOrderRequest;
import com.example.msa.order.repository.OrderRepository;
import com.example.msa.order.service.OrderService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers// 동적 속성 주입
public class OrderIntegrationTest {
    
    // 테스트 실행 시 PostgreSQL 17 버전 컨테이너를 실행합니다.
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    // 테스트 실행 시 Kafka 컨테이너를 실행합니다.
    // 2. org.testcontainers.kafka.KafkaContainer 사용
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DataSource dataSource;

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
    void createOrder_ShouldSaveOrderAndPublishEvent() {
        // given: 주문 생성 요청 데이터
        CreateOrderRequest request = new CreateOrderRequest("inv-123", 10);

        // when: 주문 생성 서비스 호출
        Long orderId = orderService.createOrder(request);

        // then: 주문이 데이터베이스에 저장되었는지 확인
        Optional<Order> savedOrderOpt = orderRepository.findById(orderId);
        assertTrue(savedOrderOpt.isPresent(), "주문이 데이터베이스에 저장되어야 합니다.");
        Order savedOrder = savedOrderOpt.get();
        assertEquals(request.getInventoryId(), savedOrder.getInventoryId());
        assertEquals(request.getQuantity(), savedOrder.getQuantity());

        // then: "주문 생성됨" 이벤트가 Kafka에 발행되었는지 확인
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*"); // 역직렬화 시 모든 패키지를 신뢰하도록 설정
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.example.msa.common.dto.OrderCreatedEvent");

        try (KafkaConsumer<String, OrderCreatedEvent> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList("order-created"));
            ConsumerRecords<String, OrderCreatedEvent> records = consumer.poll(Duration.ofSeconds(10));

            assertEquals(1, records.count(), "하나의 이벤트가 Kafka 토픽에 발행되어야 합니다.");
            ConsumerRecord<String, OrderCreatedEvent> record = records.iterator().next();
            OrderCreatedEvent event = record.value();

            assertEquals(orderId, event.getOrderId());
            assertEquals(request.getInventoryId(), event.getInventoryId());
            assertEquals(request.getQuantity(), event.getQuantity());
        }
    }
}
