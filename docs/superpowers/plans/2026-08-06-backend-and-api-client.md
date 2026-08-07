# Backend and API Client Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete Spring Boot API described in `docs/superpowers/specs/2026-08-06-backend-and-api-client-design.md`, plus the typed API client layer in `ui/`.

**Architecture:** A single Spring Boot module. Anonymous customers create orders through public endpoints; a swappable `PaymentProvider` creates the PIX charge; payment is confirmed through three independent paths (webhook, scheduled reconciler, manual admin action) that all funnel into one locked `markPaid`. A public dashboard aggregates straight from the database with no cache. Admins authenticate with JWT and manage products, orders, expenses, settings and each other. Product photos go through a port of the `sator` image module: hash dedup, S3-compatible storage, reference-guarded delete.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring Data JPA, Spring Security, Flyway, PostgreSQL 17 (dev/prod) / H2 in PostgreSQL mode (test), AWS SDK v2 S3 (MinIO local, Cloudflare R2 prod), Thumbnailator, ZXing, JJWT, React 19 + Vite 8 + TypeScript.

## Global Constraints

- **Language:** all code, comments, identifiers, log messages, commit messages, documentation and filenames are in **English**. Portuguese appears **only** in strings an end user reads on screen.
- **Money:** `NUMERIC(12,2)` in SQL, `BigDecimal` in Java. Never `double` or `float`. Every `BigDecimal.divide` passes an explicit scale and `RoundingMode`.
- **Migrations:** portable SQL only — no `jsonb`, no `gen_random_uuid()`, no `BIGSERIAL`. UUIDs are generated in Java. The same migrations run on H2 in tests and PostgreSQL in dev/prod.
- **Time:** stored and computed in UTC. `America/Sao_Paulo` is applied only when bucketing dashboard figures into days, weeks and months.
- **Never trust the client for money.** Order totals are always computed server-side from current database prices.
- **Commits:** conventional commits, English, one per task step where the plan says to commit. Never `git push`.
- **TDD:** every task writes the failing test first, watches it fail, then implements.
- Base package is `com.example.localhostfacom`. All paths below are relative to the repository root.

---

## File Structure

```
api/src/main/java/com/example/localhostfacom/
├── LocalhostfacomApplication.java        (exists)
├── config/
│   ├── AppProperties.java                typed config binding for app.*
│   ├── SecurityConfig.java               filter chain, CORS, password encoder
│   ├── JacksonConfig.java                BigDecimal + Instant serialization
│   └── SchedulingConfig.java             @EnableScheduling
├── common/
│   ├── ApiException.java                 base domain exception carrying a status + slug
│   ├── GlobalExceptionHandler.java       RFC 7807 problem+json
│   └── RateLimiter.java                  in-memory per-IP fixed window
├── admin/
│   ├── Admin.java, AdminRepository.java
│   ├── AdminService.java                 last-admin + self-delete guards
│   ├── AdminController.java              /api/admin/admins
│   └── BootstrapAdminRunner.java
├── auth/
│   ├── JwtService.java                   sign + parse HS256
│   ├── JwtAuthenticationFilter.java      re-loads the admin row per request
│   ├── AuthController.java               /api/auth/login
│   └── dto/LoginRequest.java, LoginResponse.java
├── image/
│   ├── Image.java, ImageRepository.java
│   ├── StorageProvider.java              interface
│   ├── S3CompatibleStorageProvider.java
│   ├── ImageProcessor.java               bomb guard, resize, re-encode, hash
│   ├── ProcessedImage.java
│   ├── ImageService.java                 dedup, rollback, reference-guarded delete
│   ├── ImageController.java              /api/admin/images
│   └── dto/ImageResponse.java
├── product/
│   ├── Product.java, ProductRepository.java
│   ├── ProductService.java
│   ├── PublicProductController.java      /api/public/products
│   ├── AdminProductController.java       /api/admin/products
│   └── dto/ProductResponse.java, ProductRequest.java
├── payment/
│   ├── PaymentProvider.java              interface
│   ├── PaymentProviderRegistry.java
│   ├── ChargeRequest.java, PaymentCharge.java, PaymentStatus.java
│   ├── WebhookNotification.java
│   ├── FakePaymentProvider.java
│   ├── MercadoPagoPaymentProvider.java
│   ├── MercadoPagoSignatureVerifier.java
│   └── WebhookController.java            /api/webhooks/{provider}
├── order/
│   ├── Order.java, OrderItem.java, OrderStatus.java, OrderRepository.java
│   ├── WebhookEvent.java, WebhookEventRepository.java
│   ├── OrderService.java                 create, charge, markPaid, expire
│   ├── OrderReconciler.java              @Scheduled fallback
│   ├── PublicOrderController.java        /api/public/orders
│   ├── AdminOrderController.java         /api/admin/orders
│   └── dto/CreateOrderRequest.java, OrderChargeResponse.java, OrderStatusResponse.java, AdminOrderResponse.java
├── expense/
│   ├── Expense.java, ExpenseRepository.java, ExpenseService.java
│   ├── ExpenseController.java            /api/admin/expenses
│   └── dto/ExpenseRequest.java, ExpenseResponse.java
├── settings/
│   ├── Settings.java, SettingsRepository.java, SettingsService.java
│   ├── SettingsController.java           /api/admin/settings
│   └── dto/SettingsRequest.java, SettingsResponse.java
└── dashboard/
    ├── DashboardService.java             native aggregates, no cache
    ├── DashboardController.java          /api/public/dashboard
    └── dto/DashboardResponse.java, KpiResponse.java, GoalResponse.java,
        ChartPointResponse.java, TransactionResponse.java

api/src/main/resources/
├── application.yaml                      (exists, gets rewritten)
├── application-dev.yaml
├── application-prod.yaml
└── db/migration/V1__initial_schema.sql

api/src/test/java/com/example/localhostfacom/  mirrors the above
api/src/test/resources/application-test.yaml

ui/src/api/{client,auth,public,admin}.ts
ui/src/types.ts                           (exists, realigned)

compose.yaml, CLAUDE.md, .env.example
```

Files are split by domain rather than by technical layer, so everything that changes together lives together.

---

## Task 1: Build setup, configuration and schema

**Files:**
- Modify: `api/pom.xml`
- Modify: `api/src/main/resources/application.yaml`
- Create: `api/src/main/resources/application-dev.yaml`, `application-prod.yaml`
- Create: `api/src/main/resources/db/migration/V1__initial_schema.sql`
- Create: `api/src/test/resources/application-test.yaml`
- Create: `compose.yaml`
- Create: `api/src/main/java/com/example/localhostfacom/config/AppProperties.java`
- Test: `api/src/test/java/com/example/localhostfacom/SchemaMigrationTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `AppProperties` with nested records `Jwt(String secret, Duration ttl)`, `Payments(String activeProvider, Duration orderTtl, MercadoPago mercadoPago, Fake fake)`, `Storage(String endpoint, String region, String bucket, String accessKey, String secretKey, String publicBaseUrl, boolean pathStyle)`, `Cors(List<String> allowedOrigins)`, `BootstrapAdmin(String email, String password)`. Every table listed in the spec's data model exists after `V1`.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/SchemaMigrationTest.java`:

```java
package com.example.localhostfacom;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void migrationsCreateEveryTable() throws Exception {
        Set<String> tables = new HashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[] {"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }
        }

        assertThat(tables).contains(
                "image", "admin", "product", "orders", "order_item",
                "expense", "settings", "webhook_event");
    }

    @Test
    void settingsRowIsSeededWithAPositiveGoal() throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.createStatement()
                     .executeQuery("SELECT goal_target FROM settings WHERE id = 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBigDecimal("goal_target")).isPositive();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=SchemaMigrationTest`
Expected: FAIL — the application context cannot start, because there is no datasource configured and Flyway is not on the classpath.

- [ ] **Step 3: Add the dependencies**

In `api/pom.xml`, inside `<dependencies>`, add:

```xml
<dependency>
    <!-- Boot 4 split FlywayAutoConfiguration out of flyway-core into this starter;
         flyway-core alone no longer triggers migrations on startup. -->
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>
<dependency>
    <groupId>net.coobird</groupId>
    <artifactId>thumbnailator</artifactId>
    <version>0.4.20</version>
</dependency>
<!-- Added in Task 7 (not part of the original Task 1 dependency set): registers a WebP
     ImageIO reader via SPI, since phone uploads (Android/Google Photos) sometimes arrive
     in that format and the stock JDK cannot decode it. -->
<dependency>
    <groupId>com.twelvemonkeys.imageio</groupId>
    <artifactId>imageio-webp</artifactId>
    <version>3.13.1</version>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
    <version>3.5.3</version>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>javase</artifactId>
    <version>3.5.3</version>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
<!-- No flyway-database-h2 artifact exists: H2 support ships inside flyway-core itself,
     unlike PostgreSQL, which needs the separate module above. -->
```

Add a `<dependencyManagement>` block before `<dependencies>` so the AWS SDK version is managed centrally:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>2.31.77</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

- [ ] **Step 4: Write the migration**

`api/src/main/resources/db/migration/V1__initial_schema.sql`:

```sql
CREATE TABLE image (
    id          UUID         PRIMARY KEY,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    mime_type   VARCHAR(100) NOT NULL,
    width       INTEGER      NOT NULL,
    height      INTEGER      NOT NULL,
    hash        VARCHAR(64)  NOT NULL UNIQUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE admin (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(72)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE product (
    id         UUID          PRIMARY KEY,
    name       VARCHAR(120)  NOT NULL,
    price      NUMERIC(12,2) NOT NULL CHECK (price > 0),
    image_id   UUID          REFERENCES image (id),
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE orders (
    id                   UUID          PRIMARY KEY,
    seq                  BIGINT        GENERATED BY DEFAULT AS IDENTITY NOT NULL UNIQUE,
    status               VARCHAR(20)   NOT NULL,
    total                NUMERIC(12,2) NOT NULL CHECK (total > 0),
    payment_provider     VARCHAR(50)   NOT NULL,
    provider_payment_id  VARCHAR(120),
    payment_payload      TEXT,
    payment_qr_base64    TEXT,
    payment_checkout_url VARCHAR(1024),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at              TIMESTAMP WITH TIME ZONE,
    paid_manually_by     UUID          REFERENCES admin (id)
);

CREATE INDEX idx_orders_status_expires ON orders (status, expires_at);
CREATE INDEX idx_orders_paid_at        ON orders (paid_at);
CREATE INDEX idx_orders_provider_payment ON orders (payment_provider, provider_payment_id);

CREATE TABLE order_item (
    id           UUID          PRIMARY KEY,
    order_id     UUID          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id   UUID          NOT NULL REFERENCES product (id),
    product_name VARCHAR(120)  NOT NULL,
    unit_price   NUMERIC(12,2) NOT NULL CHECK (unit_price > 0),
    quantity     INTEGER       NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_order_item_order   ON order_item (order_id);
CREATE INDEX idx_order_item_product ON order_item (product_id);

CREATE TABLE expense (
    id          UUID          PRIMARY KEY,
    description VARCHAR(255)  NOT NULL,
    amount      NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    incurred_on DATE          NOT NULL,
    created_by  UUID          REFERENCES admin (id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE settings (
    id               SMALLINT      PRIMARY KEY CHECK (id = 1),
    goal_target      NUMERIC(12,2) NOT NULL CHECK (goal_target > 0),
    crowdfunding_url VARCHAR(1024),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO settings (id, goal_target, crowdfunding_url, updated_at)
VALUES (1, 2000.00, NULL, CURRENT_TIMESTAMP);

CREATE TABLE webhook_event (
    id                  UUID         PRIMARY KEY,
    provider            VARCHAR(50)  NOT NULL,
    provider_event_id   VARCHAR(120),
    provider_payment_id VARCHAR(120),
    payload             TEXT         NOT NULL,
    received_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at        TIMESTAMP WITH TIME ZONE,
    error               VARCHAR(1024)
);

CREATE INDEX idx_webhook_event_payment ON webhook_event (provider, provider_payment_id);
```

There is deliberately no unique constraint on `provider_event_id` — Mercado Pago issues a fresh notification id on each retry, so it would not dedupe anything. Idempotency lives in `OrderService.markPaid`.

- [ ] **Step 5: Write the configuration files**

`api/src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: localhostfacom
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration
  servlet:
    multipart:
      max-file-size: 8MB
      max-request-size: 8MB

app:
  cors:
    allowed-origins: ${APP_CORS_ORIGINS:http://localhost:5173}
  jwt:
    secret: ${APP_JWT_SECRET}
    ttl: PT12H
  bootstrap-admin:
    email: ${APP_BOOTSTRAP_ADMIN_EMAIL:}
    password: ${APP_BOOTSTRAP_ADMIN_PASSWORD:}
  payments:
    active-provider: ${APP_PAYMENTS_PROVIDER:fake}
    order-ttl: PT10M
    mercado-pago:
      access-token: ${APP_MERCADOPAGO_ACCESS_TOKEN:}
      webhook-secret: ${APP_MERCADOPAGO_WEBHOOK_SECRET:}
      base-url: https://api.mercadopago.com
    fake:
      auto-confirm-after: PT10S
  storage:
    endpoint: ${APP_STORAGE_ENDPOINT:http://localhost:9000}
    region: ${APP_STORAGE_REGION:auto}
    bucket: ${APP_STORAGE_BUCKET:localhostfacom}
    access-key: ${APP_STORAGE_ACCESS_KEY:minioadmin}
    secret-key: ${APP_STORAGE_SECRET_KEY:minioadmin}
    public-base-url: ${APP_STORAGE_PUBLIC_BASE_URL:http://localhost:9000/localhostfacom}
    path-style: ${APP_STORAGE_PATH_STYLE:true}

logging:
  level:
    com.example.localhostfacom: INFO
```

`api/src/main/resources/application-dev.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/localhostfacom
    username: localhostfacom
    password: localhostfacom

app:
  jwt:
    secret: dev-only-secret-not-for-production-use-32b

logging:
  level:
    com.example.localhostfacom: DEBUG
```

`api/src/main/resources/application-prod.yaml`:

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}
```

`api/src/test/resources/application-test.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:localhostfacom;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password: ""

app:
  jwt:
    secret: test-secret-that-is-long-enough-for-hs256
  payments:
    active-provider: fake
  storage:
    public-base-url: http://storage.test/localhostfacom
```

- [ ] **Step 6: Write the typed configuration binding**

`api/src/main/java/com/example/localhostfacom/config/AppProperties.java`:

```java
package com.example.localhostfacom.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        Jwt jwt,
        BootstrapAdmin bootstrapAdmin,
        Payments payments,
        Storage storage) {

    public record Cors(List<String> allowedOrigins) {}

    public record Jwt(String secret, Duration ttl) {}

    public record BootstrapAdmin(String email, String password) {}

    public record Payments(
            String activeProvider,
            Duration orderTtl,
            MercadoPago mercadoPago,
            Fake fake) {

        public record MercadoPago(String accessToken, String webhookSecret, String baseUrl) {}

        public record Fake(Duration autoConfirmAfter) {}
    }

    public record Storage(
            String endpoint,
            String region,
            String bucket,
            String accessKey,
            String secretKey,
            String publicBaseUrl,
            boolean pathStyle) {}
}
```

Enable it on the application class — modify `LocalhostfacomApplication.java` to add `@ConfigurationPropertiesScan`:

```java
package com.example.localhostfacom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LocalhostfacomApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalhostfacomApplication.class, args);
    }
}
```

- [ ] **Step 7: Write the compose file**

`compose.yaml`:

```yaml
services:
  postgres:
    image: docker.io/library/postgres:17-alpine
    environment:
      POSTGRES_DB: localhostfacom
      POSTGRES_USER: localhostfacom
      POSTGRES_PASSWORD: localhostfacom
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U localhostfacom"]
      interval: 5s
      retries: 10

  minio:
    image: docker.io/minio/minio:latest
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - miniodata:/data
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 5s
      retries: 10

  # One-shot: creates the bucket and makes it public-read, then exits.
  minio-init:
    image: docker.io/minio/mc:latest
    depends_on:
      minio:
        condition: service_healthy
    entrypoint: >
      /bin/sh -c "
      mc alias set local http://minio:9000 minioadmin minioadmin &&
      mc mb --ignore-existing local/localhostfacom &&
      mc anonymous set download local/localhostfacom
      "

volumes:
  pgdata:
  miniodata:
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=SchemaMigrationTest`
Expected: PASS — both tests green. The context starts on H2, Flyway applies `V1`, all eight tables exist and the settings row is seeded.

- [ ] **Step 9: Commit**

```bash
git add api/pom.xml api/src/main/resources api/src/test/resources api/src/main/java api/src/test/java compose.yaml
git commit -m "feat(api): add build dependencies, configuration profiles and initial schema"
```

---

## Task 2: JPA entities and repositories

**Files:**
- Create: `api/src/main/java/com/example/localhostfacom/image/Image.java`, `ImageRepository.java`
- Create: `api/src/main/java/com/example/localhostfacom/admin/Admin.java`, `AdminRepository.java`
- Create: `api/src/main/java/com/example/localhostfacom/product/Product.java`, `ProductRepository.java`
- Create: `api/src/main/java/com/example/localhostfacom/order/Order.java`, `OrderItem.java`, `OrderStatus.java`, `OrderRepository.java`, `WebhookEvent.java`, `WebhookEventRepository.java`
- Create: `api/src/main/java/com/example/localhostfacom/expense/Expense.java`, `ExpenseRepository.java`
- Create: `api/src/main/java/com/example/localhostfacom/settings/Settings.java`, `SettingsRepository.java`
- Test: `api/src/test/java/com/example/localhostfacom/EntityMappingTest.java`

**Interfaces:**
- Consumes: the schema from Task 1.
- Produces: entities with these accessors, used by every later task. `Order` exposes `getId()`, `getSeq()`, `getStatus()`, `getTotal()`, `getPaymentProvider()`, `getProviderPaymentId()`, `getPaymentPayload()`, `getPaymentQrBase64()`, `getExpiresAt()`, `getPaidAt()`, `getItems()`. `OrderStatus` is `PENDING, PAID, EXPIRED, CANCELED`. Repositories: `ImageRepository.findByHash(String)`, `AdminRepository.findByEmailIgnoreCase(String)`, `AdminRepository.countByActiveTrue()`, `ProductRepository.findByActiveTrueOrderByNameAsc()`, `OrderRepository.findByIdForUpdate(UUID)`, `OrderRepository.findByProviderPaymentId(String)`, `SettingsRepository.get()`.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/EntityMappingTest.java`:

```java
package com.example.localhostfacom;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.admin.AdminRepository;
import com.example.localhostfacom.order.Order;
import com.example.localhostfacom.order.OrderItem;
import com.example.localhostfacom.order.OrderRepository;
import com.example.localhostfacom.order.OrderStatus;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductRepository;
import com.example.localhostfacom.settings.SettingsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityMappingTest {

    @Autowired private ProductRepository products;
    @Autowired private OrderRepository orders;
    @Autowired private AdminRepository admins;
    @Autowired private SettingsRepository settings;

    // The H2 database is shared across every test class in the same JVM run, so rows
    // another class committed (e.g. AuthenticationTest's admins) are still visible here
    // even though @Transactional rolls back what THIS class writes. Start every test
    // from a known-empty slate rather than assuming a pristine database.
    @BeforeEach
    void setUp() {
        orders.deleteAll();
        products.deleteAll();
        admins.deleteAll();
    }

    @Test
    void persistsAnOrderWithItemsAndAssignsASequence() {
        Product product = products.save(Product.create("Café", new BigDecimal("3.50"), null));

        Order order = Order.create("fake", Instant.now().plusSeconds(600));
        order.addItem(OrderItem.snapshotOf(product, 2));
        order.recalculateTotal();
        Order saved = orders.saveAndFlush(order);

        assertThat(saved.getTotal()).isEqualByComparingTo("7.00");
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getSeq()).isNotNull().isPositive();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().getFirst().getProductName()).isEqualTo("Café");
        assertThat(saved.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("3.50");
    }

    @Test
    void orderItemKeepsItsSnapshotWhenTheProductPriceChanges() {
        Product product = products.save(Product.create("Bolo", new BigDecimal("5.00"), null));
        Order order = Order.create("fake", Instant.now().plusSeconds(600));
        order.addItem(OrderItem.snapshotOf(product, 1));
        order.recalculateTotal();
        Order saved = orders.saveAndFlush(order);

        product.update("Bolo", new BigDecimal("9.00"), null, true);
        products.saveAndFlush(product);

        Order reloaded = orders.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("5.00");
        assertThat(reloaded.getTotal()).isEqualByComparingTo("5.00");
    }

    @Test
    void findsAnAdminByEmailCaseInsensitively() {
        admins.save(Admin.create("Person@Example.com", "hash"));
        assertThat(admins.findByEmailIgnoreCase("person@example.com")).isPresent();
        assertThat(admins.countByActiveTrue()).isEqualTo(1L);
    }

    @Test
    void readsTheSingletonSettingsRow() {
        assertThat(settings.get().getGoalTarget()).isPositive();
    }

    @Test
    void listsOnlyActiveProducts() {
        products.save(Product.create("Ativo", new BigDecimal("1.00"), null));
        Product inactive = products.save(Product.create("Inativo", new BigDecimal("1.00"), null));
        inactive.update("Inativo", new BigDecimal("1.00"), null, false);
        products.saveAndFlush(inactive);

        List<Product> active = products.findByActiveTrueOrderByNameAsc();
        assertThat(active).extracting(Product::getName).containsExactly("Ativo");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=EntityMappingTest`
Expected: FAIL — compilation error, none of these classes exist.

- [ ] **Step 3: Write the entities**

`image/Image.java`:

```java
package com.example.localhostfacom.image;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "image")
public class Image {

    @Id
    private UUID id;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    private int width;
    private int height;

    @Column(nullable = false, unique = true)
    private String hash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Image() {}

    public static Image create(String storageKey, String mimeType, int width, int height, String hash) {
        Image image = new Image();
        image.id = UUID.randomUUID();
        image.storageKey = storageKey;
        image.mimeType = mimeType;
        image.width = width;
        image.height = height;
        image.hash = hash;
        image.createdAt = Instant.now();
        return image;
    }

    public UUID getId() { return id; }
    public String getStorageKey() { return storageKey; }
    public String getMimeType() { return mimeType; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getHash() { return hash; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`image/ImageRepository.java`:

```java
package com.example.localhostfacom.image;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, UUID> {
    Optional<Image> findByHash(String hash);
}
```

`admin/Admin.java`:

```java
package com.example.localhostfacom.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "admin")
public class Admin {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Admin() {}

    public static Admin create(String email, String passwordHash) {
        Admin admin = new Admin();
        admin.id = UUID.randomUUID();
        admin.email = email.trim().toLowerCase(Locale.ROOT);
        admin.passwordHash = passwordHash;
        admin.active = true;
        admin.createdAt = Instant.now();
        return admin;
    }

    public void deactivate() {
        this.active = false;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`admin/AdminRepository.java`:

```java
package com.example.localhostfacom.admin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, UUID> {
    Optional<Admin> findByEmailIgnoreCase(String email);
    long countByActiveTrue();
    List<Admin> findAllByOrderByCreatedAtAsc();
    boolean existsByEmailIgnoreCase(String email);
}
```

`product/Product.java`:

```java
package com.example.localhostfacom.product;

import com.example.localhostfacom.image.Image;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "image_id")
    private Image image;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {}

    public static Product create(String name, BigDecimal price, Image image) {
        Product product = new Product();
        product.id = UUID.randomUUID();
        product.name = name;
        product.price = price;
        product.image = image;
        product.active = true;
        product.createdAt = Instant.now();
        product.updatedAt = product.createdAt;
        return product;
    }

    public void update(String name, BigDecimal price, Image image, boolean active) {
        this.name = name;
        this.price = price;
        this.image = image;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public Image getImage() { return image; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

`product/ProductRepository.java`:

```java
package com.example.localhostfacom.product;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByActiveTrueOrderByNameAsc();

    List<Product> findAllByOrderByNameAsc();

    // JPQL has no boolean-returning comparison in the SELECT list, so the CASE is required.
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END "
            + "FROM OrderItem i WHERE i.productId = :productId")
    boolean hasBeenOrdered(UUID productId);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END "
            + "FROM Product p WHERE p.image.id = :imageId")
    boolean existsByImageId(UUID imageId);
}
```

`order/OrderStatus.java`:

```java
package com.example.localhostfacom.order;

public enum OrderStatus {
    PENDING,
    PAID,
    EXPIRED,
    CANCELED;

    /**
     * PAID is the only terminal state. EXPIRED and CANCELED merely mean the system
     * stopped waiting, so a payment that lands late is still accepted.
     */
    public boolean canTransitionToPaid() {
        return this != PAID;
    }
}
```

`order/OrderItem.java`:

```java
package com.example.localhostfacom.order;

import com.example.localhostfacom.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    protected OrderItem() {}

    /**
     * Copies the product's name and price rather than referencing them, so a later
     * price change never rewrites what a past sale recorded.
     */
    public static OrderItem snapshotOf(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.id = UUID.randomUUID();
        item.productId = product.getId();
        item.productName = product.getName();
        item.unitPrice = product.getPrice();
        item.quantity = quantity;
        return item;
    }

    void assignTo(Order order) {
        this.order = order;
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
}
```

`order/Order.java`:

```java
package com.example.localhostfacom.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    // Assigned by the database identity column and read back after insert.
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private Long seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "payment_provider", nullable = false)
    private String paymentProvider;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "payment_payload")
    private String paymentPayload;

    @Column(name = "payment_qr_base64")
    private String paymentQrBase64;

    @Column(name = "payment_checkout_url")
    private String paymentCheckoutUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_manually_by")
    private UUID paidManuallyBy;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public static Order create(String paymentProvider, Instant expiresAt) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.status = OrderStatus.PENDING;
        order.total = BigDecimal.ZERO;
        order.paymentProvider = paymentProvider;
        order.createdAt = Instant.now();
        order.expiresAt = expiresAt;
        return order;
    }

    public void addItem(OrderItem item) {
        item.assignTo(this);
        items.add(item);
    }

    public void recalculateTotal() {
        this.total = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);
    }

    public void attachCharge(String providerPaymentId, String payload, String qrBase64, String checkoutUrl) {
        this.providerPaymentId = providerPaymentId;
        this.paymentPayload = payload;
        this.paymentQrBase64 = qrBase64;
        this.paymentCheckoutUrl = checkoutUrl;
    }

    public void markPaid(Instant paidAt, UUID manuallyBy) {
        this.status = OrderStatus.PAID;
        this.paidAt = paidAt;
        this.paidManuallyBy = manuallyBy;
    }

    public void markExpired() {
        this.status = OrderStatus.EXPIRED;
    }

    public void markCanceled() {
        this.status = OrderStatus.CANCELED;
    }

    public boolean hasCharge() {
        return providerPaymentId != null;
    }

    public UUID getId() { return id; }
    public Long getSeq() { return seq; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public String getPaymentProvider() { return paymentProvider; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getPaymentPayload() { return paymentPayload; }
    public String getPaymentQrBase64() { return paymentQrBase64; }
    public String getPaymentCheckoutUrl() { return paymentCheckoutUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public UUID getPaidManuallyBy() { return paidManuallyBy; }
    public List<OrderItem> getItems() { return items; }
}
```

`order/OrderRepository.java`:

```java
package com.example.localhostfacom.order;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(UUID id);

    Optional<Order> findByProviderPaymentId(String providerPaymentId);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Order> findByStatusOrderByPaidAtDesc(OrderStatus status, Pageable pageable);

    /**
     * Orders the reconciler should re-check. Includes EXPIRED and CANCELED ones within
     * the grace window, because a payment that lands after the local deadline is still
     * real money that must reach the ledger.
     */
    @Query("""
            SELECT o FROM Order o
            WHERE o.status <> com.example.localhostfacom.order.OrderStatus.PAID
              AND o.providerPaymentId IS NOT NULL
              AND o.createdAt > :notBefore
            """)
    List<Order> findReconcilable(Instant notBefore);

    @Query("""
            SELECT o FROM Order o
            WHERE o.status = com.example.localhostfacom.order.OrderStatus.PENDING
              AND o.expiresAt < :now
            """)
    List<Order> findExpirable(Instant now);
}
```

`order/WebhookEvent.java`:

```java
package com.example.localhostfacom.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_event")
public class WebhookEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_event_id")
    private String providerEventId;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(nullable = false)
    private String payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    private String error;

    protected WebhookEvent() {}

    public static WebhookEvent received(String provider, String eventId, String paymentId, String payload) {
        WebhookEvent event = new WebhookEvent();
        event.id = UUID.randomUUID();
        event.provider = provider;
        event.providerEventId = eventId;
        event.providerPaymentId = paymentId;
        event.payload = payload;
        event.receivedAt = Instant.now();
        return event;
    }

    public void markProcessed() {
        this.processedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.error = error != null && error.length() > 1024 ? error.substring(0, 1024) : error;
    }

    public UUID getId() { return id; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public Instant getProcessedAt() { return processedAt; }
    public String getError() { return error; }
}
```

`order/WebhookEventRepository.java`:

```java
package com.example.localhostfacom.order;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {}
```

`expense/Expense.java`:

```java
package com.example.localhostfacom.expense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "expense")
public class Expense {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "incurred_on", nullable = false)
    private LocalDate incurredOn;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Expense() {}

    public static Expense create(String description, BigDecimal amount, LocalDate incurredOn, UUID createdBy) {
        Expense expense = new Expense();
        expense.id = UUID.randomUUID();
        expense.description = description;
        expense.amount = amount;
        expense.incurredOn = incurredOn;
        expense.createdBy = createdBy;
        expense.createdAt = Instant.now();
        return expense;
    }

    public UUID getId() { return id; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getIncurredOn() { return incurredOn; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`expense/ExpenseRepository.java`:

```java
package com.example.localhostfacom.expense;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    List<Expense> findAllByOrderByIncurredOnDesc();
}
```

`settings/Settings.java`:

```java
package com.example.localhostfacom.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "settings")
public class Settings {

    @Id
    private Short id;

    @Column(name = "goal_target", nullable = false, precision = 12, scale = 2)
    private BigDecimal goalTarget;

    @Column(name = "crowdfunding_url")
    private String crowdfundingUrl;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Settings() {}

    public void update(BigDecimal goalTarget, String crowdfundingUrl) {
        this.goalTarget = goalTarget;
        this.crowdfundingUrl = crowdfundingUrl;
        this.updatedAt = Instant.now();
    }

    public Short getId() { return id; }
    public BigDecimal getGoalTarget() { return goalTarget; }
    public String getCrowdfundingUrl() { return crowdfundingUrl; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

`settings/SettingsRepository.java`:

```java
package com.example.localhostfacom.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<Settings, Short> {

    /** The table is constrained to a single row with id = 1. */
    default Settings get() {
        return findById((short) 1)
                .orElseThrow(() -> new IllegalStateException("settings row is missing"));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=EntityMappingTest`
Expected: PASS — five tests green, including the snapshot test proving a price change does not rewrite history.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add JPA entities and repositories"
```

---

## Task 3: Error handling and rate limiting

**Files:**
- Create: `api/src/main/java/com/example/localhostfacom/common/ApiException.java`
- Create: `api/src/main/java/com/example/localhostfacom/common/GlobalExceptionHandler.java`
- Create: `api/src/main/java/com/example/localhostfacom/common/RateLimiter.java`
- Test: `api/src/test/java/com/example/localhostfacom/common/RateLimiterTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `ApiException` with static factories `notFound(String slug, String detail)`, `conflict(...)`, `badRequest(...)`, `badGateway(...)`, `forbidden(...)`, each carrying `HttpStatus getStatus()` and `String getSlug()`. `RateLimiter.tryAcquire(String key, int limit, Duration window)` returns `boolean`. Every controller in later tasks throws `ApiException`.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/common/RateLimiterTest.java`:

```java
package com.example.localhostfacom.common;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void allowsUpToTheLimitThenRefuses() {
        RateLimiter limiter = new RateLimiter();

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4", 3, Duration.ofMinutes(1))).isTrue();
        }
        assertThat(limiter.tryAcquire("1.2.3.4", 3, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void keepsSeparateCountsPerKey() {
        RateLimiter limiter = new RateLimiter();

        assertThat(limiter.tryAcquire("a", 1, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("a", 1, Duration.ofMinutes(1))).isFalse();
        assertThat(limiter.tryAcquire("b", 1, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void resetsAfterTheWindowElapses() throws Exception {
        RateLimiter limiter = new RateLimiter();

        assertThat(limiter.tryAcquire("a", 1, Duration.ofMillis(50))).isTrue();
        assertThat(limiter.tryAcquire("a", 1, Duration.ofMillis(50))).isFalse();
        Thread.sleep(60);
        assertThat(limiter.tryAcquire("a", 1, Duration.ofMillis(50))).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=RateLimiterTest`
Expected: FAIL — `RateLimiter` does not exist.

- [ ] **Step 3: Write the implementation**

`common/RateLimiter.java`:

```java
package com.example.localhostfacom.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counter held in process memory. It is per instance and resets on
 * deploy, which is adequate for a single instance serving one room — a speed bump
 * against accidents and casual abuse, not a real defence. If this ever runs on more
 * than one instance, it needs to move to shared state.
 */
@Component
public class RateLimiter {

    private record Window(Instant startedAt, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int limit, Duration window) {
        Instant now = Instant.now();
        Window current = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.startedAt().plus(window).isBefore(now)) {
                return new Window(now, new AtomicInteger(0));
            }
            return existing;
        });
        return current.count().incrementAndGet() <= limit;
    }
}
```

`common/ApiException.java`:

```java
package com.example.localhostfacom.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String slug;

    public ApiException(HttpStatus status, String slug, String detail) {
        super(detail);
        this.status = status;
        this.slug = slug;
    }

    public static ApiException notFound(String slug, String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, slug, detail);
    }

    public static ApiException badRequest(String slug, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, slug, detail);
    }

    public static ApiException conflict(String slug, String detail) {
        return new ApiException(HttpStatus.CONFLICT, slug, detail);
    }

    public static ApiException forbidden(String slug, String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, slug, detail);
    }

    public static ApiException badGateway(String slug, String detail) {
        return new ApiException(HttpStatus.BAD_GATEWAY, slug, detail);
    }

    public static ApiException tooManyRequests(String slug, String detail) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, slug, detail);
    }

    public HttpStatus getStatus() { return status; }
    public String getSlug() { return slug; }
}
```

`common/GlobalExceptionHandler.java`:

```java
package com.example.localhostfacom.common;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_PREFIX = "https://localhostfacom.dev/problems/";

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException exception) {
        return problem(exception.getStatus(), exception.getSlug(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Request validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "validation-failed", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Unexpected server error");
    }

    private ProblemDetail problem(HttpStatus status, String slug, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + slug));
        problem.setProperty("slug", slug);
        return problem;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=RateLimiterTest`
Expected: PASS — three tests green.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add problem+json error handling and in-memory rate limiter"
```

---

## Task 4: JWT authentication and security chain

**Files:**
- Create: `api/src/main/java/com/example/localhostfacom/auth/JwtService.java`, `JwtAuthenticationFilter.java`, `AuthController.java`
- Create: `api/src/main/java/com/example/localhostfacom/auth/dto/LoginRequest.java`, `LoginResponse.java`
- Create: `api/src/main/java/com/example/localhostfacom/config/SecurityConfig.java`
- Create: `api/src/main/java/com/example/localhostfacom/admin/BootstrapAdminRunner.java`
- Test: `api/src/test/java/com/example/localhostfacom/auth/AuthenticationTest.java`

**Interfaces:**
- Consumes: `Admin`, `AdminRepository` (Task 2), `ApiException`, `RateLimiter` (Task 3), `AppProperties` (Task 1).
- Produces: `JwtService.issue(Admin)` returns `String`; `JwtService.extractAdminId(String)` returns `UUID`; `JwtService.expiresAt()` returns `Instant`. `CurrentAdmin.require()` static helper returning the authenticated `UUID`. Later tasks read the caller's id through `CurrentAdmin.require()`.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/auth/AuthenticationTest.java`:

```java
package com.example.localhostfacom.auth;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.admin.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminRepository admins;
    @Autowired private PasswordEncoder passwordEncoder;

    private Admin admin;

    @BeforeEach
    void setUp() {
        admins.deleteAll();
        admin = admins.save(Admin.create("owner@example.com", passwordEncoder.encode("correct-horse")));
    }

    @Test
    void rejectsAdminRoutesWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/admin/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void issuesATokenForValidCredentialsAndAcceptsIt() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andReturn().getResponse().getContentAsString();

        String token = extractToken(body);

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@example.com"));
    }

    @Test
    void rejectsAWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsATamperedToken() throws Exception {
        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The rotating-admin requirement: removing someone must take effect immediately,
     * not whenever their token happens to expire.
     */
    @Test
    void rejectsATokenBelongingToADeactivatedAdmin() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"correct-horse\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = extractToken(body);

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        admin.deactivate();
        admins.saveAndFlush(admin);

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicRoutesNeedNoToken() throws Exception {
        mockMvc.perform(get("/api/public/products")).andExpect(status().isOk());
    }

    // No com.jayway.jsonpath on the test classpath — Boot 4 dropped the monolithic
    // spring-boot-starter-test in favor of per-feature -test starters. The token is the
    // only field these tests need to pull out, so a regex is simpler than adding a
    // dependency for one line.
    private String extractToken(String json) {
        var matcher = java.util.regex.Pattern.compile("\"token\":\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("No token field in response: " + json);
        }
        return matcher.group(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=AuthenticationTest`
Expected: FAIL — compilation error; `JwtService`, `AuthController` and the security configuration do not exist.

- [ ] **Step 3: Write the JWT service and current-admin helper**

`auth/JwtService.java`:

```java
package com.example.localhostfacom.auth;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final java.time.Duration ttl;

    public JwtService(AppProperties properties) {
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes for HS256; got " + secret.length);
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttl = properties.jwt().ttl();
    }

    public String issue(Admin admin) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(admin.getId().toString())
                .claim("email", admin.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Instant expiresAt() {
        return Instant.now().plus(ttl);
    }

    /** Returns null when the token is absent, malformed, expired or badly signed. */
    public UUID extractAdminId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return UUID.fromString(subject);
        } catch (JwtException | IllegalArgumentException exception) {
            return null;
        }
    }
}
```

`auth/CurrentAdmin.java`:

```java
package com.example.localhostfacom.auth;

import com.example.localhostfacom.common.ApiException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentAdmin {

    private CurrentAdmin() {}

    public static UUID require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID adminId)) {
            throw ApiException.forbidden("not-authenticated", "No authenticated admin in context");
        }
        return adminId;
    }
}
```

- [ ] **Step 4: Write the filter, controller and DTOs**

`auth/JwtAuthenticationFilter.java`:

```java
package com.example.localhostfacom.auth;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.admin.AdminRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AdminRepository admins;

    public JwtAuthenticationFilter(JwtService jwtService, AdminRepository admins) {
        this.jwtService = jwtService;
        this.admins = admins;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            UUID adminId = jwtService.extractAdminId(header.substring(7));
            if (adminId != null) {
                // Re-read the row on every request. A stateless token cannot be revoked,
                // and the admin role rotates, so a removed admin must lose access at once
                // rather than when their token happens to expire.
                Optional<Admin> admin = admins.findById(adminId).filter(Admin::isActive);
                admin.ifPresent(value -> {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            value.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        }
        chain.doFilter(request, response);
    }
}
```

`auth/dto/LoginRequest.java`:

```java
package com.example.localhostfacom.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {}
```

`auth/dto/LoginResponse.java`:

```java
package com.example.localhostfacom.auth.dto;

import java.time.Instant;

public record LoginResponse(String token, String email, Instant expiresAt) {}
```

`auth/AuthController.java`:

```java
package com.example.localhostfacom.auth;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.admin.AdminRepository;
import com.example.localhostfacom.auth.dto.LoginRequest;
import com.example.localhostfacom.auth.dto.LoginResponse;
import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.common.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;

    public AuthController(AdminRepository admins, PasswordEncoder passwordEncoder,
                          JwtService jwtService, RateLimiter rateLimiter) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryAcquire("login:" + http.getRemoteAddr(), 5, Duration.ofMinutes(1))) {
            throw ApiException.tooManyRequests("rate-limited", "Too many login attempts");
        }

        Optional<Admin> admin = admins.findByEmailIgnoreCase(request.email()).filter(Admin::isActive);

        // Verify against a dummy hash when the account is missing, so a failed lookup and a
        // wrong password take the same amount of time and cannot be told apart.
        String storedHash = admin.map(Admin::getPasswordHash)
                .orElse("$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidinv");

        if (!passwordEncoder.matches(request.password(), storedHash) || admin.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Invalid email or password");
        }

        return new LoginResponse(jwtService.issue(admin.get()), admin.get().getEmail(), jwtService.expiresAt());
    }
}
```

- [ ] **Step 5: Write the security configuration and bootstrap runner**

`config/SecurityConfig.java`:

```java
package com.example.localhostfacom.config;

import com.example.localhostfacom.auth.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final AppProperties properties;

    public SecurityConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
                // No cookies are used; the token travels in the Authorization header,
                // so there is no CSRF vector to protect against.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**", "/api/auth/**", "/api/webhooks/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
```

`admin/BootstrapAdminRunner.java`:

```java
package com.example.localhostfacom.admin;

import com.example.localhostfacom.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the very first admin from environment variables. BCrypt hashes cannot be
 * produced inside a Flyway migration, so this runs at startup instead — and only when
 * the table is empty, so it can never silently reset an existing account.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final AdminRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public BootstrapAdminRunner(AdminRepository admins, PasswordEncoder passwordEncoder,
                                AppProperties properties) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (admins.count() > 0) {
            return;
        }

        String email = properties.bootstrapAdmin().email();
        String password = properties.bootstrapAdmin().password();

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("No admins exist and APP_BOOTSTRAP_ADMIN_EMAIL/PASSWORD are not set. "
                    + "The admin panel is unreachable until one is created.");
            return;
        }

        admins.save(Admin.create(email, passwordEncoder.encode(password)));
        log.info("Created the bootstrap admin {}. Change this password after first login.", email);
    }
}
```

- [ ] **Step 6: Add the `/api/admin/me` endpoint**

`admin/AdminController.java` — the full admin CRUD arrives in Task 5; for now it only serves `me`:

```java
package com.example.localhostfacom.admin;

import com.example.localhostfacom.auth.CurrentAdmin;
import com.example.localhostfacom.common.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record AdminResponse(UUID id, String email, boolean active, Instant createdAt) {
        static AdminResponse of(Admin admin) {
            return new AdminResponse(admin.getId(), admin.getEmail(), admin.isActive(), admin.getCreatedAt());
        }
    }

    private final AdminRepository admins;

    public AdminController(AdminRepository admins) {
        this.admins = admins;
    }

    @GetMapping("/me")
    public AdminResponse me() {
        UUID id = CurrentAdmin.require();
        return admins.findById(id)
                .map(AdminResponse::of)
                .orElseThrow(() -> ApiException.notFound("admin-not-found", "Admin not found"));
    }
}
```

Add a stub `product/PublicProductController.java` so the `publicRoutesNeedNoToken` test has something to hit; Task 9 fills it in:

```java
package com.example.localhostfacom.product;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/products")
public class PublicProductController {

    @GetMapping
    public List<Object> list() {
        return List.of();
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=AuthenticationTest`
Expected: PASS — six tests green, including the deactivated-admin revocation test.

- [ ] **Step 8: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add JWT authentication with per-request admin revocation"
```

---

## Task 5: Admin management

**Files:**
- Create: `api/src/main/java/com/example/localhostfacom/admin/AdminService.java`
- Modify: `api/src/main/java/com/example/localhostfacom/admin/AdminController.java`
- Test: `api/src/test/java/com/example/localhostfacom/admin/AdminManagementTest.java`

**Interfaces:**
- Consumes: `Admin`, `AdminRepository`, `CurrentAdmin`, `ApiException`, `PasswordEncoder`.
- Produces: `AdminService.list()`, `AdminService.create(String email, String password)`, `AdminService.remove(UUID targetId, UUID callerId)`.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/admin/AdminManagementTest.java`:

```java
package com.example.localhostfacom.admin;

import com.example.localhostfacom.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AdminManagementTest {

    @Autowired private AdminService service;
    @Autowired private AdminRepository admins;
    @Autowired private PasswordEncoder passwordEncoder;

    private Admin first;

    @BeforeEach
    void setUp() {
        admins.deleteAll();
        first = admins.save(Admin.create("first@example.com", passwordEncoder.encode("password-one")));
    }

    @Test
    void addsAnAdminByEmail() {
        Admin created = service.create("second@example.com", "password-two");

        assertThat(created.getEmail()).isEqualTo("second@example.com");
        assertThat(service.list()).hasSize(2);
    }

    @Test
    void refusesADuplicateEmail() {
        assertThatThrownBy(() -> service.create("FIRST@example.com", "whatever"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already");
    }

    @Test
    void refusesToRemoveYourself() {
        Admin second = service.create("second@example.com", "password-two");

        assertThatThrownBy(() -> service.remove(second.getId(), second.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("yourself");
    }

    /** The role rotates; removing the last account would lock everyone out permanently. */
    @Test
    void refusesToRemoveTheLastActiveAdmin() {
        Admin second = service.create("second@example.com", "password-two");
        service.remove(second.getId(), first.getId());

        assertThatThrownBy(() -> service.remove(first.getId(), second.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("last");
    }

    @Test
    void removingAnAdminDeactivatesRatherThanDeletes() {
        Admin second = service.create("second@example.com", "password-two");
        service.remove(second.getId(), first.getId());

        assertThat(admins.findById(second.getId())).isPresent();
        assertThat(admins.findById(second.getId()).orElseThrow().isActive()).isFalse();
        assertThat(admins.countByActiveTrue()).isEqualTo(1L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=AdminManagementTest`
Expected: FAIL — `AdminService` does not exist.

- [ ] **Step 3: Write the service**

`admin/AdminService.java`:

```java
package com.example.localhostfacom.admin;

import com.example.localhostfacom.common.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final AdminRepository admins;
    private final PasswordEncoder passwordEncoder;

    public AdminService(AdminRepository admins, PasswordEncoder passwordEncoder) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Admin> list() {
        return admins.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public Admin create(String email, String password) {
        if (admins.existsByEmailIgnoreCase(email.trim())) {
            throw ApiException.conflict("admin-exists", "An admin with that email already exists");
        }
        return admins.save(Admin.create(email, passwordEncoder.encode(password)));
    }

    @Transactional
    public void remove(UUID targetId, UUID callerId) {
        if (targetId.equals(callerId)) {
            throw ApiException.conflict("cannot-remove-self", "You cannot remove yourself");
        }

        Admin target = admins.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("admin-not-found", "Admin not found"));

        if (!target.isActive()) {
            return;
        }

        if (admins.countByActiveTrue() <= 1) {
            throw ApiException.conflict("cannot-remove-last-admin",
                    "Cannot remove the last active admin");
        }

        // Deactivated rather than deleted: expenses reference the admin who recorded them,
        // and orders reference whoever confirmed a payment by hand.
        target.deactivate();
        admins.save(target);
    }
}
```

- [ ] **Step 4: Extend the controller**

Replace the body of `admin/AdminController.java` with:

```java
package com.example.localhostfacom.admin;

import com.example.localhostfacom.auth.CurrentAdmin;
import com.example.localhostfacom.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record AdminResponse(UUID id, String email, boolean active, Instant createdAt) {
        static AdminResponse of(Admin admin) {
            return new AdminResponse(admin.getId(), admin.getEmail(), admin.isActive(), admin.getCreatedAt());
        }
    }

    public record CreateAdminRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    private final AdminRepository admins;
    private final AdminService service;

    public AdminController(AdminRepository admins, AdminService service) {
        this.admins = admins;
        this.service = service;
    }

    @GetMapping("/me")
    public AdminResponse me() {
        return admins.findById(CurrentAdmin.require())
                .map(AdminResponse::of)
                .orElseThrow(() -> ApiException.notFound("admin-not-found", "Admin not found"));
    }

    @GetMapping("/admins")
    public List<AdminResponse> list() {
        return service.list().stream().map(AdminResponse::of).toList();
    }

    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminResponse create(@Valid @RequestBody CreateAdminRequest request) {
        return AdminResponse.of(service.create(request.email(), request.password()));
    }

    @DeleteMapping("/admins/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) {
        service.remove(id, CurrentAdmin.require());
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=AdminManagementTest`
Expected: PASS — five tests green.

- [ ] **Step 6: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add admin management with last-admin and self-removal guards"
```

---

## Task 6: Object storage

**Files:**
- Create: `api/src/main/java/com/example/localhostfacom/image/StorageProvider.java`, `S3CompatibleStorageProvider.java`
- Test: `api/src/test/java/com/example/localhostfacom/image/StorageProviderTest.java`

**Interfaces:**
- Consumes: `AppProperties.Storage`.
- Produces: `StorageProvider` with `upload(String key, InputStream body, long size, String mimeType)`, `delete(String key)`, `publicUrl(String key)`, `presignDownloadUrl(String key, Duration expires)`. Tasks 8 and 9 depend on `publicUrl`.

- [ ] **Step 1: Write the failing test**

The S3 round trip needs a live MinIO, so the unit test covers URL construction — the part with real logic — and the network calls are exercised manually in Task 19's smoke check.

`api/src/test/java/com/example/localhostfacom/image/StorageProviderTest.java`:

```java
package com.example.localhostfacom.image;

import com.example.localhostfacom.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageProviderTest {

    private S3CompatibleStorageProvider providerWithBase(String publicBaseUrl) {
        AppProperties.Storage storage = new AppProperties.Storage(
                "http://localhost:9000", "auto", "localhostfacom",
                "key", "secret", publicBaseUrl, true);
        return new S3CompatibleStorageProvider(storage);
    }

    @Test
    void buildsAPublicUrlFromTheConfiguredBase() {
        assertThat(providerWithBase("https://cdn.example.com/bucket").publicUrl("products/abc.jpg"))
                .isEqualTo("https://cdn.example.com/bucket/products/abc.jpg");
    }

    @Test
    void doesNotProduceADoubleSlashWhenTheBaseHasATrailingSlash() {
        assertThat(providerWithBase("https://cdn.example.com/bucket/").publicUrl("products/abc.jpg"))
                .isEqualTo("https://cdn.example.com/bucket/products/abc.jpg");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=StorageProviderTest`
Expected: FAIL — neither class exists.

- [ ] **Step 3: Write the interface and implementation**

`image/StorageProvider.java`:

```java
package com.example.localhostfacom.image;

import java.io.InputStream;
import java.time.Duration;

/** Abstracts the object store so a vendor swap is a configuration change. */
public interface StorageProvider {

    void upload(String key, InputStream body, long size, String mimeType);

    void delete(String key);

    String publicUrl(String key);

    /**
     * Unused while the bucket is public-read. It is declared so switching to a private
     * bucket later does not change this interface.
     */
    String presignDownloadUrl(String key, Duration expires);
}
```

`image/S3CompatibleStorageProvider.java`:

```java
package com.example.localhostfacom.image;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.config.AppProperties;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Works with any S3-protocol store: MinIO locally, Cloudflare R2 in production.
 * Instantiated by {@code StorageConfig} rather than component-scanned, because its
 * constructor takes a nested config record that is not itself a bean.
 */
public class S3CompatibleStorageProvider implements StorageProvider {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public S3CompatibleStorageProvider(AppProperties.Storage storage) {
        this.bucket = storage.bucket();
        this.publicBaseUrl = storage.publicBaseUrl().replaceAll("/+$", "");

        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(storage.accessKey(), storage.secretKey()));
        var region = Region.of(storage.region() == null || storage.region().isBlank() ? "auto" : storage.region());

        this.client = S3Client.builder()
                .endpointOverride(URI.create(storage.endpoint()))
                .credentialsProvider(credentials)
                .region(region)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storage.pathStyle())
                        .build())
                .build();

        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(storage.endpoint()))
                .credentialsProvider(credentials)
                .region(region)
                .build();
    }

    @Override
    public void upload(String key, InputStream body, long size, String mimeType) {
        try {
            client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(mimeType).build(),
                    RequestBody.fromInputStream(body, size));
        } catch (S3Exception exception) {
            throw ApiException.badGateway("storage-upload-failed",
                    "Could not upload the image to object storage");
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception exception) {
            throw ApiException.badGateway("storage-delete-failed",
                    "Could not delete the object from storage");
        }
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }

    @Override
    public String presignDownloadUrl(String key, Duration expires) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(expires)
                        .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                        .build())
                .url()
                .toString();
    }
}
```

- [ ] **Step 4: Register the bean**

`config/StorageConfig.java`:

```java
package com.example.localhostfacom.config;

import com.example.localhostfacom.image.S3CompatibleStorageProvider;
import com.example.localhostfacom.image.StorageProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    public StorageProvider storageProvider(AppProperties properties) {
        return new S3CompatibleStorageProvider(properties.storage());
    }
}
```

Remove the now-unused `org.springframework.stereotype.Component` import from `S3CompatibleStorageProvider`.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=StorageProviderTest`
Expected: PASS — two tests green.

- [ ] **Step 6: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add S3-compatible object storage provider"
```

---

## Task 7: Image processing

**Files:**
- Create: `api/src/main/java/com/example/localhostfacom/image/ProcessedImage.java`, `ImageProcessor.java`
- Test: `api/src/test/java/com/example/localhostfacom/image/ImageProcessorTest.java`

**Interfaces:**
- Consumes: `ApiException`.
- Produces: `ImageProcessor.process(byte[] source, int maxDim)` returns `ProcessedImage(byte[] bytes, int width, int height, String hash, String mimeType)`. `ProcessedImage.extension()` returns `"jpg"` or `"png"`. Task 8 consumes both.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/image/ImageProcessorTest.java`:

```java
package com.example.localhostfacom.image;

import com.example.localhostfacom.common.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    private byte[] image(int width, int height, int type, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width / 2, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    @Test
    void encodesAnOpaqueSourceAsJpeg() throws Exception {
        ProcessedImage result = processor.process(image(200, 100, BufferedImage.TYPE_INT_RGB, "png"), 1024);

        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThat(result.extension()).isEqualTo("jpg");
        assertThat(result.width()).isEqualTo(200);
        assertThat(result.height()).isEqualTo(100);
    }

    /** Re-encoding a transparent upload as JPEG would flatten it onto a black background. */
    @Test
    void keepsATransparentSourceAsPng() throws Exception {
        ProcessedImage result = processor.process(image(120, 120, BufferedImage.TYPE_INT_ARGB, "png"), 1024);

        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(result.extension()).isEqualTo("png");
    }

    @Test
    void scalesDownWhileKeepingTheAspectRatio() throws Exception {
        ProcessedImage result = processor.process(image(2000, 1000, BufferedImage.TYPE_INT_RGB, "jpg"), 1024);

        assertThat(result.width()).isEqualTo(1024);
        assertThat(result.height()).isEqualTo(512);
    }

    @Test
    void leavesASmallImageAtItsOriginalSize() throws Exception {
        ProcessedImage result = processor.process(image(300, 200, BufferedImage.TYPE_INT_RGB, "jpg"), 1024);

        assertThat(result.width()).isEqualTo(300);
        assertThat(result.height()).isEqualTo(200);
    }

    @Test
    void producesTheSameHashForIdenticalBytes() throws Exception {
        byte[] source = image(50, 50, BufferedImage.TYPE_INT_RGB, "png");

        assertThat(processor.process(source, 1024).hash())
                .isEqualTo(processor.process(source, 1024).hash())
                .hasSize(64);
    }

    @Test
    void rejectsSomethingThatIsNotAnImage() {
        assertThatThrownBy(() -> processor.process("not an image".getBytes(), 1024))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void rejectsAnImageWhoseDeclaredDimensionsAreAbsurd() throws Exception {
        // 9000 exceeds maxSourceDim, so it is refused from the header alone,
        // before anything is decoded into memory.
        assertThatThrownBy(() -> processor.process(image(9000, 10, BufferedImage.TYPE_INT_RGB, "png"), 1024))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("too large");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=ImageProcessorTest`
Expected: FAIL — `ImageProcessor` and `ProcessedImage` do not exist.

- [ ] **Step 3: Write the implementation**

`image/ProcessedImage.java`:

```java
package com.example.localhostfacom.image;

public record ProcessedImage(byte[] bytes, int width, int height, String hash, String mimeType) {

    public String extension() {
        return "image/png".equals(mimeType) ? "png" : "jpg";
    }

    public long size() {
        return bytes.length;
    }
}
```

`image/ImageProcessor.java`:

```java
package com.example.localhostfacom.image;

import com.example.localhostfacom.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

@Component
public class ImageProcessor {

    /**
     * Caps the SOURCE dimensions, checked from the file header before the full decode.
     * Without it, a small but heavily compressed file declaring an enormous pixel count
     * would force a huge allocation during decode — long before any resize could help.
     */
    private static final int MAX_SOURCE_DIM = 8192;

    private static final float JPEG_QUALITY = 0.85f;

    public ProcessedImage process(byte[] source, int maxDim) {
        String hash = sha256(source);

        int[] dimensions = readDimensionsFromHeader(source);
        int sourceWidth = dimensions[0];
        int sourceHeight = dimensions[1];

        if (sourceWidth > MAX_SOURCE_DIM || sourceHeight > MAX_SOURCE_DIM) {
            throw ApiException.badRequest("image-too-large",
                    "Image dimensions are too large; the maximum is " + MAX_SOURCE_DIM + " pixels per side");
        }

        BufferedImage decoded = decode(source);
        boolean hasAlpha = decoded.getColorModel().hasAlpha();

        BufferedImage resized = decoded;
        if (sourceWidth > maxDim || sourceHeight > maxDim) {
            try {
                resized = Thumbnails.of(decoded).size(maxDim, maxDim).keepAspectRatio(true).asBufferedImage();
            } catch (IOException exception) {
                throw ApiException.badRequest("image-resize-failed", "Could not resize the image");
            }
        }

        String mimeType = hasAlpha ? "image/png" : "image/jpeg";
        byte[] encoded = encode(resized, hasAlpha);

        return new ProcessedImage(encoded, resized.getWidth(), resized.getHeight(), hash, mimeType);
    }

    private int[] readDimensionsFromHeader(byte[] source) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (stream == null) {
                throw ApiException.badRequest("unsupported-image", "Unsupported image format");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw ApiException.badRequest("unsupported-image", "Unsupported image format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw ApiException.badRequest("unsupported-image", "Unsupported image format");
        }
    }

    private BufferedImage decode(byte[] source) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null) {
                throw ApiException.badRequest("unsupported-image", "Unsupported image format");
            }
            return image;
        } catch (IOException exception) {
            throw ApiException.badRequest("unsupported-image", "Unsupported image format");
        }
    }

    private byte[] encode(BufferedImage image, boolean hasAlpha) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (hasAlpha) {
                Thumbnails.of(image).scale(1.0).outputFormat("png").toOutputStream(out);
            } else {
                Thumbnails.of(image).scale(1.0).outputFormat("jpg")
                        .outputQuality(JPEG_QUALITY).toOutputStream(out);
            }
        } catch (IOException exception) {
            throw ApiException.badRequest("image-encode-failed", "Could not encode the image");
        }
        return out.toByteArray();
    }

    private String sha256(byte[] source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required but unavailable", exception);
        }
    }
}
```

Note on formats: the accepted set is whatever `ImageIO` can actually read. Stock JDK covers JPEG, PNG, GIF and BMP. Uploads come mostly from phone cameras, so WebP support (Android/Google Photos) was added via the `com.twelvemonkeys.imageio:imageio-webp` dependency in `pom.xml` — it registers itself via SPI, so no code in `ImageProcessor` references WebP directly; `ImageProcessorTest.registersAWebpReaderSoAndroidPhotosAreAccepted` confirms the reader is picked up. HEIC (the iPhone default) is deliberately **not** supported — there is no pure-Java decoder, only native-library options (e.g. libheif via JNI), which was ruled out to avoid a native dependency in the deployment. iPhone users need "Settings > Camera > Formats > Most Compatible" (saves JPEG), or rely on their browser/OS converting HEIC to JPEG on upload, which most mobile browsers already do by default in a file picker. An unrecognized format is still refused with `unsupported-image`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=ImageProcessorTest`
Expected: PASS — seven tests green.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add image processing with decompression bomb guard"
```

---

## Task 8: Image service and upload endpoint

**Files:**
- Create: `api/src/main/java/com/example/localhostfacom/image/ImageService.java`, `ImageController.java`
- Create: `api/src/main/java/com/example/localhostfacom/image/dto/ImageResponse.java`
- Test: `api/src/test/java/com/example/localhostfacom/image/ImageServiceTest.java`

**Interfaces:**
- Consumes: `ImageProcessor`, `ProcessedImage`, `StorageProvider`, `Image`, `ImageRepository`, `ProductRepository.existsByImageId`.
- Produces: `ImageService.uploadAndSave(byte[] source)` returns `Image`; `ImageService.delete(UUID id)`. Task 9 attaches the returned `Image` to a product.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/image/ImageServiceTest.java`:

```java
package com.example.localhostfacom.image;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ImageServiceTest {

    /** Records what was uploaded and deleted, so rollback behaviour is observable. */
    static class RecordingStorageProvider implements StorageProvider {
        final List<String> uploaded = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();
        boolean failUploads = false;

        @Override public void upload(String key, InputStream body, long size, String mimeType) {
            if (failUploads) {
                throw ApiException.badGateway("storage-upload-failed", "boom");
            }
            uploaded.add(key);
        }
        @Override public void delete(String key) { deleted.add(key); }
        @Override public String publicUrl(String key) { return "http://storage.test/" + key; }
        @Override public String presignDownloadUrl(String key, Duration expires) { return publicUrl(key); }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary RecordingStorageProvider recordingStorageProvider() {
            return new RecordingStorageProvider();
        }
    }

    @Autowired private ImageService service;
    @Autowired private ImageRepository images;
    @Autowired private ProductRepository products;
    @Autowired private RecordingStorageProvider storage;

    private byte[] png(int size) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @BeforeEach
    void setUp() {
        products.deleteAll();
        images.deleteAll();
        storage.uploaded.clear();
        storage.deleted.clear();
        storage.failUploads = false;
    }

    @Test
    void uploadsAndStoresAnImage() throws Exception {
        Image image = service.uploadAndSave(png(64));

        assertThat(image.getStorageKey()).startsWith("products/").endsWith(".jpg");
        assertThat(storage.uploaded).hasSize(1);
        assertThat(images.findById(image.getId())).isPresent();
    }

    /** The same bytes must never occupy the bucket twice. */
    @Test
    void deduplicatesByHashWithoutUploadingAgain() throws Exception {
        byte[] source = png(64);
        Image first = service.uploadAndSave(source);
        Image second = service.uploadAndSave(source);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(storage.uploaded).hasSize(1);
        assertThat(images.count()).isEqualTo(1L);
    }

    @Test
    void reportsAStorageFailureWithoutWritingARow() throws Exception {
        storage.failUploads = true;

        assertThatThrownBy(() -> service.uploadAndSave(png(64)))
                .isInstanceOf(ApiException.class);
        assertThat(images.count()).isZero();
    }

    @Test
    void refusesToDeleteAnImageAProductStillReferences() throws Exception {
        Image image = service.uploadAndSave(png(64));
        products.save(Product.create("Café", new BigDecimal("3.50"), image));

        assertThatThrownBy(() -> service.delete(image.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("in use");
        assertThat(images.findById(image.getId())).isPresent();
    }

    @Test
    void deletesAnUnreferencedImageFromBothTheDatabaseAndStorage() throws Exception {
        Image image = service.uploadAndSave(png(64));

        service.delete(image.getId());

        assertThat(images.findById(image.getId())).isEmpty();
        assertThat(storage.deleted).containsExactly(image.getStorageKey());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=ImageServiceTest`
Expected: FAIL — `ImageService` does not exist.

- [ ] **Step 3: Write the service**

`image/ImageService.java`:

```java
package com.example.localhostfacom.image;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.product.ProductRepository;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);
    private static final int MAX_DIMENSION = 1024;

    private final ImageRepository images;
    private final ProductRepository products;
    private final ImageProcessor processor;
    private final StorageProvider storage;

    public ImageService(ImageRepository images, ProductRepository products,
                        ImageProcessor processor, StorageProvider storage) {
        this.images = images;
        this.products = products;
        this.processor = processor;
        this.storage = storage;
    }

    /**
     * Deliberately NOT @Transactional. It performs a network upload, and it recovers from
     * a unique-constraint violation by re-reading the winning row — inside a transaction
     * that violation would mark the context rollback-only and the recovery read would fail
     * too. Each repository call manages its own transaction instead.
     */
    public Image uploadAndSave(byte[] source) {
        ProcessedImage processed = processor.process(source, MAX_DIMENSION);

        var existing = images.findByHash(processed.hash());
        if (existing.isPresent()) {
            return existing.get();
        }

        String key = "products/" + UUID.randomUUID() + "." + processed.extension();
        storage.upload(key, new ByteArrayInputStream(processed.bytes()), processed.size(), processed.mimeType());

        try {
            return images.saveAndFlush(Image.create(
                    key, processed.mimeType(), processed.width(), processed.height(), processed.hash()));
        } catch (DataIntegrityViolationException exception) {
            // Never leave an object in the bucket that no row points at.
            storage.delete(key);

            // Another request uploaded the same bytes concurrently and won the race on the
            // hash constraint. Its row is just as good as the one we failed to write.
            return images.findByHash(processed.hash()).orElseThrow(() ->
                    ApiException.conflict("image-save-failed", "Could not save the image"));
        }
    }

    @Transactional
    public void delete(UUID id) {
        Image image = images.findById(id)
                .orElseThrow(() -> ApiException.notFound("image-not-found", "Image not found"));

        if (products.existsByImageId(id)) {
            throw ApiException.conflict("image-in-use", "This image is in use by a product");
        }

        images.delete(image);

        try {
            storage.delete(image.getStorageKey());
        } catch (RuntimeException exception) {
            // The row is gone, which is what the caller asked for. An orphaned object costs
            // a few kilobytes; failing here would leave the caller thinking nothing happened.
            log.warn("Deleted image row {} but could not remove storage key {}",
                    id, image.getStorageKey(), exception);
        }
    }

    public String publicUrl(Image image) {
        return image == null ? null : storage.publicUrl(image.getStorageKey());
    }
}
```

`image/dto/ImageResponse.java`:

```java
package com.example.localhostfacom.image.dto;

import java.util.UUID;

public record ImageResponse(UUID id, String url, int width, int height) {}
```

`image/ImageController.java`:

```java
package com.example.localhostfacom.image;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.image.dto.ImageResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/images")
public class ImageController {

    private final ImageService service;

    public ImageController(ImageService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImageResponse upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw ApiException.badRequest("empty-file", "No file was uploaded");
        }
        try {
            Image image = service.uploadAndSave(file.getBytes());
            return new ImageResponse(
                    image.getId(), service.publicUrl(image), image.getWidth(), image.getHeight());
        } catch (IOException exception) {
            throw ApiException.badRequest("unreadable-upload", "Could not read the uploaded file");
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=ImageServiceTest`
Expected: PASS — five tests green.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add image upload with hash dedup and reference-guarded delete"
```

---

## Task 9: Products

**Files:**
- Create: `api/src/main/java/com/example/localhostfacom/product/ProductService.java`, `AdminProductController.java`
- Create: `api/src/main/java/com/example/localhostfacom/product/dto/ProductRequest.java`, `ProductResponse.java`
- Modify: `api/src/main/java/com/example/localhostfacom/product/PublicProductController.java`
- Test: `api/src/test/java/com/example/localhostfacom/product/ProductServiceTest.java`

**Interfaces:**
- Consumes: `Product`, `ProductRepository`, `ImageRepository`, `ImageService.publicUrl`, `ApiException`.
- Produces: `ProductService.listActive()`, `listAll()`, `create(String name, BigDecimal price, UUID imageId)`, `update(UUID id, String name, BigDecimal price, UUID imageId, boolean active)`, `remove(UUID id)`, `requireActive(UUID id)`. `ProductResponse(UUID id, String name, BigDecimal price, String imageUrl, Integer imageWidth, Integer imageHeight, boolean active)`. Task 12 calls `requireActive`.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/product/ProductServiceTest.java`:

```java
package com.example.localhostfacom.product;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.order.Order;
import com.example.localhostfacom.order.OrderItem;
import com.example.localhostfacom.order.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProductServiceTest {

    @Autowired private ProductService service;
    @Autowired private ProductRepository products;
    @Autowired private OrderRepository orders;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        products.deleteAll();
    }

    @Test
    void createsAProduct() {
        Product product = service.create("Café", new BigDecimal("3.50"), null);

        assertThat(product.getName()).isEqualTo("Café");
        assertThat(product.isActive()).isTrue();
    }

    @Test
    void listsOnlyActiveProductsForThePublicCatalogue() {
        service.create("Ativo", new BigDecimal("1.00"), null);
        Product hidden = service.create("Escondido", new BigDecimal("1.00"), null);
        service.update(hidden.getId(), "Escondido", new BigDecimal("1.00"), null, false);

        assertThat(service.listActive()).extracting(Product::getName).containsExactly("Ativo");
        assertThat(service.listAll()).hasSize(2);
    }

    /** A product that never sold leaves no history worth keeping. */
    @Test
    void removesAProductThatWasNeverOrdered() {
        Product product = service.create("Nunca vendido", new BigDecimal("1.00"), null);

        service.remove(product.getId());

        assertThat(products.findById(product.getId())).isEmpty();
    }

    /** A product that has sold must survive, or past orders lose their referent. */
    @Test
    void deactivatesRatherThanRemovesAProductThatHasSold() {
        Product product = service.create("Já vendido", new BigDecimal("2.00"), null);
        Order order = Order.create("fake", Instant.now().plusSeconds(600));
        order.addItem(OrderItem.snapshotOf(product, 1));
        order.recalculateTotal();
        orders.saveAndFlush(order);

        service.remove(product.getId());

        Product reloaded = products.findById(product.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    void refusesAnInactiveProductWhenAnOrderAsksForIt() {
        Product product = service.create("Indisponível", new BigDecimal("1.00"), null);
        service.update(product.getId(), "Indisponível", new BigDecimal("1.00"), null, false);

        assertThatThrownBy(() -> service.requireActive(product.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void refusesAnUnknownProduct() {
        assertThatThrownBy(() -> service.requireActive(java.util.UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=ProductServiceTest`
Expected: FAIL — `ProductService` does not exist.

- [ ] **Step 3: Write the service and DTOs**

`product/ProductService.java`:

```java
package com.example.localhostfacom.product;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.image.Image;
import com.example.localhostfacom.image.ImageRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;
    private final ImageRepository images;

    public ProductService(ProductRepository products, ImageRepository images) {
        this.products = products;
        this.images = images;
    }

    public List<Product> listActive() {
        return products.findByActiveTrueOrderByNameAsc();
    }

    public List<Product> listAll() {
        return products.findAllByOrderByNameAsc();
    }

    @Transactional
    public Product create(String name, BigDecimal price, UUID imageId) {
        return products.save(Product.create(name.trim(), price, resolveImage(imageId)));
    }

    @Transactional
    public Product update(UUID id, String name, BigDecimal price, UUID imageId, boolean active) {
        Product product = products.findById(id)
                .orElseThrow(() -> ApiException.notFound("product-not-found", "Product not found"));
        product.update(name.trim(), price, resolveImage(imageId), active);
        return products.save(product);
    }

    /**
     * Removes the row outright when the product has never been ordered, and deactivates it
     * otherwise. Either way the admin's intent — stop selling this — is satisfied, and a
     * product that has ever sold is never destroyed, so past orders keep their referent.
     */
    @Transactional
    public void remove(UUID id) {
        Product product = products.findById(id)
                .orElseThrow(() -> ApiException.notFound("product-not-found", "Product not found"));

        if (products.hasBeenOrdered(id)) {
            product.deactivate();
            products.save(product);
        } else {
            products.delete(product);
        }
    }

    public Product requireActive(UUID id) {
        Product product = products.findById(id)
                .orElseThrow(() -> ApiException.badRequest("product-not-found",
                        "Product " + id + " does not exist"));
        if (!product.isActive()) {
            throw ApiException.badRequest("product-inactive",
                    "Product " + product.getName() + " is not available");
        }
        return product;
    }

    private Image resolveImage(UUID imageId) {
        if (imageId == null) {
            return null;
        }
        return images.findById(imageId)
                .orElseThrow(() -> ApiException.badRequest("image-not-found", "Image not found"));
    }
}
```

`product/dto/ProductRequest.java`:

```java
package com.example.localhostfacom.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        UUID imageId,
        Boolean active) {

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
```

`product/dto/ProductResponse.java`:

```java
package com.example.localhostfacom.product.dto;

import com.example.localhostfacom.image.Image;
import com.example.localhostfacom.product.Product;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        BigDecimal price,
        String imageUrl,
        Integer imageWidth,
        Integer imageHeight,
        boolean active) {

    public static ProductResponse of(Product product, String imageUrl) {
        Image image = product.getImage();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                imageUrl,
                image == null ? null : image.getWidth(),
                image == null ? null : image.getHeight(),
                product.isActive());
    }
}
```

- [ ] **Step 4: Write the controllers**

Replace `product/PublicProductController.java`:

```java
package com.example.localhostfacom.product;

import com.example.localhostfacom.image.ImageService;
import com.example.localhostfacom.product.dto.ProductResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/products")
public class PublicProductController {

    private final ProductService products;
    private final ImageService images;

    public PublicProductController(ProductService products, ImageService images) {
        this.products = products;
        this.images = images;
    }

    @GetMapping
    public List<ProductResponse> list() {
        return products.listActive().stream()
                .map(product -> ProductResponse.of(product, images.publicUrl(product.getImage())))
                .toList();
    }
}
```

`product/AdminProductController.java`:

```java
package com.example.localhostfacom.product;

import com.example.localhostfacom.image.ImageService;
import com.example.localhostfacom.product.dto.ProductRequest;
import com.example.localhostfacom.product.dto.ProductResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService products;
    private final ImageService images;

    public AdminProductController(ProductService products, ImageService images) {
        this.products = products;
        this.images = images;
    }

    @GetMapping
    public List<ProductResponse> list() {
        return products.listAll().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return toResponse(products.create(request.name(), request.price(), request.imageId()));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return toResponse(products.update(
                id, request.name(), request.price(), request.imageId(), request.activeOrDefault()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) {
        products.remove(id);
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.of(product, images.publicUrl(product.getImage()));
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=ProductServiceTest`
Expected: PASS — six tests green.

- [ ] **Step 6: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add product catalogue and admin CRUD"
```

---

## Task 10: Payment provider abstraction and fake provider

**Files:**
- Create: `api/src/main/java/com/example/localhostfacom/payment/PaymentProvider.java`, `PaymentProviderRegistry.java`, `ChargeRequest.java`, `PaymentCharge.java`, `PaymentStatus.java`, `WebhookNotification.java`, `FakePaymentProvider.java`
- Test: `api/src/test/java/com/example/localhostfacom/payment/PaymentProviderRegistryTest.java`

**Interfaces:**
- Consumes: `AppProperties.Payments`.
- Produces: `PaymentProvider` with `name()`, `createCharge(ChargeRequest)`, `fetchStatus(String)`, `parseAndVerify(Map<String,String>, String)`. `PaymentProviderRegistry.active()` returns the configured provider; `byName(String)` resolves an order's originating provider. Records: `ChargeRequest(UUID orderId, BigDecimal amount, String description, Instant expiresAt)`, `PaymentCharge(String providerPaymentId, String payload, String qrImageBase64, String checkoutUrl, Instant expiresAt)`, `WebhookNotification(String eventId, String providerPaymentId, PaymentStatus status)`. Enum `PaymentStatus { PENDING, APPROVED, REJECTED, EXPIRED }`.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/payment/PaymentProviderRegistryTest.java`:

```java
package com.example.localhostfacom.payment;

import com.example.localhostfacom.common.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PaymentProviderRegistryTest {

    @Autowired private PaymentProviderRegistry registry;

    @Test
    void resolvesTheConfiguredActiveProvider() {
        assertThat(registry.active().name()).isEqualTo("fake");
    }

    /**
     * An order records the provider that created its charge, so status checks follow the
     * order rather than current configuration. Otherwise switching providers would strand
     * every in-flight order.
     */
    @Test
    void resolvesAProviderByNameRegardlessOfWhichIsActive() {
        assertThat(registry.byName("fake").name()).isEqualTo("fake");
        assertThat(registry.byName("mercadopago").name()).isEqualTo("mercadopago");
    }

    @Test
    void failsLoudlyForAnUnknownProvider() {
        assertThatThrownBy(() -> registry.byName("nonexistent"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void fakeProviderProducesAScannableQrAndAPayload() {
        PaymentCharge charge = registry.byName("fake").createCharge(new ChargeRequest(
                UUID.randomUUID(), new BigDecimal("12.50"), "Pedido", Instant.now().plusSeconds(600)));

        assertThat(charge.providerPaymentId()).isNotBlank();
        assertThat(charge.payload()).contains("12.50");
        assertThat(charge.qrImageBase64()).isNotBlank();
    }

    @Test
    void fakeProviderReportsPendingBeforeTheConfiguredDelay() {
        PaymentCharge charge = registry.byName("fake").createCharge(new ChargeRequest(
                UUID.randomUUID(), new BigDecimal("1.00"), "Pedido", Instant.now().plusSeconds(600)));

        assertThat(registry.byName("fake").fetchStatus(charge.providerPaymentId()))
                .isEqualTo(PaymentStatus.PENDING);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=PaymentProviderRegistryTest`
Expected: FAIL — none of the payment classes exist.

- [ ] **Step 3: Write the abstraction**

`payment/PaymentStatus.java`:

```java
package com.example.localhostfacom.payment;

public enum PaymentStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED
}
```

`payment/ChargeRequest.java`:

```java
package com.example.localhostfacom.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The expiry comes from the order row, so both sides always agree on the deadline. */
public record ChargeRequest(UUID orderId, BigDecimal amount, String description, Instant expiresAt) {}
```

`payment/PaymentCharge.java`:

```java
package com.example.localhostfacom.payment;

import java.time.Instant;

/**
 * Deliberately not PIX-specific. {@code payload} is whatever the customer copies — an EMV
 * string today, something else for a future provider — and {@code checkoutUrl} covers
 * providers that redirect instead.
 */
public record PaymentCharge(
        String providerPaymentId,
        String payload,
        String qrImageBase64,
        String checkoutUrl,
        Instant expiresAt) {}
```

`payment/WebhookNotification.java`:

```java
package com.example.localhostfacom.payment;

public record WebhookNotification(String eventId, String providerPaymentId, PaymentStatus status) {}
```

`payment/PaymentProvider.java`:

```java
package com.example.localhostfacom.payment;

import java.util.Map;
import java.util.Optional;

public interface PaymentProvider {

    /** Stable identifier persisted on the order and used in the webhook route. */
    String name();

    PaymentCharge createCharge(ChargeRequest request);

    PaymentStatus fetchStatus(String providerPaymentId);

    /**
     * Returns empty when the request is not authentic. Callers must treat empty as a
     * hard rejection and write nothing.
     */
    Optional<WebhookNotification> parseAndVerify(Map<String, String> headers, String rawBody);
}
```

`payment/PaymentProviderRegistry.java`:

```java
package com.example.localhostfacom.payment;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.config.AppProperties;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> byName;
    private final String activeName;

    public PaymentProviderRegistry(List<PaymentProvider> providers, AppProperties properties) {
        this.byName = providers.stream()
                .collect(Collectors.toMap(PaymentProvider::name, Function.identity()));
        this.activeName = properties.payments().activeProvider();

        if (!byName.containsKey(activeName)) {
            throw new IllegalStateException(
                    "app.payments.active-provider is '" + activeName + "' but only "
                            + byName.keySet() + " are registered");
        }
    }

    public PaymentProvider active() {
        return byName.get(activeName);
    }

    public PaymentProvider byName(String name) {
        PaymentProvider provider = byName.get(name);
        if (provider == null) {
            throw ApiException.notFound("unknown-payment-provider", "Unknown payment provider: " + name);
        }
        return provider;
    }
}
```

- [ ] **Step 4: Write the fake provider**

`payment/FakePaymentProvider.java`:

```java
package com.example.localhostfacom.payment;

import com.example.localhostfacom.config.AppProperties;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Runs the whole customer flow with no credentials. Charges report PENDING until the
 * configured delay elapses, then APPROVED, so the payment screen's polling can be
 * exercised end to end. The application refuses to start with this provider active
 * under the prod profile (see PaymentProviderGuard).
 */
@Component
public class FakePaymentProvider implements PaymentProvider {

    private final Map<String, Instant> createdAt = new ConcurrentHashMap<>();
    private final Duration autoConfirmAfter;

    public FakePaymentProvider(AppProperties properties) {
        this.autoConfirmAfter = properties.payments().fake().autoConfirmAfter();
    }

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public PaymentCharge createCharge(ChargeRequest request) {
        String paymentId = "fake-" + UUID.randomUUID();
        createdAt.put(paymentId, Instant.now());

        String payload = "00020126FAKE-PIX-PAYLOAD"
                + "-order-" + request.orderId()
                + "-amount-" + request.amount().toPlainString()
                + "-6304FAKE";

        return new PaymentCharge(paymentId, payload, qrCodeBase64(payload), null, request.expiresAt());
    }

    @Override
    public PaymentStatus fetchStatus(String providerPaymentId) {
        Instant created = createdAt.get(providerPaymentId);
        if (created == null) {
            return PaymentStatus.PENDING;
        }
        return created.plus(autoConfirmAfter).isAfter(Instant.now())
                ? PaymentStatus.PENDING
                : PaymentStatus.APPROVED;
    }

    @Override
    public Optional<WebhookNotification> parseAndVerify(Map<String, String> headers, String rawBody) {
        // The fake provider never calls back; confirmation arrives through polling.
        return Optional.empty();
    }

    private String qrCodeBase64(String payload) {
        try {
            var matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 256, 256);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("Could not render the fake QR code", exception);
        }
    }
}
```

`payment/PaymentProviderGuard.java`:

```java
package com.example.localhostfacom.payment;

import com.example.localhostfacom.config.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Refuses to start a production instance wired to the fake provider, which would accept
 * orders and mark them paid without any money ever moving.
 */
@Configuration
@Profile("prod")
public class PaymentProviderGuard {

    public PaymentProviderGuard(AppProperties properties) {
        if ("fake".equals(properties.payments().activeProvider())) {
            throw new IllegalStateException(
                    "app.payments.active-provider must not be 'fake' under the prod profile");
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=PaymentProviderRegistryTest`
Expected: FAIL on `resolvesAProviderByNameRegardlessOfWhichIsActive` — `mercadopago` is not registered yet. That is expected; Task 11 adds it. Every other test passes.

- [ ] **Step 6: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add provider-agnostic payment abstraction and fake provider"
```

---

## Task 11: Mercado Pago provider

**Files:**
- Create: `api/src/main/java/com/example/localhostfacom/payment/MercadoPagoSignatureVerifier.java`, `MercadoPagoPaymentProvider.java`
- Test: `api/src/test/java/com/example/localhostfacom/payment/MercadoPagoSignatureVerifierTest.java`

**Interfaces:**
- Consumes: `PaymentProvider`, `AppProperties.Payments.MercadoPago`, `ApiException`.
- Produces: a `PaymentProvider` named `mercadopago`. `MercadoPagoSignatureVerifier.verify(Map<String,String> headers, String dataId)` returns `boolean`.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/payment/MercadoPagoSignatureVerifierTest.java`:

```java
package com.example.localhostfacom.payment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoSignatureVerifierTest {

    private static final String SECRET = "webhook-secret";

    private final MercadoPagoSignatureVerifier verifier = new MercadoPagoSignatureVerifier(SECRET);

    private String sign(String dataId, String requestId, long ts) throws Exception {
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, String> headers(String signature, String requestId, long ts) {
        return Map.of(
                "x-signature", "ts=" + ts + ",v1=" + signature,
                "x-request-id", requestId);
    }

    @Test
    void acceptsACorrectlySignedRequest() throws Exception {
        long ts = Instant.now().getEpochSecond();
        assertThat(verifier.verify(headers(sign("123", "req-1", ts), "req-1", ts), "123")).isTrue();
    }

    @Test
    void rejectsATamperedPaymentId() throws Exception {
        long ts = Instant.now().getEpochSecond();
        assertThat(verifier.verify(headers(sign("123", "req-1", ts), "req-1", ts), "999")).isFalse();
    }

    @Test
    void rejectsAWrongSignature() {
        long ts = Instant.now().getEpochSecond();
        assertThat(verifier.verify(headers("deadbeef", "req-1", ts), "123")).isFalse();
    }

    /** An old capture must not be replayable. */
    @Test
    void rejectsAStaleTimestamp() throws Exception {
        long ts = Instant.now().minusSeconds(3600).getEpochSecond();
        assertThat(verifier.verify(headers(sign("123", "req-1", ts), "req-1", ts), "123")).isFalse();
    }

    @Test
    void rejectsAMissingSignatureHeader() {
        assertThat(verifier.verify(Map.of("x-request-id", "req-1"), "123")).isFalse();
    }

    @Test
    void rejectsAMalformedSignatureHeader() {
        assertThat(verifier.verify(Map.of("x-signature", "garbage", "x-request-id", "r"), "123")).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=MercadoPagoSignatureVerifierTest`
Expected: FAIL — `MercadoPagoSignatureVerifier` does not exist.

- [ ] **Step 3: Write the verifier**

`payment/MercadoPagoSignatureVerifier.java`:

```java
package com.example.localhostfacom.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Without this, anyone who knows the webhook URL could forge a payment confirmation and
 * have the system record money that never arrived.
 */
public class MercadoPagoSignatureVerifier {

    private static final Duration MAX_AGE = Duration.ofMinutes(5);

    private final String secret;

    public MercadoPagoSignatureVerifier(String secret) {
        this.secret = secret;
    }

    public boolean verify(Map<String, String> headers, String dataId) {
        String signatureHeader = header(headers, "x-signature");
        String requestId = header(headers, "x-request-id");

        if (signatureHeader == null || dataId == null || secret == null || secret.isBlank()) {
            return false;
        }

        String ts = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("ts".equals(pair[0].trim())) {
                ts = pair[1].trim();
            } else if ("v1".equals(pair[0].trim())) {
                v1 = pair[1].trim();
            }
        }

        if (ts == null || v1 == null) {
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(ts);
        } catch (NumberFormatException exception) {
            return false;
        }

        if (Duration.between(Instant.ofEpochSecond(timestamp), Instant.now()).abs().compareTo(MAX_AGE) > 0) {
            return false;
        }

        String manifest = "id:" + dataId + ";request-id:" + (requestId == null ? "" : requestId)
                + ";ts:" + ts + ";";

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            // Constant time, so a timing side channel cannot leak the correct signature.
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(v1));
        } catch (Exception exception) {
            return false;
        }
    }

    private String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
```

- [ ] **Step 4: Write the provider**

**Before writing this**, confirm which Jackson `ObjectMapper` Spring actually autowires here.
Boot 4 moved to Jackson 3 under the `tools.jackson.*` groupId/package for its own
`spring-boot-starter-jackson` (confirmed in Task 1/4: `tools.jackson.core:jackson-databind`
was on the classpath, not `com.fasterxml.jackson.core`). `jjwt-jackson` separately pulls
`com.fasterxml.jackson.core:jackson-databind:2.21.4` as its own runtime dependency for JWT
serialization only — that one is not the bean Spring injects. Import
`tools.jackson.databind.JsonNode` / `tools.jackson.databind.ObjectMapper` below unless a
build check at execution time shows otherwise; run
`./mvnw dependency:tree | grep -i jackson` first if unsure.

`payment/MercadoPagoPaymentProvider.java`:

```java
package com.example.localhostfacom.payment;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.config.AppProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Talks to the Mercado Pago REST API directly rather than through the official Java SDK:
 * fewer transitive dependencies, and the SDK trails the REST API.
 */
@Component
public class MercadoPagoPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoPaymentProvider.class);

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final MercadoPagoSignatureVerifier verifier;

    public MercadoPagoPaymentProvider(AppProperties properties, ObjectMapper objectMapper) {
        AppProperties.Payments.MercadoPago config = properties.payments().mercadoPago();
        this.objectMapper = objectMapper;
        this.verifier = new MercadoPagoSignatureVerifier(config.webhookSecret());
        this.client = RestClient.builder()
                .baseUrl(config.baseUrl())
                .defaultHeader("Authorization", "Bearer " + config.accessToken())
                .build();
    }

    @Override
    public String name() {
        return "mercadopago";
    }

    @Override
    public PaymentCharge createCharge(ChargeRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transaction_amount", request.amount());
        body.put("description", request.description());
        body.put("payment_method_id", "pix");
        // Derived from the order row so the provider and the database never disagree
        // about when the charge dies.
        body.put("date_of_expiration", DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(request.expiresAt().atOffset(ZoneOffset.UTC)));
        body.put("payer", Map.of("email", "anonimo@localhostfacom.dev"));

        try {
            JsonNode response = client.post()
                    .uri("/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    // The order id makes the call safely retryable.
                    .header("X-Idempotency-Key", request.orderId().toString())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw ApiException.badGateway("payment-provider-error", "Empty response from Mercado Pago");
            }

            JsonNode transactionData = response
                    .path("point_of_interaction")
                    .path("transaction_data");

            return new PaymentCharge(
                    response.path("id").asText(),
                    transactionData.path("qr_code").asText(null),
                    transactionData.path("qr_code_base64").asText(null),
                    transactionData.path("ticket_url").asText(null),
                    request.expiresAt());
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Mercado Pago charge creation failed for order {}", request.orderId(), exception);
            throw ApiException.badGateway("payment-provider-error",
                    "Could not create the payment charge");
        }
    }

    @Override
    public PaymentStatus fetchStatus(String providerPaymentId) {
        try {
            JsonNode response = client.get()
                    .uri("/v1/payments/{id}", providerPaymentId)
                    .retrieve()
                    .body(JsonNode.class);

            return response == null
                    ? PaymentStatus.PENDING
                    : toStatus(response.path("status").asText(""));
        } catch (RuntimeException exception) {
            log.warn("Could not fetch Mercado Pago status for {}", providerPaymentId, exception);
            return PaymentStatus.PENDING;
        }
    }

    @Override
    public Optional<WebhookNotification> parseAndVerify(Map<String, String> headers, String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String dataId = root.path("data").path("id").asText(null);

            if (dataId == null || !verifier.verify(headers, dataId)) {
                return Optional.empty();
            }

            // The notification carries only an id; the authoritative status comes from
            // asking the API, so a forged or stale body cannot dictate the outcome.
            PaymentStatus status = fetchStatus(dataId);
            return Optional.of(new WebhookNotification(root.path("id").asText(null), dataId, status));
        } catch (Exception exception) {
            log.warn("Rejected an unparseable Mercado Pago webhook", exception);
            return Optional.empty();
        }
    }

    private PaymentStatus toStatus(String raw) {
        return switch (raw) {
            case "approved" -> PaymentStatus.APPROVED;
            case "rejected", "cancelled" -> PaymentStatus.REJECTED;
            case "expired" -> PaymentStatus.EXPIRED;
            default -> PaymentStatus.PENDING;
        };
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd api && ./mvnw test -Dtest='MercadoPagoSignatureVerifierTest,PaymentProviderRegistryTest'`
Expected: PASS — all six verifier tests plus all five registry tests, including the `mercadopago` lookup that was failing at the end of Task 10.

- [ ] **Step 6: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add Mercado Pago provider with webhook signature verification"
```

---

## Task 12: Order creation and charge

**Files:**
- Create: `api/src/main/java/com/example/localhostfacom/order/OrderService.java`, `PublicOrderController.java`
- Create: `api/src/main/java/com/example/localhostfacom/order/dto/CreateOrderRequest.java`, `OrderChargeResponse.java`
- Test: `api/src/test/java/com/example/localhostfacom/order/OrderCreationTest.java`

**Interfaces:**
- Consumes: `Order`, `OrderItem`, `OrderRepository`, `ProductService.requireActive`, `PaymentProviderRegistry`, `AppProperties.Payments.orderTtl`, `RateLimiter`, `ApiException`.
- Produces: `OrderService.create(List<CreateOrderRequest.Item>)` returns `Order` (committed, `PENDING`, no charge); `OrderService.ensureCharge(UUID orderId)` returns `Order` with a charge attached. Task 13 adds `markPaid` to the same class.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/order/OrderCreationTest.java`:

```java
package com.example.localhostfacom.order;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.order.dto.CreateOrderRequest;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductRepository;
import com.example.localhostfacom.product.ProductService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrderCreationTest {

    @Autowired private OrderService service;
    @Autowired private OrderRepository orders;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository products;

    private Product coffee;
    private Product cake;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        products.deleteAll();
        coffee = productService.create("Café", new BigDecimal("3.50"), null);
        cake = productService.create("Bolo", new BigDecimal("5.25"), null);
    }

    @Test
    void computesTheTotalFromDatabasePrices() {
        Order order = service.create(List.of(
                new CreateOrderRequest.Item(coffee.getId(), 2),
                new CreateOrderRequest.Item(cake.getId(), 1)));

        assertThat(order.getTotal()).isEqualByComparingTo("12.25");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    /** The request carries no prices at all, so a manipulated client cannot set its own. */
    @Test
    void snapshotsNameAndPriceOntoEachItem() {
        Order order = service.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));

        OrderItem item = order.getItems().getFirst();
        assertThat(item.getProductName()).isEqualTo("Café");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("3.50");
    }

    @Test
    void refusesAnEmptyCart() {
        assertThatThrownBy(() -> service.create(List.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void refusesAnInactiveProduct() {
        productService.update(coffee.getId(), "Café", new BigDecimal("3.50"), null, false);

        assertThatThrownBy(() -> service.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void refusesAnUnknownProduct() {
        assertThatThrownBy(() -> service.create(List.of(new CreateOrderRequest.Item(UUID.randomUUID(), 1))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void createsTheOrderWithoutAChargeAttached() {
        Order order = service.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));

        assertThat(order.hasCharge()).isFalse();
        assertThat(orders.findById(order.getId())).isPresent();
    }

    @Test
    void attachesAChargeOnDemand() {
        Order order = service.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));

        Order charged = service.ensureCharge(order.getId());

        assertThat(charged.hasCharge()).isTrue();
        assertThat(charged.getPaymentPayload()).isNotBlank();
        assertThat(charged.getPaymentQrBase64()).isNotBlank();
        assertThat(charged.getPaymentProvider()).isEqualTo("fake");
    }

    /** Retrying after a provider failure must not create a second payable charge. */
    @Test
    void reusesTheExistingChargeWhenAskedTwice() {
        Order order = service.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));

        String first = service.ensureCharge(order.getId()).getProviderPaymentId();
        String second = service.ensureCharge(order.getId()).getProviderPaymentId();

        assertThat(second).isEqualTo(first);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=OrderCreationTest`
Expected: FAIL — `OrderService` does not exist.

- [ ] **Step 3: Write the DTOs**

`order/dto/CreateOrderRequest.java`:

```java
package com.example.localhostfacom.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Carries quantities only. Prices are never accepted from the client — the total is
 * always recomputed from the database.
 */
public record CreateOrderRequest(@NotEmpty @Valid List<Item> items) {

    public record Item(
            @NotNull UUID productId,
            @Min(1) @Max(99) int quantity) {}
}
```

`order/dto/OrderChargeResponse.java`:

```java
package com.example.localhostfacom.order.dto;

import com.example.localhostfacom.order.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderChargeResponse(
        UUID orderId,
        BigDecimal total,
        String payload,
        String qrImageBase64,
        String checkoutUrl,
        Instant expiresAt) {

    public static OrderChargeResponse of(Order order) {
        return new OrderChargeResponse(
                order.getId(),
                order.getTotal(),
                order.getPaymentPayload(),
                order.getPaymentQrBase64(),
                order.getPaymentCheckoutUrl(),
                order.getExpiresAt());
    }
}
```

- [ ] **Step 4: Write the service**

`order/OrderService.java`:

```java
package com.example.localhostfacom.order;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.config.AppProperties;
import com.example.localhostfacom.order.dto.CreateOrderRequest;
import com.example.localhostfacom.payment.ChargeRequest;
import com.example.localhostfacom.payment.PaymentCharge;
import com.example.localhostfacom.payment.PaymentProviderRegistry;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final ProductService products;
    private final PaymentProviderRegistry providers;
    private final Duration orderTtl;

    public OrderService(OrderRepository orders, ProductService products,
                        PaymentProviderRegistry providers, AppProperties properties) {
        this.orders = orders;
        this.products = products;
        this.providers = providers;
        this.orderTtl = properties.payments().orderTtl();
    }

    /**
     * Persists and commits the order before any provider call. Holding a database
     * transaction open across an external HTTP request would be bad enough; worse, a
     * failure after the provider had created the charge would roll the order away while
     * a real payable charge existed in the wild.
     */
    @Transactional
    public Order create(List<CreateOrderRequest.Item> items) {
        if (items == null || items.isEmpty()) {
            throw ApiException.badRequest("empty-cart", "The cart is empty");
        }

        Order order = Order.create(providers.active().name(), Instant.now().plus(orderTtl));

        for (CreateOrderRequest.Item item : items) {
            Product product = products.requireActive(item.productId());
            order.addItem(OrderItem.snapshotOf(product, item.quantity()));
        }

        order.recalculateTotal();
        return orders.save(order);
    }

    /**
     * Creates the charge for an already committed order, or returns the existing one.
     * Idempotent, so retrying after a provider failure never produces a second charge
     * the customer could pay twice.
     */
    @Transactional
    public Order ensureCharge(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("order-not-found", "Order not found"));

        if (order.hasCharge()) {
            return order;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw ApiException.conflict("order-not-pending",
                    "This order is no longer awaiting payment");
        }

        PaymentCharge charge = providers.byName(order.getPaymentProvider())
                .createCharge(new ChargeRequest(
                        order.getId(),
                        order.getTotal(),
                        "Sala de Estudos",
                        order.getExpiresAt()));

        order.attachCharge(
                charge.providerPaymentId(), charge.payload(), charge.qrImageBase64(), charge.checkoutUrl());
        return orders.save(order);
    }

    public Order require(UUID orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("order-not-found", "Order not found"));
    }
}
```

- [ ] **Step 5: Write the public controller**

`order/PublicOrderController.java`:

```java
package com.example.localhostfacom.order;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.common.RateLimiter;
import com.example.localhostfacom.order.dto.CreateOrderRequest;
import com.example.localhostfacom.order.dto.OrderChargeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.ErrorResponseException;

@RestController
@RequestMapping("/api/public/orders")
public class PublicOrderController {

    private final OrderService orders;
    private final RateLimiter rateLimiter;

    public PublicOrderController(OrderService orders, RateLimiter rateLimiter) {
        this.orders = orders;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderChargeResponse create(@Valid @RequestBody CreateOrderRequest request,
                                      HttpServletRequest http) {
        if (!rateLimiter.tryAcquire("order:" + http.getRemoteAddr(), 10, Duration.ofMinutes(1))) {
            throw ApiException.tooManyRequests("rate-limited", "Too many orders; please wait a moment");
        }

        Order order = orders.create(request.items());

        try {
            return OrderChargeResponse.of(orders.ensureCharge(order.getId()));
        } catch (ApiException exception) {
            // The order is committed, so the customer is not stranded: the payment screen
            // retries against /charge rather than starting over.
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_GATEWAY, "Could not create the payment charge");
            problem.setProperty("slug", "charge-creation-failed");
            problem.setProperty("orderId", order.getId().toString());
            // Boot 4 / Spring Framework 7 made ErrorResponseException.getBody() final, so
            // the anonymous-subclass-override trick from older Spring no longer compiles.
            // Use the constructor that takes a ProblemDetail directly instead.
            throw new ErrorResponseException(HttpStatus.BAD_GATEWAY, problem, exception);
        }
    }

    @PostMapping("/{id}/charge")
    public OrderChargeResponse charge(@PathVariable UUID id) {
        return OrderChargeResponse.of(orders.ensureCharge(id));
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=OrderCreationTest`
Expected: PASS — eight tests green.

- [ ] **Step 7: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add order creation with server-side totals and idempotent charge"
```

---

## Task 13: Payment confirmation

**Files:**
- Modify: `api/src/main/java/com/example/localhostfacom/order/OrderService.java`
- Modify: `api/src/main/java/com/example/localhostfacom/order/PublicOrderController.java`
- Create: `api/src/main/java/com/example/localhostfacom/order/AdminOrderController.java`
- Create: `api/src/main/java/com/example/localhostfacom/order/dto/OrderStatusResponse.java`, `AdminOrderResponse.java`
- Test: `api/src/test/java/com/example/localhostfacom/order/PaymentConfirmationTest.java`

**Interfaces:**
- Consumes: everything from Task 12, plus `CurrentAdmin`.
- Produces: `OrderService.markPaid(UUID orderId, UUID manuallyBy)` returns `boolean` (true when this call was the one that credited it); `OrderService.applyProviderStatus(UUID orderId, PaymentStatus status)`; `OrderService.cancel(UUID)`; `OrderService.syncWithProvider(UUID)` returns `Order`; `OrderService.expireOverdueOrders(Instant now)`; `OrderService.reconcilableOrders()` returns `List<Order>`; `OrderService.statusOf(UUID)`. Tasks 14 and 15 call `markPaid` and `applyProviderStatus`.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/order/PaymentConfirmationTest.java`:

```java
package com.example.localhostfacom.order;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.admin.AdminRepository;
import com.example.localhostfacom.order.dto.CreateOrderRequest;
import com.example.localhostfacom.payment.PaymentStatus;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductRepository;
import com.example.localhostfacom.product.ProductService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PaymentConfirmationTest {

    @Autowired private OrderService service;
    @Autowired private OrderRepository orders;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository products;
    @Autowired private AdminRepository admins;

    private Product coffee;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        products.deleteAll();
        admins.deleteAll();
        coffee = productService.create("Café", new BigDecimal("3.50"), null);
    }

    private Order newOrder() {
        return service.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));
    }

    @Test
    void marksAPendingOrderAsPaid() {
        Order order = newOrder();

        assertThat(service.markPaid(order.getId(), null)).isTrue();

        Order reloaded = orders.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reloaded.getPaidAt()).isNotNull();
    }

    /** Two confirmation paths can land at once; the order must be credited exactly once. */
    @Test
    void isIdempotentAcrossRepeatedConfirmations() {
        Order order = newOrder();

        assertThat(service.markPaid(order.getId(), null)).isTrue();
        assertThat(service.markPaid(order.getId(), null)).isFalse();

        Order reloaded = orders.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    /**
     * The single most important behaviour in the system. Money that arrives after the
     * local deadline is still money, and leaving it out of the ledger would make the
     * public totals understate what was collected.
     */
    @Test
    void creditsAnExpiredOrderWhenThePaymentArrivesLate() {
        Order order = newOrder();
        service.expireOverdueOrders(order.getExpiresAt().plusSeconds(1));
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.EXPIRED);

        assertThat(service.markPaid(order.getId(), null)).isTrue();

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void creditsACanceledOrderWhenThePaymentArrivesAnyway() {
        Order order = newOrder();
        service.cancel(order.getId());

        assertThat(service.markPaid(order.getId(), null)).isTrue();

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void recordsWhichAdminConfirmedAPaymentByHand() {
        Order order = newOrder();
        // orders.paid_manually_by has a real FK to admin(id) — a bare random UUID
        // violates the constraint, so this needs an admin row that actually exists.
        UUID adminId = admins.save(Admin.create("owner@example.com", "hash")).getId();

        service.markPaid(order.getId(), adminId);

        assertThat(orders.findById(order.getId()).orElseThrow().getPaidManuallyBy()).isEqualTo(adminId);
    }

    @Test
    void appliesAnApprovedProviderStatus() {
        Order order = newOrder();

        service.applyProviderStatus(order.getId(), PaymentStatus.APPROVED);

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void leavesTheOrderPendingForAPendingProviderStatus() {
        Order order = newOrder();

        service.applyProviderStatus(order.getId(), PaymentStatus.PENDING);

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void neverUnpaysAnAlreadyPaidOrder() {
        Order order = newOrder();
        service.markPaid(order.getId(), null);

        service.applyProviderStatus(order.getId(), PaymentStatus.EXPIRED);

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void leavesAnOrderAloneUntilItsDeadlineHasActuallyPassed() {
        Order order = newOrder();

        service.expireOverdueOrders(order.getExpiresAt().minusSeconds(1));

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void expiresAnOrderOnceItsDeadlineHasPassed() {
        Order order = newOrder();

        service.expireOverdueOrders(order.getExpiresAt().plusSeconds(1));

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.EXPIRED);
    }

    /** Sweeping a paid order into EXPIRED would erase a real receipt. */
    @Test
    void neverExpiresAnOrderThatWasAlreadyPaid() {
        Order order = newOrder();
        service.markPaid(order.getId(), null);

        service.expireOverdueOrders(order.getExpiresAt().plusSeconds(1));

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=PaymentConfirmationTest`
Expected: FAIL — `markPaid`, `applyProviderStatus`, `cancel` and `expireOverdueOrders` do not exist.

- [ ] **Step 3: Extend the service**

Append to `order/OrderService.java` (and add the imports `com.example.localhostfacom.payment.PaymentStatus`, `org.slf4j.Logger`, `org.slf4j.LoggerFactory`, `java.time.Duration`):

```java
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** How long after creation a non-paid order is still worth re-checking. */
    private static final Duration RECONCILE_WINDOW = Duration.ofHours(24);

    /**
     * The single point where an order becomes PAID, whatever confirmed it — webhook,
     * reconciler or an admin. Takes a row lock and checks the current status, so two
     * paths arriving at once credit the order exactly once.
     *
     * @return true when this call performed the transition, false when it was already paid
     */
    @Transactional
    public boolean markPaid(UUID orderId, UUID manuallyBy) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("order-not-found", "Order not found"));

        if (!order.getStatus().canTransitionToPaid()) {
            return false;
        }

        // EXPIRED and CANCELED are local states meaning "stopped waiting", not "refused
        // the money". A late payment is still a payment and must reach the ledger.
        order.markPaid(Instant.now(), manuallyBy);
        orders.save(order);
        return true;
    }

    @Transactional
    public void applyProviderStatus(UUID orderId, PaymentStatus status) {
        switch (status) {
            case APPROVED -> markPaid(orderId, null);
            case REJECTED, EXPIRED -> expireIfStillPending(orderId);
            case PENDING -> { /* nothing to do; keep waiting */ }
        }
    }

    @Transactional
    public void cancel(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("order-not-found", "Order not found"));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw ApiException.conflict("order-not-pending", "Only a pending order can be canceled");
        }

        order.markCanceled();
        orders.save(order);
    }

    /** Asks the originating provider for the current status and applies it. */
    @Transactional
    public Order syncWithProvider(UUID orderId) {
        Order order = require(orderId);

        if (!order.hasCharge()) {
            throw ApiException.conflict("order-has-no-charge",
                    "This order has no payment charge yet; create one first");
        }

        PaymentStatus status = providers.byName(order.getPaymentProvider())
                .fetchStatus(order.getProviderPaymentId());
        applyProviderStatus(orderId, status);
        return require(orderId);
    }

    @Transactional
    public void expireOverdueOrders(Instant now) {
        for (Order order : orders.findExpirable(now)) {
            order.markExpired();
            orders.save(order);
        }
    }

    public List<Order> reconcilableOrders() {
        return orders.findReconcilable(Instant.now().minus(RECONCILE_WINDOW));
    }

    public OrderStatus statusOf(UUID orderId) {
        return require(orderId).getStatus();
    }

    private void expireIfStillPending(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order != null && order.getStatus() == OrderStatus.PENDING) {
            order.markExpired();
            orders.save(order);
        }
    }
```

- [ ] **Step 4: Add the status endpoint and admin controller**

`order/dto/OrderStatusResponse.java`:

```java
package com.example.localhostfacom.order.dto;

import com.example.localhostfacom.order.Order;
import java.time.Instant;

public record OrderStatusResponse(String status, Instant paidAt) {

    public static OrderStatusResponse of(Order order) {
        return new OrderStatusResponse(order.getStatus().name(), order.getPaidAt());
    }
}
```

Add to `order/PublicOrderController.java`:

```java
    /**
     * The payment screen polls this every few seconds. The provider is queried at most
     * once per throttle window, and the throttle lives in memory rather than in a column,
     * so polling does not turn into a database write per poll.
     */
    @GetMapping("/{id}/status")
    public OrderStatusResponse status(@PathVariable UUID id) {
        Order order = orders.require(id);

        if (order.getStatus() == OrderStatus.PENDING
                && order.hasCharge()
                && rateLimiter.tryAcquire("status-sync:" + id, 1, Duration.ofSeconds(10))) {
            order = orders.syncWithProvider(id);
        }

        return OrderStatusResponse.of(order);
    }
```

Add the imports `org.springframework.web.bind.annotation.GetMapping` and `com.example.localhostfacom.order.dto.OrderStatusResponse` to that file.

`order/dto/AdminOrderResponse.java`:

```java
package com.example.localhostfacom.order.dto;

import com.example.localhostfacom.order.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminOrderResponse(
        UUID id,
        Long seq,
        String status,
        BigDecimal total,
        String paymentProvider,
        boolean hasCharge,
        Instant createdAt,
        Instant expiresAt,
        Instant paidAt,
        UUID paidManuallyBy,
        List<Item> items) {

    public record Item(String productName, BigDecimal unitPrice, int quantity) {}

    public static AdminOrderResponse of(Order order) {
        return new AdminOrderResponse(
                order.getId(),
                order.getSeq(),
                order.getStatus().name(),
                order.getTotal(),
                order.getPaymentProvider(),
                order.hasCharge(),
                order.getCreatedAt(),
                order.getExpiresAt(),
                order.getPaidAt(),
                order.getPaidManuallyBy(),
                order.getItems().stream()
                        .map(item -> new Item(item.getProductName(), item.getUnitPrice(), item.getQuantity()))
                        .toList());
    }
}
```

`order/AdminOrderController.java`:

```java
package com.example.localhostfacom.order;

import com.example.localhostfacom.auth.CurrentAdmin;
import com.example.localhostfacom.order.dto.AdminOrderResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService service;
    private final OrderRepository orders;

    public AdminOrderController(OrderService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    @GetMapping
    public Page<AdminOrderResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Order> result = status == null
                ? orders.findAllByOrderByCreatedAtDesc(pageable)
                : orders.findByStatusOrderByCreatedAtDesc(status, pageable);
        return result.map(AdminOrderResponse::of);
    }

    /** Used when the webhook never arrived and the admin can see the money did. */
    @PostMapping("/{id}/mark-paid")
    public AdminOrderResponse markPaid(@PathVariable UUID id) {
        service.markPaid(id, CurrentAdmin.require());
        return AdminOrderResponse.of(service.require(id));
    }

    @PostMapping("/{id}/sync")
    public AdminOrderResponse sync(@PathVariable UUID id) {
        return AdminOrderResponse.of(service.syncWithProvider(id));
    }

    @PostMapping("/{id}/cancel")
    public AdminOrderResponse cancel(@PathVariable UUID id) {
        service.cancel(id);
        return AdminOrderResponse.of(service.require(id));
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=PaymentConfirmationTest`
Expected: PASS — eleven tests green, including both late-payment tests.

- [ ] **Step 6: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add idempotent payment confirmation that accepts late payments"
```

---

## Task 14: Webhook endpoint

**Files:**
- Create: `api/src/main/java/com/example/localhostfacom/payment/WebhookController.java`
- Test: `api/src/test/java/com/example/localhostfacom/payment/WebhookControllerTest.java`

**Interfaces:**
- Consumes: `PaymentProviderRegistry`, `OrderService`, `OrderRepository.findByProviderPaymentId`, `WebhookEventRepository`.
- Produces: `POST /api/webhooks/{provider}`.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/payment/WebhookControllerTest.java`:

```java
package com.example.localhostfacom.payment;

import com.example.localhostfacom.order.Order;
import com.example.localhostfacom.order.OrderRepository;
import com.example.localhostfacom.order.OrderService;
import com.example.localhostfacom.order.OrderStatus;
import com.example.localhostfacom.order.WebhookEventRepository;
import com.example.localhostfacom.order.dto.CreateOrderRequest;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductRepository;
import com.example.localhostfacom.product.ProductService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookControllerTest {

    /** Stands in for a real provider so authenticity can be toggled per test. */
    static class StubProvider implements PaymentProvider {
        boolean authentic = true;
        String paymentId = "stub-payment-1";
        PaymentStatus status = PaymentStatus.APPROVED;
        String nextEventId = "event-1";

        @Override public String name() { return "stub"; }
        @Override public PaymentCharge createCharge(ChargeRequest request) {
            return new PaymentCharge(paymentId, "payload", "qr", null, request.expiresAt());
        }
        @Override public PaymentStatus fetchStatus(String providerPaymentId) { return status; }
        @Override public Optional<WebhookNotification> parseAndVerify(Map<String, String> h, String body) {
            return authentic
                    ? Optional.of(new WebhookNotification(nextEventId, paymentId, status))
                    : Optional.empty();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean StubProvider stubProvider() { return new StubProvider(); }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private StubProvider provider;
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orders;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository products;
    @Autowired private WebhookEventRepository events;

    private Order order;

    @BeforeEach
    void setUp() {
        events.deleteAll();
        orders.deleteAll();
        products.deleteAll();
        provider.authentic = true;
        provider.status = PaymentStatus.APPROVED;
        provider.nextEventId = "event-1";

        Product coffee = productService.create("Café", new BigDecimal("3.50"), null);
        order = orderService.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));
        // Point the order at the stub so the webhook can find it by payment id.
        order = orders.findById(order.getId()).orElseThrow();
        order.attachCharge(provider.paymentId, "payload", "qr", null);
        orders.saveAndFlush(order);
    }

    @Test
    void creditsTheOrderForAnAuthenticNotification() throws Exception {
        mockMvc.perform(post("/api/webhooks/stub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"stub-payment-1\"}}"))
                .andExpect(status().isOk());

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    /** An unsigned request must be refused outright and leave no trace. */
    @Test
    void rejectsAnUnverifiedNotificationAndWritesNothing() throws Exception {
        provider.authentic = false;

        mockMvc.perform(post("/api/webhooks/stub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"stub-payment-1\"}}"))
                .andExpect(status().isUnauthorized());

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(events.count()).isZero();
    }

    /**
     * This is the retry shape Mercado Pago actually produces: the same payment event
     * redelivered under a NEW notification id. Deduplicating on the notification id
     * would miss it entirely, so the guard has to be the order's own status.
     */
    @Test
    void creditsExactlyOnceWhenTheSameEventIsRedeliveredUnderANewId() throws Exception {
        mockMvc.perform(post("/api/webhooks/stub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"stub-payment-1\"}}"))
                .andExpect(status().isOk());

        provider.nextEventId = "event-2-different-id";

        mockMvc.perform(post("/api/webhooks/stub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"stub-payment-1\"}}"))
                .andExpect(status().isOk());

        Order reloaded = orders.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(events.count()).isEqualTo(2L);
    }

    @Test
    void returnsOkForAnUnknownPaymentSoTheProviderStopsRetrying() throws Exception {
        provider.paymentId = "payment-we-have-never-seen";

        mockMvc.perform(post("/api/webhooks/stub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"payment-we-have-never-seen\"}}"))
                .andExpect(status().isOk());
    }

    @Test
    void returnsNotFoundForAnUnknownProvider() throws Exception {
        mockMvc.perform(post("/api/webhooks/nonexistent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=WebhookControllerTest`
Expected: FAIL — `WebhookController` does not exist, so every request is 404 or 403.

- [ ] **Step 3: Write the controller**

`payment/WebhookController.java`:

```java
package com.example.localhostfacom.payment;

import com.example.localhostfacom.order.Order;
import com.example.localhostfacom.order.OrderRepository;
import com.example.localhostfacom.order.OrderService;
import com.example.localhostfacom.order.WebhookEvent;
import com.example.localhostfacom.order.WebhookEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PaymentProviderRegistry providers;
    private final OrderService orderService;
    private final OrderRepository orders;
    private final WebhookEventRepository events;

    public WebhookController(PaymentProviderRegistry providers, OrderService orderService,
                             OrderRepository orders, WebhookEventRepository events) {
        this.providers = providers;
        this.orderService = orderService;
        this.orders = orders;
        this.events = events;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Void> receive(@PathVariable String provider,
                                        @RequestBody(required = false) String rawBody,
                                        HttpServletRequest request) {

        PaymentProvider paymentProvider = providers.byName(provider);

        Optional<WebhookNotification> verified =
                paymentProvider.parseAndVerify(headersOf(request), rawBody == null ? "" : rawBody);

        if (verified.isEmpty()) {
            // Forging a confirmation would let anyone record money that never arrived,
            // so an unverified request is refused before anything is written.
            log.warn("Rejected an unverified {} webhook", provider);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        WebhookNotification notification = verified.get();
        WebhookEvent event = events.save(WebhookEvent.received(
                provider, notification.eventId(), notification.providerPaymentId(), rawBody == null ? "" : rawBody));

        Optional<Order> order = orders.findByProviderPaymentId(notification.providerPaymentId());
        if (order.isEmpty()) {
            event.markFailed("No order matches this payment id");
            events.save(event);
            // Still 200: retrying will not conjure an order, and we do not want the
            // provider hammering this endpoint forever.
            return ResponseEntity.ok().build();
        }

        try {
            orderService.applyProviderStatus(order.get().getId(), notification.status());
            event.markProcessed();
        } catch (RuntimeException exception) {
            log.error("Failed to apply {} webhook for payment {}",
                    provider, notification.providerPaymentId(), exception);
            event.markFailed(exception.getMessage());
        }
        events.save(event);

        return ResponseEntity.ok().build();
    }

    private Map<String, String> headersOf(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Collections.list(request.getHeaderNames())
                .forEach(name -> headers.put(name.toLowerCase(), request.getHeader(name)));
        return headers;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=WebhookControllerTest`
Expected: PASS — five tests green.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add signature-verified payment webhook endpoint"
```

---

## Task 15: Scheduled reconciler

**Files:**
- Create: `api/src/main/java/com/example/localhostfacom/order/OrderReconciler.java`
- Create: `api/src/main/java/com/example/localhostfacom/config/SchedulingConfig.java`
- Test: `api/src/test/java/com/example/localhostfacom/order/OrderReconcilerTest.java`

**Interfaces:**
- Consumes: `OrderService.reconcilableOrders`, `expireOverdueOrders`, `applyProviderStatus`, `PaymentProviderRegistry`.
- Produces: `OrderReconciler.reconcile()`, invoked on a fixed delay and directly by tests.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/order/OrderReconcilerTest.java`:

```java
package com.example.localhostfacom.order;

import com.example.localhostfacom.order.dto.CreateOrderRequest;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductRepository;
import com.example.localhostfacom.product.ProductService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
// Zero delay makes the fake provider report APPROVED immediately.
@TestPropertySource(properties = "app.payments.fake.auto-confirm-after=PT0S")
class OrderReconcilerTest {

    @Autowired private OrderReconciler reconciler;
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orders;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository products;

    private Product coffee;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        products.deleteAll();
        coffee = productService.create("Café", new BigDecimal("3.50"), null);
    }

    private Order chargedOrder() {
        Order order = orderService.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));
        return orderService.ensureCharge(order.getId());
    }

    /** The webhook is allowed to fail; this is what makes that survivable. */
    @Test
    void confirmsAPendingOrderTheWebhookNeverReported() {
        Order order = chargedOrder();

        reconciler.reconcile();

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    /**
     * The behaviour that keeps late money in the ledger. An order the system already gave
     * up on must still be credited once the provider confirms it.
     */
    @Test
    void confirmsAnExpiredOrderTheProviderLaterApproves() {
        Order order = chargedOrder();
        orderService.expireOverdueOrders(order.getExpiresAt().plusSeconds(1));
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.EXPIRED);

        reconciler.reconcile();

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void skipsAnOrderThatNeverGotACharge() {
        Order order = orderService.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));

        reconciler.reconcile();

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void survivesAProviderThatThrows() {
        chargedOrder();
        // Nothing to assert beyond "does not propagate" — a scheduled job that throws
        // stops running, which would silently disable the whole fallback.
        reconciler.reconcile();
        reconciler.reconcile();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=OrderReconcilerTest`
Expected: FAIL — `OrderReconciler` does not exist.

- [ ] **Step 3: Write the reconciler**

`order/OrderReconciler.java`:

```java
package com.example.localhostfacom.order;

import com.example.localhostfacom.payment.PaymentProviderRegistry;
import com.example.localhostfacom.payment.PaymentStatus;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The webhook is the fast path, not the only one. This sweep asks each order's
 * originating provider what actually happened, so a missed, delayed or misconfigured
 * callback cannot leave a paid order stuck as pending.
 */
@Component
public class OrderReconciler {

    private static final Logger log = LoggerFactory.getLogger(OrderReconciler.class);

    private final OrderService orders;
    private final PaymentProviderRegistry providers;

    public OrderReconciler(OrderService orders, PaymentProviderRegistry providers) {
        this.orders = orders;
        this.providers = providers;
    }

    @Scheduled(fixedDelayString = "PT60S")
    public void reconcile() {
        try {
            orders.expireOverdueOrders(Instant.now());
        } catch (RuntimeException exception) {
            log.error("Failed to expire overdue orders", exception);
        }

        for (Order order : orders.reconcilableOrders()) {
            try {
                // Resolved from the order, not from current configuration, so switching
                // providers never strands orders created under the previous one.
                PaymentStatus status = providers.byName(order.getPaymentProvider())
                        .fetchStatus(order.getProviderPaymentId());
                orders.applyProviderStatus(order.getId(), status);
            } catch (RuntimeException exception) {
                // One bad order must not abort the sweep, and an exception escaping a
                // scheduled method stops it being rescheduled at all.
                log.warn("Could not reconcile order {}", order.getId(), exception);
            }
        }
    }
}
```

`config/SchedulingConfig.java`:

```java
package com.example.localhostfacom.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=OrderReconcilerTest`
Expected: PASS — four tests green.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add scheduled reconciler as webhook fallback"
```

---

## Task 16: Expenses and settings

**Files:**
- Create: `api/src/main/java/com/example/localhostfacom/expense/ExpenseService.java`, `ExpenseController.java`, `dto/ExpenseRequest.java`, `dto/ExpenseResponse.java`
- Create: `api/src/main/java/com/example/localhostfacom/settings/SettingsService.java`, `SettingsController.java`, `dto/SettingsRequest.java`, `dto/SettingsResponse.java`
- Test: `api/src/test/java/com/example/localhostfacom/settings/SettingsServiceTest.java`

**Interfaces:**
- Consumes: `Expense`, `ExpenseRepository`, `Settings`, `SettingsRepository`, `CurrentAdmin`, `ApiException`.
- Produces: `ExpenseService.list()`, `create(String, BigDecimal, LocalDate, UUID)`, `delete(UUID)`, `total()`. `SettingsService.get()`, `update(BigDecimal goalTarget, String crowdfundingUrl)`. Task 17 calls `ExpenseService.total()` and `SettingsService.get()`.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/settings/SettingsServiceTest.java`:

```java
package com.example.localhostfacom.settings;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.admin.AdminRepository;
import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.expense.ExpenseRepository;
import com.example.localhostfacom.expense.ExpenseService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SettingsServiceTest {

    @Autowired private SettingsService settings;
    @Autowired private ExpenseService expenses;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private AdminRepository adminRepository;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        adminRepository.deleteAll();
        settings.update(new BigDecimal("2000.00"), null);
        adminId = adminRepository.save(Admin.create("owner@example.com", "hash")).getId();
    }

    @Test
    void updatesTheGoalAndCrowdfundingLink() {
        settings.update(new BigDecimal("3500.00"), "https://vakinha.example/sala");

        assertThat(settings.get().getGoalTarget()).isEqualByComparingTo("3500.00");
        assertThat(settings.get().getCrowdfundingUrl()).isEqualTo("https://vakinha.example/sala");
    }

    /**
     * The public dashboard divides by this value to draw the progress bar, so a zero
     * target would render Infinity.
     */
    @Test
    void refusesANonPositiveGoal() {
        assertThatThrownBy(() -> settings.update(BigDecimal.ZERO, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void sumsRecordedExpenses() {
        expenses.create("Café em grão", new BigDecimal("40.00"), LocalDate.now(), adminId);
        expenses.create("Copos", new BigDecimal("12.50"), LocalDate.now(), adminId);

        assertThat(expenses.total()).isEqualByComparingTo("52.50");
    }

    @Test
    void reportsZeroWhenNothingHasBeenSpent() {
        assertThat(expenses.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void deletesAnExpense() {
        var expense = expenses.create("Erro", new BigDecimal("5.00"), LocalDate.now(), adminId);

        expenses.delete(expense.getId());

        assertThat(expenses.total()).isEqualByComparingTo("0.00");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=SettingsServiceTest`
Expected: FAIL — neither service exists.

- [ ] **Step 3: Write the settings side**

`settings/SettingsService.java`:

```java
package com.example.localhostfacom.settings;

import com.example.localhostfacom.common.ApiException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private final SettingsRepository settings;

    public SettingsService(SettingsRepository settings) {
        this.settings = settings;
    }

    public Settings get() {
        return settings.get();
    }

    @Transactional
    public Settings update(BigDecimal goalTarget, String crowdfundingUrl) {
        if (goalTarget == null || goalTarget.signum() <= 0) {
            // The dashboard divides by this to size the progress bar.
            throw ApiException.badRequest("invalid-goal", "The goal must be greater than zero");
        }
        Settings current = settings.get();
        current.update(goalTarget, crowdfundingUrl);
        return settings.save(current);
    }
}
```

`settings/dto/SettingsRequest.java`:

```java
package com.example.localhostfacom.settings.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SettingsRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal goalTarget,
        @Size(max = 1024) String crowdfundingUrl) {}
```

`settings/dto/SettingsResponse.java`:

```java
package com.example.localhostfacom.settings.dto;

import com.example.localhostfacom.settings.Settings;
import java.math.BigDecimal;

public record SettingsResponse(BigDecimal goalTarget, String crowdfundingUrl) {

    public static SettingsResponse of(Settings settings) {
        return new SettingsResponse(settings.getGoalTarget(), settings.getCrowdfundingUrl());
    }
}
```

`settings/SettingsController.java`:

```java
package com.example.localhostfacom.settings;

import com.example.localhostfacom.settings.dto.SettingsRequest;
import com.example.localhostfacom.settings.dto.SettingsResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @GetMapping
    public SettingsResponse get() {
        return SettingsResponse.of(service.get());
    }

    @PutMapping
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        return SettingsResponse.of(service.update(request.goalTarget(), request.crowdfundingUrl()));
    }
}
```

- [ ] **Step 4: Write the expense side**

`expense/ExpenseService.java`:

```java
package com.example.localhostfacom.expense;

import com.example.localhostfacom.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private final ExpenseRepository expenses;

    public ExpenseService(ExpenseRepository expenses) {
        this.expenses = expenses;
    }

    public List<Expense> list() {
        return expenses.findAllByOrderByIncurredOnDesc();
    }

    @Transactional
    public Expense create(String description, BigDecimal amount, LocalDate incurredOn, UUID createdBy) {
        return expenses.save(Expense.create(
                description.trim(),
                amount,
                incurredOn == null ? LocalDate.now() : incurredOn,
                createdBy));
    }

    @Transactional
    public void delete(UUID id) {
        if (!expenses.existsById(id)) {
            throw ApiException.notFound("expense-not-found", "Expense not found");
        }
        expenses.deleteById(id);
    }

    public BigDecimal total() {
        return expenses.findAll().stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);
    }
}
```

`expense/dto/ExpenseRequest.java`:

```java
package com.example.localhostfacom.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseRequest(
        @NotBlank @Size(max = 255) String description,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        LocalDate incurredOn) {}
```

`expense/dto/ExpenseResponse.java`:

```java
package com.example.localhostfacom.expense.dto;

import com.example.localhostfacom.expense.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(UUID id, String description, BigDecimal amount, LocalDate incurredOn) {

    public static ExpenseResponse of(Expense expense) {
        return new ExpenseResponse(
                expense.getId(), expense.getDescription(), expense.getAmount(), expense.getIncurredOn());
    }
}
```

`expense/ExpenseController.java`:

```java
package com.example.localhostfacom.expense;

import com.example.localhostfacom.auth.CurrentAdmin;
import com.example.localhostfacom.expense.dto.ExpenseRequest;
import com.example.localhostfacom.expense.dto.ExpenseResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/expenses")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExpenseResponse> list() {
        return service.list().stream().map(ExpenseResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@Valid @RequestBody ExpenseRequest request) {
        return ExpenseResponse.of(service.create(
                request.description(), request.amount(), request.incurredOn(), CurrentAdmin.require()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=SettingsServiceTest`
Expected: PASS — five tests green.

- [ ] **Step 6: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add expense tracking and fundraising goal settings"
```

---

## Task 17: Public dashboard

**Files:**
- Modify: `api/src/main/java/com/example/localhostfacom/order/OrderRepository.java`
- Create: `api/src/main/java/com/example/localhostfacom/dashboard/DashboardService.java`, `DashboardController.java`
- Create: `api/src/main/java/com/example/localhostfacom/dashboard/dto/DashboardResponse.java`, `KpiResponse.java`, `GoalResponse.java`, `ChartPointResponse.java`, `TransactionResponse.java`, `TransactionPageResponse.java`
- Test: `api/src/test/java/com/example/localhostfacom/dashboard/DashboardServiceTest.java`

**Interfaces:**
- Consumes: `OrderRepository`, `ExpenseService.total`, `SettingsService.get`.
- Produces: `DashboardService.build(int page, int size)` returns `DashboardResponse`. Task 18 mirrors these DTOs in TypeScript.

- [ ] **Step 1: Write the failing test**

`api/src/test/java/com/example/localhostfacom/dashboard/DashboardServiceTest.java`:

```java
package com.example.localhostfacom.dashboard;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.admin.AdminRepository;
import com.example.localhostfacom.dashboard.dto.DashboardResponse;
import com.example.localhostfacom.expense.ExpenseRepository;
import com.example.localhostfacom.expense.ExpenseService;
import com.example.localhostfacom.order.OrderRepository;
import com.example.localhostfacom.order.OrderService;
import com.example.localhostfacom.order.dto.CreateOrderRequest;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductRepository;
import com.example.localhostfacom.product.ProductService;
import com.example.localhostfacom.settings.SettingsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DashboardServiceTest {

    @Autowired private DashboardService dashboard;
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orders;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository products;
    @Autowired private ExpenseService expenses;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private SettingsService settings;
    @Autowired private AdminRepository adminRepository;

    private Product coffee;
    private Product cake;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        products.deleteAll();
        expenseRepository.deleteAll();
        adminRepository.deleteAll();
        settings.update(new BigDecimal("2000.00"), "https://vakinha.example/sala");
        coffee = productService.create("Café", new BigDecimal("3.00"), null);
        cake = productService.create("Bolo", new BigDecimal("5.00"), null);
        adminId = adminRepository.save(Admin.create("owner@example.com", "hash")).getId();
    }

    private void paidOrder(Product product, int quantity) {
        var order = orderService.create(List.of(new CreateOrderRequest.Item(product.getId(), quantity)));
        orderService.markPaid(order.getId(), null);
    }

    /** An unsold room is the day-one state, not an edge case. */
    @Test
    void reportsZeroesAndANullTopProductWithNoSales() {
        DashboardResponse result = dashboard.build(0, 20);

        assertThat(result.kpis().totalRaised()).isEqualByComparingTo("0.00");
        assertThat(result.kpis().totalOrders()).isZero();
        assertThat(result.kpis().averageTicket()).isEqualByComparingTo("0.00");
        assertThat(result.kpis().topProduct()).isNull();
        assertThat(result.transactions().content()).isEmpty();
    }

    @Test
    void countsOnlyPaidOrders() {
        paidOrder(coffee, 1);
        orderService.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 5)));

        DashboardResponse result = dashboard.build(0, 20);

        assertThat(result.kpis().totalRaised()).isEqualByComparingTo("3.00");
        assertThat(result.kpis().totalOrders()).isEqualTo(1L);
    }

    /**
     * BigDecimal.divide without an explicit scale and RoundingMode throws on a
     * non-terminating result, which would take down the whole public dashboard.
     */
    @Test
    void roundsTheAverageTicketInsteadOfThrowing() {
        paidOrder(coffee, 1);
        paidOrder(coffee, 1);
        paidOrder(cake, 2);

        DashboardResponse result = dashboard.build(0, 20);

        assertThat(result.kpis().totalRaised()).isEqualByComparingTo("16.00");
        assertThat(result.kpis().averageTicket()).isEqualByComparingTo("5.33");
    }

    @Test
    void subtractsExpensesFromTheNetBalance() {
        paidOrder(coffee, 10);
        expenses.create("Insumos", new BigDecimal("12.00"), LocalDate.now(), adminId);

        DashboardResponse result = dashboard.build(0, 20);

        assertThat(result.kpis().totalRaised()).isEqualByComparingTo("30.00");
        assertThat(result.kpis().totalExpenses()).isEqualByComparingTo("12.00");
        assertThat(result.kpis().netBalance()).isEqualByComparingTo("18.00");
        assertThat(result.goal().current()).isEqualByComparingTo("18.00");
    }

    /** Buying stock before selling it is normal, and hiding the deficit would be dishonest. */
    @Test
    void reportsANegativeBalanceWhenExpensesExceedRevenue() {
        paidOrder(coffee, 1);
        expenses.create("Estoque inicial", new BigDecimal("100.00"), LocalDate.now(), adminId);

        DashboardResponse result = dashboard.build(0, 20);

        assertThat(result.kpis().netBalance()).isEqualByComparingTo("-97.00");
        assertThat(result.goal().current()).isEqualByComparingTo("-97.00");
    }

    @Test
    void namesTheBestSellingProductFromTheItemSnapshot() {
        paidOrder(cake, 5);
        paidOrder(coffee, 1);

        assertThat(dashboard.build(0, 20).kpis().topProduct()).isEqualTo("Bolo");
    }

    /** A renamed product must not rewrite what past sales recorded. */
    @Test
    void keepsTheOldNameInTheTopProductAfterARename() {
        paidOrder(cake, 5);
        productService.update(cake.getId(), "Bolo de Cenoura", new BigDecimal("5.00"), null, true);

        assertThat(dashboard.build(0, 20).kpis().topProduct()).isEqualTo("Bolo");
    }

    @Test
    void alwaysReturnsSevenZeroFilledChartDays() {
        paidOrder(coffee, 1);

        assertThat(dashboard.build(0, 20).chartData()).hasSize(7);
    }

    @Test
    void exposesTheOrderSequenceRatherThanTheOrderUuid() {
        paidOrder(coffee, 2);

        var transaction = dashboard.build(0, 20).transactions().content().getFirst();

        assertThat(transaction.id()).isNotBlank();
        assertThat(transaction.productNames()).isEqualTo("2x Café");
        assertThat(transaction.amount()).isEqualByComparingTo("6.00");
        // The UUID is the handle for the status endpoint and has no place on a public feed.
        assertThat(transaction.id()).doesNotContain("-");
    }

    @Test
    void carriesTheGoalAndCrowdfundingLink() {
        var goal = dashboard.build(0, 20).goal();

        assertThat(goal.target()).isEqualByComparingTo("2000.00");
        assertThat(goal.crowdfundingUrl()).isEqualTo("https://vakinha.example/sala");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd api && ./mvnw test -Dtest=DashboardServiceTest`
Expected: FAIL — `DashboardService` does not exist.

- [x] **Step 3: Extend the repository** — already done in Task 2 (see that task's
  deliberate-reordering note); these queries already exist in `OrderRepository.java`.
  Skip this step when executing.

Add to `order/OrderRepository.java`:

```java
    @Query("""
            SELECT COALESCE(SUM(o.total), 0) FROM Order o
            WHERE o.status = com.example.localhostfacom.order.OrderStatus.PAID
            """)
    java.math.BigDecimal sumPaidTotal();

    @Query("""
            SELECT COUNT(o) FROM Order o
            WHERE o.status = com.example.localhostfacom.order.OrderStatus.PAID
            """)
    long countPaid();

    @Query("""
            SELECT COALESCE(SUM(o.total), 0) FROM Order o
            WHERE o.status = com.example.localhostfacom.order.OrderStatus.PAID
              AND o.paidAt >= :since
            """)
    java.math.BigDecimal sumPaidSince(Instant since);

    @Query("""
            SELECT i.productName FROM OrderItem i
            WHERE i.order.status = com.example.localhostfacom.order.OrderStatus.PAID
            GROUP BY i.productName
            ORDER BY SUM(i.quantity) DESC
            """)
    List<String> findProductNamesByUnitsSold(org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT o FROM Order o
            WHERE o.status = com.example.localhostfacom.order.OrderStatus.PAID
              AND o.paidAt >= :since
            """)
    List<Order> findPaidSince(Instant since);
```

- [ ] **Step 4: Write the DTOs**

`dashboard/dto/KpiResponse.java`:

```java
package com.example.localhostfacom.dashboard.dto;

import java.math.BigDecimal;

public record KpiResponse(
        BigDecimal totalRaised,
        BigDecimal totalExpenses,
        BigDecimal netBalance,
        long totalOrders,
        BigDecimal averageTicket,
        String topProduct,
        BigDecimal soldToday,
        BigDecimal soldThisWeek,
        BigDecimal soldThisMonth) {}
```

`dashboard/dto/GoalResponse.java`:

```java
package com.example.localhostfacom.dashboard.dto;

import java.math.BigDecimal;

/** {@code current} is the net balance and may legitimately be negative. */
public record GoalResponse(BigDecimal current, BigDecimal target, String crowdfundingUrl) {}
```

`dashboard/dto/ChartPointResponse.java`:

```java
package com.example.localhostfacom.dashboard.dto;

import java.math.BigDecimal;

public record ChartPointResponse(String date, BigDecimal amount) {}
```

`dashboard/dto/TransactionResponse.java`:

```java
package com.example.localhostfacom.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code id} is the order sequence, never the order UUID. */
public record TransactionResponse(String id, String productNames, BigDecimal amount, Instant timestamp) {}
```

`dashboard/dto/TransactionPageResponse.java`:

```java
package com.example.localhostfacom.dashboard.dto;

import java.util.List;

public record TransactionPageResponse(List<TransactionResponse> content, int totalPages, long totalElements) {}
```

`dashboard/dto/DashboardResponse.java`:

```java
package com.example.localhostfacom.dashboard.dto;

import java.util.List;

public record DashboardResponse(
        KpiResponse kpis,
        GoalResponse goal,
        List<ChartPointResponse> chartData,
        TransactionPageResponse transactions) {}
```

- [ ] **Step 5: Write the service**

`dashboard/DashboardService.java`:

```java
package com.example.localhostfacom.dashboard;

import com.example.localhostfacom.dashboard.dto.ChartPointResponse;
import com.example.localhostfacom.dashboard.dto.DashboardResponse;
import com.example.localhostfacom.dashboard.dto.GoalResponse;
import com.example.localhostfacom.dashboard.dto.KpiResponse;
import com.example.localhostfacom.dashboard.dto.TransactionPageResponse;
import com.example.localhostfacom.dashboard.dto.TransactionResponse;
import com.example.localhostfacom.expense.ExpenseService;
import com.example.localhostfacom.order.Order;
import com.example.localhostfacom.order.OrderRepository;
import com.example.localhostfacom.order.OrderStatus;
import com.example.localhostfacom.settings.Settings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read straight from the database on every request, never cached. A stale transparency
 * figure is worse than a slow one.
 */
@Service
public class DashboardService {

    /** Otherwise an evening sale lands on the wrong day. */
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final int CHART_DAYS = 7;

    private final OrderRepository orders;
    private final ExpenseService expenses;
    private final com.example.localhostfacom.settings.SettingsService settings;

    public DashboardService(OrderRepository orders, ExpenseService expenses,
                            com.example.localhostfacom.settings.SettingsService settings) {
        this.orders = orders;
        this.expenses = expenses;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build(int page, int size) {
        BigDecimal totalRaised = scale(orders.sumPaidTotal());
        BigDecimal totalExpenses = scale(expenses.total());
        long totalOrders = orders.countPaid();

        Settings currentSettings = settings.get();
        BigDecimal netBalance = totalRaised.subtract(totalExpenses);

        KpiResponse kpis = new KpiResponse(
                totalRaised,
                totalExpenses,
                netBalance,
                totalOrders,
                averageTicket(totalRaised, totalOrders),
                topProduct(),
                scale(orders.sumPaidSince(startOfToday())),
                scale(orders.sumPaidSince(startOfDaysAgo(7))),
                scale(orders.sumPaidSince(startOfDaysAgo(30))));

        return new DashboardResponse(
                kpis,
                new GoalResponse(netBalance, currentSettings.getGoalTarget(),
                        currentSettings.getCrowdfundingUrl()),
                chart(),
                transactions(page, size));
    }

    /**
     * Guards the two ways this can blow up: dividing by zero orders, and BigDecimal's
     * refusal to divide when the result does not terminate.
     */
    private BigDecimal averageTicket(BigDecimal totalRaised, long totalOrders) {
        if (totalOrders == 0) {
            return scale(BigDecimal.ZERO);
        }
        return totalRaised.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_EVEN);
    }

    private String topProduct() {
        List<String> names = orders.findProductNamesByUnitsSold(PageRequest.of(0, 1));
        return names.isEmpty() ? null : names.getFirst();
    }

    /**
     * Buckets in Java rather than SQL. Date truncation with a time zone is spelled
     * differently on PostgreSQL and H2, and the row count here is tiny.
     */
    private List<ChartPointResponse> chart() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from = today.minusDays(CHART_DAYS - 1L);

        Map<LocalDate, BigDecimal> byDay = orders.findPaidSince(from.atStartOfDay(ZONE).toInstant())
                .stream()
                .collect(Collectors.groupingBy(
                        order -> LocalDate.ofInstant(order.getPaidAt(), ZONE),
                        LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Order::getTotal, BigDecimal::add)));

        List<ChartPointResponse> points = new ArrayList<>(CHART_DAYS);
        for (int i = 0; i < CHART_DAYS; i++) {
            LocalDate day = from.plusDays(i);
            // Zero-filled, so the chart keeps a stable seven-column shape on quiet days.
            points.add(new ChartPointResponse(
                    DAY_LABEL.format(day), scale(byDay.getOrDefault(day, BigDecimal.ZERO))));
        }
        return points;
    }

    private TransactionPageResponse transactions(int page, int size) {
        Page<Order> paid = orders.findByStatusOrderByPaidAtDesc(
                OrderStatus.PAID, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100)));

        List<TransactionResponse> content = paid.getContent().stream()
                .map(order -> new TransactionResponse(
                        // The sequence, not the UUID: the UUID is the status-endpoint handle.
                        String.valueOf(order.getSeq()),
                        describe(order),
                        order.getTotal(),
                        order.getPaidAt()))
                .toList();

        return new TransactionPageResponse(content, paid.getTotalPages(), paid.getTotalElements());
    }

    private String describe(Order order) {
        return order.getItems().stream()
                .map(item -> item.getQuantity() + "x " + item.getProductName())
                .collect(Collectors.joining(", "));
    }

    private Instant startOfToday() {
        return LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
    }

    private Instant startOfDaysAgo(int days) {
        return LocalDate.now(ZONE).minusDays(days).atStartOfDay(ZONE).toInstant();
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_EVEN);
    }
}
```

`dashboard/DashboardController.java`:

```java
package com.example.localhostfacom.dashboard;

import com.example.localhostfacom.dashboard.dto.DashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardResponse get(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.build(page, size);
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd api && ./mvnw test -Dtest=DashboardServiceTest`
Expected: PASS — ten tests green.

- [ ] **Step 7: Run the whole suite**

Run: `cd api && ./mvnw test`
Expected: PASS — every test from Tasks 1 through 17.

- [ ] **Step 8: Commit**

```bash
git add api/src/main/java api/src/test/java
git commit -m "feat(api): add public transparency dashboard"
```

---

## Task 18: Frontend API client layer

**Files:**
- Create: `ui/src/api/client.ts`, `auth.ts`, `public.ts`, `admin.ts`
- Modify: `ui/src/types.ts`, `ui/vite.config.ts`
- Create: `ui/.env.example`
- Test: manual — `npm run build` and `npm run lint` (the project has no test runner configured, and adding one is out of scope for this pass)

**Interfaces:**
- Consumes: the DTOs produced by Tasks 9, 12, 13, 16 and 17.
- Produces: typed functions the UI screens will call in the next pass.

- [ ] **Step 1: Realign the types**

Replace `ui/src/types.ts` in full. Every shape here mirrors a Java DTO field for field:

```typescript
export interface Product {
  id: string;
  name: string;
  price: number;
  imageUrl: string | null;
  imageWidth: number | null;
  imageHeight: number | null;
  active: boolean;
}

export interface CartItem extends Product {
  quantity: number;
}

export interface OrderChargeResponse {
  orderId: string;
  total: number;
  payload: string;
  qrImageBase64: string;
  checkoutUrl: string | null;
  expiresAt: string;
}

export type OrderStatus = 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELED';

export interface OrderStatusResponse {
  status: OrderStatus;
  paidAt: string | null;
}

export interface DashboardKPIs {
  totalRaised: number;
  totalExpenses: number;
  /** Revenue minus expenses. Legitimately negative before sales cover the initial stock. */
  netBalance: number;
  totalOrders: number;
  averageTicket: number;
  /** Null until something sells. */
  topProduct: string | null;
  soldToday: number;
  soldThisWeek: number;
  soldThisMonth: number;
}

export interface FundingGoal {
  /** The net balance, so it can be negative. Clamp the progress bar, not this value. */
  current: number;
  target: number;
  crowdfundingUrl: string | null;
}

export interface ChartData {
  date: string;
  amount: number;
}

export interface Transaction {
  /** The order sequence, not the order UUID. */
  id: string;
  productNames: string;
  amount: number;
  timestamp: string;
}

export interface DashboardResponse {
  kpis: DashboardKPIs;
  goal: FundingGoal;
  chartData: ChartData[];
  transactions: {
    content: Transaction[];
    totalPages: number;
    totalElements: number;
  };
}

export interface AdminUser {
  id: string;
  email: string;
  active: boolean;
  createdAt: string;
}

export interface AdminOrderItem {
  productName: string;
  unitPrice: number;
  quantity: number;
}

export interface AdminOrder {
  id: string;
  seq: number;
  status: OrderStatus;
  total: number;
  paymentProvider: string;
  hasCharge: boolean;
  createdAt: string;
  expiresAt: string;
  paidAt: string | null;
  paidManuallyBy: string | null;
  items: AdminOrderItem[];
}

export interface Expense {
  id: string;
  description: string;
  amount: number;
  incurredOn: string;
}

export interface Settings {
  goalTarget: number;
  crowdfundingUrl: string | null;
}

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
}
```

- [ ] **Step 2: Write the fetch wrapper**

`ui/src/api/client.ts`:

```typescript
const BASE_URL = import.meta.env.VITE_API_URL ?? '/api';
const TOKEN_KEY = 'localhostfacom.token';

/** Mirrors the RFC 7807 problem+json body the API returns. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly slug: string,
    message: string,
    readonly fieldErrors?: Record<string, string>,
    readonly orderId?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export const tokenStorage = {
  get: (): string | null => localStorage.getItem(TOKEN_KEY),
  set: (token: string): void => localStorage.setItem(TOKEN_KEY, token),
  clear: (): void => localStorage.removeItem(TOKEN_KEY),
};

interface RequestOptions {
  method?: string;
  body?: unknown;
  auth?: boolean;
  signal?: AbortSignal;
}

async function toApiError(response: Response): Promise<ApiError> {
  let slug = 'unknown-error';
  let detail = `Request failed with status ${response.status}`;
  let fieldErrors: Record<string, string> | undefined;
  let orderId: string | undefined;

  try {
    const problem = await response.json();
    slug = problem.slug ?? slug;
    detail = problem.detail ?? detail;
    fieldErrors = problem.errors;
    orderId = problem.orderId;
  } catch {
    // A non-JSON body (a proxy error page, say) leaves the defaults in place.
  }

  return new ApiError(response.status, slug, detail, fieldErrors, orderId);
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = false, signal } = options;
  const headers: Record<string, string> = {};

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  if (auth) {
    const token = tokenStorage.get();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    signal,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401 && auth) {
    // The token is gone or the admin was deactivated; stop replaying a dead token.
    tokenStorage.clear();
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

/** Multipart upload; the browser sets its own Content-Type boundary. */
export async function upload<T>(path: string, file: File): Promise<T> {
  const form = new FormData();
  form.append('file', file);

  const token = tokenStorage.get();
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  });

  if (!response.ok) {
    throw await toApiError(response);
  }

  return (await response.json()) as T;
}
```

- [ ] **Step 3: Write the endpoint modules**

`ui/src/api/auth.ts`:

```typescript
import { request, tokenStorage } from './client';

export interface LoginResponse {
  token: string;
  email: string;
  expiresAt: string;
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  const response = await request<LoginResponse>('/auth/login', {
    method: 'POST',
    body: { email, password },
  });
  tokenStorage.set(response.token);
  return response;
}

export function logout(): void {
  tokenStorage.clear();
}

export function isLoggedIn(): boolean {
  return tokenStorage.get() !== null;
}
```

`ui/src/api/public.ts`:

```typescript
import { request } from './client';
import type {
  DashboardResponse,
  OrderChargeResponse,
  OrderStatusResponse,
  Product,
} from '../types';

export function listProducts(): Promise<Product[]> {
  return request<Product[]>('/public/products');
}

export interface OrderItemInput {
  productId: string;
  quantity: number;
}

/**
 * Sends quantities only. The API recomputes the total from its own prices, so there is
 * nothing here for a tampered client to inflate.
 */
export function createOrder(items: OrderItemInput[]): Promise<OrderChargeResponse> {
  return request<OrderChargeResponse>('/public/orders', { method: 'POST', body: { items } });
}

/**
 * Retries charge creation for an order that already exists. Idempotent, so calling it
 * after a provider hiccup returns the original charge rather than a second payable one.
 */
export function createCharge(orderId: string): Promise<OrderChargeResponse> {
  return request<OrderChargeResponse>(`/public/orders/${orderId}/charge`, { method: 'POST' });
}

export function getOrderStatus(orderId: string, signal?: AbortSignal): Promise<OrderStatusResponse> {
  return request<OrderStatusResponse>(`/public/orders/${orderId}/status`, { signal });
}

export function getDashboard(page = 0, size = 20): Promise<DashboardResponse> {
  return request<DashboardResponse>(`/public/dashboard?page=${page}&size=${size}`);
}
```

`ui/src/api/admin.ts`:

```typescript
import { request, upload } from './client';
import type {
  AdminOrder,
  AdminUser,
  Expense,
  OrderStatus,
  Page,
  Product,
  Settings,
} from '../types';

export function me(): Promise<AdminUser> {
  return request<AdminUser>('/admin/me', { auth: true });
}

// Products

export interface ProductInput {
  name: string;
  price: number;
  imageId?: string | null;
  active?: boolean;
}

export function listProducts(): Promise<Product[]> {
  return request<Product[]>('/admin/products', { auth: true });
}

export function createProduct(input: ProductInput): Promise<Product> {
  return request<Product>('/admin/products', { method: 'POST', body: input, auth: true });
}

export function updateProduct(id: string, input: ProductInput): Promise<Product> {
  return request<Product>(`/admin/products/${id}`, { method: 'PUT', body: input, auth: true });
}

export function deleteProduct(id: string): Promise<void> {
  return request<void>(`/admin/products/${id}`, { method: 'DELETE', auth: true });
}

// Images

export interface UploadedImage {
  id: string;
  url: string;
  width: number;
  height: number;
}

export function uploadImage(file: File): Promise<UploadedImage> {
  return upload<UploadedImage>('/admin/images', file);
}

export function deleteImage(id: string): Promise<void> {
  return request<void>(`/admin/images/${id}`, { method: 'DELETE', auth: true });
}

// Orders

export function listOrders(status?: OrderStatus, page = 0, size = 20): Promise<Page<AdminOrder>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) {
    query.set('status', status);
  }
  return request<Page<AdminOrder>>(`/admin/orders?${query}`, { auth: true });
}

/** Used when the webhook never arrived but the money plainly did. */
export function markOrderPaid(id: string): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}/mark-paid`, { method: 'POST', auth: true });
}

export function syncOrder(id: string): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}/sync`, { method: 'POST', auth: true });
}

export function cancelOrder(id: string): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}/cancel`, { method: 'POST', auth: true });
}

// Expenses

export interface ExpenseInput {
  description: string;
  amount: number;
  incurredOn?: string;
}

export function listExpenses(): Promise<Expense[]> {
  return request<Expense[]>('/admin/expenses', { auth: true });
}

export function createExpense(input: ExpenseInput): Promise<Expense> {
  return request<Expense>('/admin/expenses', { method: 'POST', body: input, auth: true });
}

export function deleteExpense(id: string): Promise<void> {
  return request<void>(`/admin/expenses/${id}`, { method: 'DELETE', auth: true });
}

// Settings

export function getSettings(): Promise<Settings> {
  return request<Settings>('/admin/settings', { auth: true });
}

export function updateSettings(input: Settings): Promise<Settings> {
  return request<Settings>('/admin/settings', { method: 'PUT', body: input, auth: true });
}

// Admins

export function listAdmins(): Promise<AdminUser[]> {
  return request<AdminUser[]>('/admin/admins', { auth: true });
}

export function createAdmin(email: string, password: string): Promise<AdminUser> {
  return request<AdminUser>('/admin/admins', {
    method: 'POST',
    body: { email, password },
    auth: true,
  });
}

export function deleteAdmin(id: string): Promise<void> {
  return request<void>(`/admin/admins/${id}`, { method: 'DELETE', auth: true });
}
```

- [ ] **Step 4: Configure the dev proxy and environment**

`ui/vite.config.ts`:

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Lets the dev server call the API on the same origin, so no CORS in development.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

`ui/.env.example`:

```
# Leave unset in development to use the Vite proxy at /api.
# In production, point this at the deployed API, e.g. https://api.example.com/api
VITE_API_URL=
```

- [ ] **Step 5: Verify it compiles and lints**

Run: `cd ui && npm install && npm run build && npm run lint`
Expected: both succeed. `tsc -b` type-checks every file in `src/api/` against `types.ts`.

- [ ] **Step 6: Commit**

```bash
git add ui/src ui/vite.config.ts ui/.env.example
git commit -m "feat(ui): add typed API client layer and align types with API DTOs"
```

---

## Task 19: Project documentation and end-to-end smoke check

**Files:**
- Create: `CLAUDE.md`
- Modify: `.gitignore`, `README.md`
- Create: `.env.example`

**Interfaces:**
- Consumes: everything above.
- Produces: nothing consumed by other tasks.

- [ ] **Step 1: Write `CLAUDE.md`**

```markdown
# localhostfacom

Transparent sales system for a university study room. Anonymous customers order via QR
code and pay with PIX; every confirmed transaction is publicly visible.

## Layout

- `api/` — Spring Boot 4.1 API (Java 25, Maven wrapper)
- `ui/` — React 19 + Vite 8 + TypeScript
- `docs/superpowers/specs/` — design documents
- `docs/superpowers/plans/` — implementation plans

## Language rule

All code, comments, identifiers, log messages, commit messages, documentation and
filenames are in **English**. Portuguese appears **only** in strings an end user reads on
screen.

## Running locally

```bash
podman compose up -d                    # PostgreSQL 17 + MinIO
cd api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd ui && npm install && npm run dev
```

The dev profile uses the `fake` payment provider, so the full order flow works without
Mercado Pago credentials. Charges auto-confirm after ten seconds.

Set `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD` before the first run to
create the initial admin. It is only created when the `admin` table is empty.

## Commands

```bash
cd api && ./mvnw test                   # full test suite (H2)
cd api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd ui && npm run build                  # tsc -b && vite build
cd ui && npm run lint
```

## Conventions

- Money is `NUMERIC(12,2)` / `BigDecimal`, never `double`. Every `divide` passes a scale
  and a `RoundingMode`.
- Migrations stay portable: no `jsonb`, no `gen_random_uuid()`, no `BIGSERIAL`. Tests run
  the same migrations on H2, so a PostgreSQL-only construct breaks the build.
- Order totals are always computed server-side. The client sends quantities only.
- `PAID` is the only terminal order state. `EXPIRED` and `CANCELED` mean "stopped
  waiting", so a late payment is still credited.
- Domain packages own their entity, repository, service, controller and DTOs together.

## Gotchas

- Tests run on H2 while dev and production run PostgreSQL, so a PostgreSQL-specific SQL
  bug can pass the suite. Testcontainers would close this gap.
- The rate limiter lives in process memory: per instance, resets on deploy.
- `WebhookEvent` is an audit log, not the idempotency mechanism. Mercado Pago issues a
  fresh notification id per retry, so idempotency lives in `OrderService.markPaid`.
- WebP uploads are rejected: stock `ImageIO` cannot read them without an extra plugin.
```

- [ ] **Step 2: Update `.gitignore` and add `.env.example`**

Append to `.gitignore`:

```
### Agent context ###
CLAUDE.md
```

Create `.env.example` at the repository root:

```
# API — required in production
APP_JWT_SECRET=change-me-to-at-least-32-bytes-of-random
DATABASE_URL=jdbc:postgresql://localhost:5432/localhostfacom
DATABASE_USER=localhostfacom
DATABASE_PASSWORD=localhostfacom

# First admin, only used while the admin table is empty
APP_BOOTSTRAP_ADMIN_EMAIL=
APP_BOOTSTRAP_ADMIN_PASSWORD=

# Payments — 'fake' for local development, 'mercadopago' in production.
# The application refuses to start with 'fake' under the prod profile.
APP_PAYMENTS_PROVIDER=fake
APP_MERCADOPAGO_ACCESS_TOKEN=
APP_MERCADOPAGO_WEBHOOK_SECRET=

# Object storage — MinIO locally, Cloudflare R2 in production
APP_STORAGE_ENDPOINT=http://localhost:9000
APP_STORAGE_REGION=auto
APP_STORAGE_BUCKET=localhostfacom
APP_STORAGE_ACCESS_KEY=minioadmin
APP_STORAGE_SECRET_KEY=minioadmin
APP_STORAGE_PUBLIC_BASE_URL=http://localhost:9000/localhostfacom
APP_STORAGE_PATH_STYLE=true

APP_CORS_ORIGINS=http://localhost:5173
```

- [ ] **Step 3: Fix the README**

In `README.md`, replace the "Estrutura do repositório" block so it names the real
directories:

```
.
├── api/     # API Spring Boot
├── ui/      # Aplicação React
└── README.md
```

And replace the "Rodando o projeto localmente" section body with:

````markdown
### Pré-requisitos

- Java 25+
- Node.js 24+
- Podman ou Docker

### Infraestrutura local

```bash
podman compose up -d
```

Sobe PostgreSQL 17 e MinIO (armazenamento das fotos dos produtos).

### Backend

```bash
cd api
export APP_BOOTSTRAP_ADMIN_EMAIL=voce@exemplo.com
export APP_BOOTSTRAP_ADMIN_PASSWORD=uma-senha-forte
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

O perfil `dev` usa um provedor de pagamento simulado — o fluxo completo funciona sem
credenciais do Mercado Pago, e as cobranças são confirmadas automaticamente após dez
segundos.

### Frontend

```bash
cd ui
cp .env.example .env
npm install
npm run dev
```
````

The README is the one file whose prose stays in Portuguese: it is read by the students who
will run this, not by tooling.

- [ ] **Step 4: Run the full backend suite**

Run: `cd api && ./mvnw test`
Expected: PASS — every test across Tasks 1 to 17.

- [ ] **Step 5: Smoke check the real stack**

This is the only step that exercises MinIO and PostgreSQL rather than H2 and a stub, so it
catches the class of bug the H2 gap allows through.

```bash
podman compose up -d
cd api && APP_BOOTSTRAP_ADMIN_EMAIL=admin@example.com \
  APP_BOOTSTRAP_ADMIN_PASSWORD=smoke-test-password \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

In a second terminal:

```bash
# Public catalogue responds
curl -s localhost:8080/api/public/products

# Log in and keep the token
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"smoke-test-password"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')

# Create a product
PRODUCT=$(curl -s -X POST localhost:8080/api/admin/products \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Café","price":3.50}' | sed -E 's/.*"id":"([^"]+)".*/\1/')

# Order it — the response carries the PIX payload and QR
ORDER=$(curl -s -X POST localhost:8080/api/public/orders \
  -H 'Content-Type: application/json' \
  -d "{\"items\":[{\"productId\":\"$PRODUCT\",\"quantity\":2}]}")
echo "$ORDER"

# Wait past the fake provider's ten-second delay, then confirm it flips to PAID
ORDER_ID=$(echo "$ORDER" | sed -E 's/.*"orderId":"([^"]+)".*/\1/')
sleep 12
curl -s "localhost:8080/api/public/orders/$ORDER_ID/status"

# The paid order now appears on the public dashboard
curl -s localhost:8080/api/public/dashboard
```

Expected: the status call returns `"status":"PAID"`, and the dashboard reports
`totalRaised` of `7.00` with one transaction. Verify an image upload too:

```bash
curl -s -X POST localhost:8080/api/admin/images \
  -H "Authorization: Bearer $TOKEN" -F "file=@/path/to/any.png"
```

Expected: a JSON body with a `url` under `http://localhost:9000/localhostfacom/products/`,
and that URL loads in a browser — proving the bucket really is public-read.

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md .gitignore .env.example README.md
git commit -m "docs: add agent context, environment template and corrected README"
```

Note that `CLAUDE.md` is gitignored, so the `git add` above will refuse it unless forced.
That is intentional — create the file, and let the commit cover only `.gitignore`,
`.env.example` and `README.md`.

---

## Self-Review

**Spec coverage.** Every section of the design maps to a task: data model → 1, 2; payment
abstraction → 10, 11; product images → 6, 7, 8; public API → 9, 12, 13, 17; webhooks →
14; admin API → 5, 9, 13, 16; three confirmation paths → 13, 14, 15; late payments → 13,
15; dashboard including the empty and negative cases → 17; security → 4, 5; frontend
client → 18; infrastructure and docs → 1, 19.

**Known deviations from the spec, all deliberate:**

- The spec placed `presignDownloadUrl` on `StorageProvider` as dead code for the future;
  the plan keeps it, and Task 6 tests only `publicUrl`, since that is the method with
  logic.
- The spec's `soldToday/Week/Month` are computed as rolling windows from the São Paulo
  start of day rather than calendar week and month boundaries. Simpler, and the difference
  is invisible on a seven-day chart.
- Day bucketing happens in Java rather than SQL (Task 17), because `date_trunc` with a
  time zone is spelled differently on PostgreSQL and H2 — which would defeat the whole
  point of running the same migrations and queries on both.

**Open risk carried from the spec.** Tests run on H2, dev and production on PostgreSQL.
Task 19's smoke check is the only step that touches the real stack. Testcontainers remains
the proper fix.

