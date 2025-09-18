package com.example.msa.payment.integration;

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
import com.example.msa.common.dto.PaymentCompletedEvent;
import com.example.msa.common.dto.PaymentFailedEvent;
import com.example.msa.payment.domain.Payment;
import com.example.msa.payment.domain.PaymentStatus;
import com.example.msa.payment.repository.PaymentRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers// 동적 속성 주입
public class PaymentIntegrationTest {
    
    // 테스트 실행 시 PostgreSQL 17 버전 컨테이너를 실행합니다.
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    // 테스트 실행 시 Kafka 컨테이너를 실행합니다.
    // 2. org.testcontainers.kafka.KafkaContainer 사용
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));

    @Autowired
    private PaymentRepository paymentRepository;

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
    void handleOrderCreated_ShouldProcessPaymentSuccessfully() {
        // given: 결제 성공 조건에 맞는 주문 생성 이벤트 (수량 <= 10)
        long orderId = System.currentTimeMillis();
        OrderCreatedEvent event = new OrderCreatedEvent(orderId, "inv-123", 5);

        // when: 'order-created' 토픽으로 이벤트를 발행
        kafkaTemplate.send("order-created", event);

        // then: 잠시 후, 결제가 완료되고 데이터베이스에 저장되며, 'payment-completed' 이벤트가 발행되어야 함
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // 1. DB에 결제 정보가 'COMPLETED' 상태로 저장되었는지 확인
            Optional<Payment> paymentOpt = paymentRepository.findByOrderId(orderId);
            assertTrue(paymentOpt.isPresent(), "결제 정보가 DB에 저장되어야 합니다.");
            assertEquals(PaymentStatus.COMPLETED, paymentOpt.get().getStatus());
            assertEquals(orderId, paymentOpt.get().getOrderId());

            // 2. 'payment-completed' 토픽에 이벤트가 발행되었는지 확인
            try (KafkaConsumer<String, PaymentCompletedEvent> consumer = createConsumer("payment-completed-group", PaymentCompletedEvent.class)) {
                consumer.subscribe(Collections.singletonList("payment-completed"));
                ConsumerRecords<String, PaymentCompletedEvent> records = consumer.poll(Duration.ofSeconds(5));

                boolean eventFound = false;
                for (ConsumerRecord<String, PaymentCompletedEvent> record : records) {
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
    void handleOrderCreated_ShouldHandlePaymentFailure() {
        // given: 결제 실패 조건에 맞는 주문 생성 이벤트 (수량 > 10)
        long orderId = System.currentTimeMillis();
        OrderCreatedEvent event = new OrderCreatedEvent(orderId, "inv-456", 15);

        // when: 'order-created' 토픽으로 이벤트를 발행
        kafkaTemplate.send("order-created", event);

        // then: 잠시 후, 결제가 실패하고 데이터베이스에 저장되며, 'payment-failed' 이벤트가 발행되어야 함
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // 1. DB에 결제 정보가 'FAILED' 상태로 저장되었는지 확인
            Optional<Payment> paymentOpt = paymentRepository.findByOrderId(orderId);
            assertTrue(paymentOpt.isPresent(), "결제 정보가 DB에 저장되어야 합니다.");
            assertEquals(PaymentStatus.FAILED, paymentOpt.get().getStatus());
            assertEquals(orderId, paymentOpt.get().getOrderId());

            // 2. 'payment-failed' 토픽에 이벤트가 발행되었는지 확인
            try (KafkaConsumer<String, PaymentFailedEvent> consumer = createConsumer("payment-failed-group", PaymentFailedEvent.class)) {
                consumer.subscribe(Collections.singletonList("payment-failed"));
                ConsumerRecords<String, PaymentFailedEvent> records = consumer.poll(Duration.ofSeconds(5));

                boolean eventFound = false;
                for (ConsumerRecord<String, PaymentFailedEvent> record : records) {
                    if (record.value().getOrderId().equals(orderId)) {
                        assertEquals("잔액 부족 또는 주문 수량 과다", record.value().getReason());
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
