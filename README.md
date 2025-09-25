# Saga 패턴을 적용한 Kafka 기반 마이크로서비스 예제

이 프로젝트는 Spring Boot, Apache Kafka, Docker를 사용한 마이크로서비스 아키텍처의 샘플 구현입니다. 이커머스 주문 처리 시나리오에서 분산된 서비스 간의 데이터 일관성을 유지하기 위해 Saga 패턴(Choreography 기반)을 사용합니다.

## 아키텍처 (Architecture)

시스템은 Kafka 이벤트를 통해 비동기적으로 통신하는 세 가지 핵심 마이크로서비스로 구성됩니다:

- **Order Service**: 주문 생성을 처리하고 주문의 전체 상태를 관리합니다.
- **Payment Service**: 주문에 대한 결제를 처리합니다.
- **Inventory Service**: 상품 재고를 관리하고 주문에 대한 재고를 예약합니다.

### 이벤트 기반 흐름 (Saga Pattern)

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

## 기술 스택 (Tech Stack)

- **Backend**: Java 21, Spring Boot 3
- **Messaging**: Apache Kafka
- **Database**: PostgreSQL
- **Build Tool**: Gradle
- **Containerization**: Docker, Docker Compose
- **Testing**: JUnit 5, Testcontainers

## 사전 요구사항 (Prerequisites)

- Java 21 이상
- Docker 및 Docker Compose

## 시작하기 (Getting Started)

로컬 머신에서 프로젝트를 설정하고 실행하려면 다음 단계를 따르세요.

### 1. 저장소 복제 (Clone the Repository)

```bash
git clone <your-repository-url>
cd kafka-example
```

### 2. 환경 변수 설정 (Set Up Environment Variables)

예제 환경 변수 파일을 복사하여 로컬 설정을 생성합니다.

```bash
cp .env.example .env
```

`.env` 파일의 기본값은 로컬 Docker Compose 환경에 적합합니다.

### 3. 애플리케이션 빌드 및 실행 (Build and Run the Application)

Docker Compose를 사용하여 모든 서비스의 이미지를 빌드하고 전체 스택(Kafka, PostgreSQL 포함)을 실행합니다.

```bash
docker-compose up --build
```

이 명령어는 다음 작업을 수행합니다:

1.  `order`, `payment`, `inventory` 서비스의 Docker 이미지를 빌드합니다.
2.  Kafka 클러스터와 PostgreSQL 데이터베이스를 포함한 모든 서비스의 컨테이너를 시작합니다.
3.  Health check를 실행하여 서비스들이 올바른 순서로 시작되도록 보장합니다.

`Ctrl+C`를 눌러 모든 서비스를 중지한 후, 아래 명령어로 컨테이너를 정리할 수 있습니다:

```bash
docker-compose down
```

## 사용 방법 (How to Use)

모든 서비스가 실행되면, `Order Service` API를 호출하여 시스템과 상호작용할 수 있습니다.

### 주문 생성하기 (Create a New Order)

Order Service로 `POST` 요청을 보냅니다.

```bash
curl -X POST http://localhost:8080/orders \
-H "Content-Type: application/json" \
-d '{
  "sku": "PRODUCT-001",
  "quantity": 5
}'
```

- **결제 실패**를 시뮬레이션하려면, `quantity`를 10보다 큰 값으로 설정하세요.
- **재고 부족 실패**를 시뮬레이션하려면, 존재하지 않는 `sku`를 사용하세요.

## 테스트 실행 (Running Tests)

이 프로젝트는 Testcontainers를 사용한 통합 테스트를 포함하고 있습니다. 각 테스트 실행 시 필요한 Kafka 및 PostgreSQL 컨테이너가 자동으로 실행됩니다.

모든 모듈의 테스트를 실행하려면, 프로젝트 루트에서 아래의 Gradle 명령어를 실행하세요:

```bash
./gradlew test
```
