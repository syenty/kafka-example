# Saga 패턴을 적용한 Kafka 기반 마이크로서비스 예제

이 프로젝트는 Spring Boot, Apache Kafka, Docker를 사용한 마이크로서비스 아키텍처의 샘플 구현입니다. 이커머스 주문 처리 시나리오에서 분산된 서비스 간의 데이터 일관성을 유지하기 위해 Saga 패턴(Choreography 기반)을 사용합니다.

## Architecture

시스템은 Kafka 이벤트를 통해 비동기적으로 통신하는 세 가지 핵심 마이크로서비스로 구성됩니다:

- **Order Service**: 주문 생성을 처리하고 주문의 전체 상태를 관리합니다.
- **Payment Service**: 주문에 대한 결제를 처리합니다.
- **Inventory Service**: 상품 재고를 관리하고 주문에 대한 재고를 예약합니다.

### Event-Driven Flow (Saga Pattern)

서비스들은 주문 트랜잭션을 처리하기 위해 Choreography 기반 Saga 패턴을 따릅니다:

1.  **주문 생성 (Order Creation)**: `POST /orders`에 대한 사용자 요청이 `Order Service`를 트리거합니다.
2.  `Order Service`는 `PENDING` 상태의 주문을 생성하고 `OrderCreatedEvent`를 발행합니다.
3.  **병렬 처리 (Parallel Processing)**:
    - `Payment Service`는 `OrderCreatedEvent`를 수신하여 결제를 처리하고, `PaymentCompletedEvent` 또는 `PaymentFailedEvent`를 발행합니다.
    - `Inventory Service`는 `OrderCreatedEvent`를 수신하여 재고를 예약하고, `InventoryReservedEvent` 또는 `InventoryFailedEvent`를 발행합니다.
4.  **주문 확정 (Order Confirmation)**: `Order Service`는 `PaymentCompletedEvent`와 `InventoryReservedEvent`를 모두 수신합니다. 두 이벤트가 모두 수신되면 주문 상태를 `CONFIRMED`로 업데이트합니다.
5.  **보상 트랜잭션 (Compensating Transactions / Rollback)**:
    - `Payment Service`가 `PaymentFailedEvent`를 발행하면, `Order Service`는 이를 수신하여 주문을 취소합니다.
    - `Inventory Service`가 `InventoryFailedEvent`를 발행하면, `Order Service`와 `Payment Service`는 이를 수신하여 각자의 보상 트랜잭션(주문 취소, 결제 환불)을 실행합니다.

_(간단한 아키텍처 다이어그램을 만들어 여기에 추가하는 것을 강력히 권장합니다)_

## Tech Stack

- **Backend**: Java 21, Spring Boot 3
- **Messaging**: Apache Kafka
- **Database**: PostgreSQL
- **Build Tool**: Gradle
- **Containerization**: Docker, Docker Compose
- **Testing**: JUnit 5, Testcontainers

## Prerequisites

- Java 21 이상
- Docker 및 Docker Compose

## Getting Started

시작하기

Follow these steps to get the project up and running on your local machine.

### 1. Clone the Repository

```bash
git clone <your-repository-url>
cd kafka-example
```

### 2. Set Up Environment Variables

예제 환경 변수 파일을 복사하여 로컬 설정을 생성합니다.

```bash
cp .env.example .env
```

The default values in the `.env` file are suitable for the local Docker Compose setup.

### 3. Build and Run the Application

Use Docker Compose to build the images for all services and run the entire stack (including Kafka and PostgreSQL).

```bash
docker-compose up --build
```

This command will:

1.  Build the Docker images for `order`, `payment`, and `inventory` services.
2.  Start containers for all services, including the Kafka cluster and PostgreSQL databases.
3.  Run health checks to ensure services start in the correct order.

You can stop all services by pressing `Ctrl+C` and then running:

```bash
docker-compose down
```

## How to Use

Once all services are running, you can interact with the system by calling the `Order Service` API.

### Create a New Order

Send a `POST` request to the Order Service.

```bash
curl -X POST http://localhost:8080/orders \
-H "Content-Type: application/json" \
-d '{
  "sku": "PRODUCT-001",
  "quantity": 5
}'
```

- To simulate a **payment failure**, set the `quantity` to a value greater than 10.
- To simulate an **inventory failure**, use a `sku` that does not exist in the inventory.

## Running Tests

The project includes integration tests using Testcontainers, which automatically spin up the necessary Kafka and PostgreSQL containers for each test run.

To run the tests for all modules, execute the following Gradle command from the project root:

```bash
./gradlew test
```
