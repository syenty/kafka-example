# Kafka-based Microservices Example with Saga Pattern

This project is a sample implementation of a microservices architecture using Spring Boot, Apache Kafka, and Docker. It demonstrates the Saga pattern (Choreography-based) to maintain data consistency across distributed services in an e-commerce order processing scenario.

## Architecture

The system consists of three core microservices that communicate asynchronously via Kafka events:

- **Order Service**: Handles order creation and manages the overall status of an order.
- **Payment Service**: Processes payments for orders.
- **Inventory Service**: Manages product stock and reserves inventory for orders.

### Event-Driven Flow (Saga Pattern)

The services follow a choreography-based Saga pattern to handle an order transaction:

1.  **Order Creation**: A user request to `POST /orders` triggers the `Order Service`.
2.  `Order Service` creates an order in `PENDING` status and publishes an `OrderCreatedEvent`.
3.  **Parallel Processing**:
    - `Payment Service` consumes the `OrderCreatedEvent`, processes the payment, and publishes either a `PaymentCompletedEvent` or `PaymentFailedEvent`.
    - `Inventory Service` consumes the `OrderCreatedEvent`, reserves stock, and publishes either an `InventoryReservedEvent` or `InventoryFailedEvent`.
4.  **Order Confirmation**: `Order Service` consumes both `PaymentCompletedEvent` and `InventoryReservedEvent`. Once both are received, it updates the order status to `CONFIRMED`.
5.  **Compensating Transactions (Rollback)**:
    - If `Payment Service` publishes a `PaymentFailedEvent`, `Order Service` consumes it and cancels the order.
    - If `Inventory Service` publishes an `InventoryFailedEvent`, both `Order Service` and `Payment Service` consume it to trigger their respective compensating transactions (cancel order, refund payment).

_(It's highly recommended to create and add a simple architecture diagram here)_

## Tech Stack

- **Backend**: Java 21, Spring Boot 3
- **Messaging**: Apache Kafka
- **Database**: PostgreSQL
- **Build Tool**: Gradle
- **Containerization**: Docker, Docker Compose
- **Testing**: JUnit 5, Testcontainers

## Prerequisites

- Java 21 or higher
- Docker and Docker Compose

## Getting Started

Follow these steps to get the project up and running on your local machine.

### 1. Clone the Repository

```bash
git clone <your-repository-url>
cd kafka-example
```

### 2. Set Up Environment Variables

Copy the example environment file to create your own local configuration.

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
