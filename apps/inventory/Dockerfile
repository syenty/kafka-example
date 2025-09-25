# --- Builder Stage ---
# Gradle과 JDK를 포함한 이미지를 빌드용으로 사용합니다.
FROM gradle:8.10.2-jdk21 AS builder

WORKDIR /app

# 프로젝트 전체를 복사합니다.
COPY . .

# 'inventory' 애플리케이션을 빌드합니다. 테스트는 생략합니다.
# --no-daemon 옵션은 CI 환경에서 권장됩니다.
RUN chmod +x ./gradlew
RUN ./gradlew :apps:inventory:bootJar --no-daemon

# --- Runtime Stage ---
# 실제 애플리케이션 실행을 위한 가벼운 JRE 이미지를 사용합니다.
FROM eclipse-temurin:21-jre-jammy

# healthcheck에 필요한 curl을 설치합니다.
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Builder 스테이지에서 빌드된 JAR 파일을 복사합니다.
COPY --from=builder /app/apps/inventory/build/libs/*.jar app.jar

EXPOSE 8082

# 애플리케이션을 실행합니다.
ENTRYPOINT ["java", "-jar", "app.jar"]
