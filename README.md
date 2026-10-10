# E-Commerce Backend (Spring Boot microservices)

Backend for an online store, built from the PRD in `GIVEN/PRD_ HLD of Ecommerce Website_2.pdf`: user accounts, product catalog with search, cart, checkout, payments and email notifications.
Every service has Swagger UI, and the API gateway shows all of them in one place.

## Services

| Module | Port | Eureka name | Storage | What it does |
|---|---|---|---|---|
| `ServiceRegistry` | 8761 | - | - | Eureka service registry |
| `ApiGateway` | 8080 | `apigateway` | Redis (rate limits) | Single entry point: routing, token check, rate limiting, CORS, combined Swagger UI |
| `UserAuthenticationService` | 8081 | `userservice` | MySQL `userauthservice` | Sign-up, login (JWT access + refresh tokens), Google login, logout, profile, password reset |
| `ProductCatalogService` | 8082 | `productservice` | MySQL `productcatalogservice`, Redis, Elasticsearch | Categories, products, cached product details, typo-tolerant search |
| `CartService` | 8083 | `cartservice` | MongoDB `cartservice`, Redis | Shopping cart |
| `OrderService` | 8084 | `orderservice` | MySQL `orderservice` | Checkout, order history, tracking, cancellation, admin status updates |
| `PaymentService` | 8085 | `paymentservice` | MySQL `paymentservice` | Payment links (Razorpay, Stripe or mock), callbacks, webhooks, receipts |
| `NotificationService` | 8086 | `notificationservice` | - | Sends emails (Gmail or Amazon SES SMTP) and SMS (Twilio) for Kafka events |

### How the services talk to each other

- **REST through Eureka:** services validate tokens with `userservice` (`POST /auth/validateToken`). The cart service reads product prices from `productservice`. The order service reads and clears the cart.
- **Kafka topics:**

| Topic | Producer | Consumers | Purpose |
|---|---|---|---|
| `signup`, `password_reset` | user | notification | Welcome and password reset emails |
| `cart_events` | cart | - | Cart activity stream (for analytics/recommendations) |
| `order_created` | order | payment, cart | Create the payment link; empty the cart |
| `payment_events` | payment | order | `LINK_CREATED`, `SUCCESS` (confirms the order), `FAILED` |
| `order_status_updates` | order | user | Every order status change; feeds the user's order summary (`GET /users/me/order-summary`) |
| `order_notifications` | order | notification | Order confirmation and status emails |
| `payment_notifications` | payment | notification | Payment receipts |
| `sms_notifications` | order, payment | notification | SMS for order confirmation, status changes and payment receipts |

Every service keeps working when an optional dependency is down:

- If Redis is down, requests skip the cache.
- If Elasticsearch is down, search falls back to MySQL.
- If Kafka is down, events are logged instead and checkout creates the payment over REST.

## Prerequisites

- JDK 17 or newer (`JAVA_HOME` set). Maven is not needed; use the included wrapper `mvnw` / `mvnw.cmd`.
- MySQL 8 on `localhost:3306` and MongoDB on `localhost:27017` (or use the MySQL container, see below).
- Docker Desktop for Redis, Kafka and Elasticsearch. On Windows it needs WSL 2: run `wsl --status` to check. If WSL is missing, run these in an admin PowerShell and then restart:

  ```powershell
  dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
  dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
  ```

  Start Docker Desktop and wait for "Engine running" before using `docker compose`.

## Run locally

1. Start Redis, Kafka and Elasticsearch, plus an optional MySQL on port 3307:

   ```powershell
   docker compose up -d
   ```

2. Point the services at MySQL. For your local MySQL:

   ```powershell
   $env:MYSQL_PASSWORD = "<your root password>"
   ```

   Or, to use the MySQL container instead:

   ```powershell
   $env:MYSQL_PORT = "3307"; $env:MYSQL_PASSWORD = "12345"
   ```

   Databases are created automatically on first start. Environment variables only apply to the terminal they are set in, so set them in every terminal you start a service from.

3. Build everything once from the project root:

   ```powershell
   .\mvnw.cmd -B package
   ```

4. Start the services, each in its own terminal, in this order: registry first, then the others, then the gateway.

   ```powershell
   java -jar ServiceRegistry\target\ServiceRegistry-0.0.1-SNAPSHOT.jar
   java -jar UserAuthenticationService\target\UserAuthService-0.0.1-SNAPSHOT.jar
   java -jar ProductCatalogService\target\ProductCatalogService-0.0.1-SNAPSHOT.jar
   java -jar CartService\target\CartService-0.0.1-SNAPSHOT.jar
   java -jar OrderService\target\OrderService-0.0.1-SNAPSHOT.jar
   java -jar PaymentService\target\PaymentService-0.0.1-SNAPSHOT.jar
   java -jar NotificationService\target\NotificationService-0.0.1-SNAPSHOT.jar
   java -jar ApiGateway\target\ApiGateway-0.0.1-SNAPSHOT.jar
   ```

   To start them all from a single terminal instead, run them in the background with their logs in `%TEMP%\ecom-logs`:

   ```powershell
   $logs = "$env:TEMP\ecom-logs"; New-Item -ItemType Directory -Force $logs | Out-Null
   function Start-Jar($name, $jar) { Start-Process java -ArgumentList "-jar", $jar -RedirectStandardOutput "$logs\$name.log" -RedirectStandardError "$logs\$name.err" -WindowStyle Hidden }
   Start-Jar registry ServiceRegistry\target\ServiceRegistry-0.0.1-SNAPSHOT.jar
   Start-Sleep 20
   Start-Jar user UserAuthenticationService\target\UserAuthService-0.0.1-SNAPSHOT.jar
   Start-Jar product ProductCatalogService\target\ProductCatalogService-0.0.1-SNAPSHOT.jar
   Start-Jar cart CartService\target\CartService-0.0.1-SNAPSHOT.jar
   Start-Jar order OrderService\target\OrderService-0.0.1-SNAPSHOT.jar
   Start-Jar payment PaymentService\target\PaymentService-0.0.1-SNAPSHOT.jar
   Start-Jar notification NotificationService\target\NotificationService-0.0.1-SNAPSHOT.jar
   Start-Jar gateway ApiGateway\target\ApiGateway-0.0.1-SNAPSHOT.jar
   ```

   All services are ready once they show up on the Eureka dashboard (about a minute).

   During development you can also run `.\mvnw.cmd -f <Module>\pom.xml spring-boot:run` or start the `*Application` class from the IDE.

5. Open:
   - Swagger UI for all services: <http://localhost:8080/swagger-ui.html> (switch services with the dropdown at the top right).
   - Eureka dashboard: <http://localhost:8761>.
   - Each service's own Swagger UI: `http://localhost:<port>/swagger-ui.html`.

## Using the API (Swagger walkthrough)

On first start the user service creates an admin account: `admin@ecommerce.local` / `Admin@123`. You can change it with `ADMIN_EMAIL` / `ADMIN_PASSWORD`. The product service seeds 4 categories and 9 products (disable this with `SEED_DATA=false`).

1. **User & Authentication:** call `POST /auth/signup`, then `POST /auth/login`. Copy `accessToken` from the response, click **Authorize** and paste it. The token works in every service's page.
2. **Product Catalog:** use `GET /products`, `GET /categories`, `GET /search?q=iphne` (typos are tolerated) or `POST /search` (filters, sorting, paging).
3. **Cart:** use `POST /cart/items` with `{"productId": 1, "quantity": 2}`, then `GET /cart`.
4. **Orders:** call `POST /orders/checkout` with a delivery address and a payment method. The response has `paymentLink`.
5. Open the `paymentLink` in a browser and pay:
   - With the **mock gateway** (used when no Razorpay keys are configured), opening the link completes the payment. Add `?outcome=failure` to simulate a failed payment.
   - With **Razorpay test mode**, the payment page opens on the method chosen at checkout (card, net banking, UPI or wallet). Pay with Razorpay's test details. Razorpay then redirects to `/payments/callback/razorpay`, which verifies the signature.
6. The order becomes `CONFIRMED`. Check it with `GET /orders/{id}/tracking`. The receipt is at `GET /payments/{id}/receipt`. The confirmation and receipt emails and SMS show up in the notification service's log, or are delivered when SMTP/Twilio is configured. `GET /users/me/order-summary` now counts the order.
7. **Admin:** log in as the admin and move the order with `PUT /orders/{id}/status`. The allowed order is CONFIRMED, then PACKED, SHIPPED, OUT_FOR_DELIVERY and finally DELIVERED. Admins can also manage products and categories, rebuild the search index with `POST /search/reindex`, send test messages with `POST /notifications/email` and `POST /notifications/sms`, and list recent notifications with `GET /notifications`.

### Checking the Kafka flow

After a checkout and payment, every consumer group should show `LAG` 0:

```powershell
docker exec ecommerce-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups
```

A warning like `UNKNOWN_TOPIC_OR_PARTITION` on the first start is normal. Topics are created when the first message is sent.

### Stopping

```powershell
Get-Process java | Stop-Process          # stops every Java process, including any IDE-run services
docker compose --profile edge down       # add -v to also delete the MySQL and Elasticsearch data
```

## Configuration

All settings have local defaults. Override them with environment variables.

| Variable | Used by | Default | Notes |
|---|---|---|---|
| `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USERNAME`, `MYSQL_PASSWORD` | user, product, order, payment | `localhost`, `3306`, `root`, `12345` | |
| `MONGODB_URI` | cart | `mongodb://localhost:27017/cartservice` | |
| `REDIS_HOST`, `REDIS_PORT` | gateway, product, cart | `localhost`, `6379` | |
| `KAFKA_BOOTSTRAP_SERVERS` | user, cart, order, payment, notification | `localhost:9092` | |
| `ELASTICSEARCH_URL` | product | `http://localhost:9200` | |
| `EUREKA_URL` | all | `http://localhost:8761/eureka/` | |
| `JWT_SECRET` | user | development value | Use 32+ random characters in production |
| `JWT_ACCESS_TOKEN_VALIDITY_SECONDS` / `JWT_REFRESH_TOKEN_VALIDITY_SECONDS` | user | `3600` / `604800` | |
| `GOOGLE_CLIENT_ID` | user | empty | Needed for `POST /auth/social-login` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | user | `admin@ecommerce.local`, `Admin@123` | Admin account created on first start |
| `SEED_DATA` | product | `true` | Adds sample categories and products on first start |
| `GATEWAY_URL` | user, product, cart, order, payment, notification | `http://localhost:8080` | Server listed in each service's Swagger page for "Try it out" |
| `PAYMENT_GATEWAY` | payment | `auto` | `auto` uses Razorpay when keys are set, otherwise mock; or `razorpay`, `stripe`, `mock` |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | payment | empty | Test keys from the Razorpay dashboard (Test Mode) |
| `RAZORPAY_WEBHOOK_SECRET` | payment | empty | Needed for `POST /payments/webhook/razorpay` |
| `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET` | payment | empty | When `PAYMENT_GATEWAY=stripe` |
| `PAYMENT_CALLBACK_BASE_URL` | payment | `http://localhost:8080` | Where the browser returns after paying; use a public URL (e.g. ngrok) for webhooks |
| `MAIL_HOST`, `MAIL_PORT` | notification | `smtp.gmail.com`, `587` | Amazon SES: `email-smtp.<region>.amazonaws.com` |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | notification | empty | Gmail address + app password, or SES SMTP credentials. Empty means emails are only logged |
| `MAIL_FROM` | notification | `MAIL_USERNAME` | Must be a verified sender for SES |
| `SMS_PROVIDER` | notification | `log` | `log` only logs SMS; `twilio` sends them (falls back to logging without credentials) |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` | notification | empty | From the Twilio console |
| `SMS_DEFAULT_COUNTRY_CODE` | notification | `+91` | Added to phone numbers given without a country code |
| `RATE_LIMIT_ENABLED` | gateway | `true` | 20 requests/second per client IP, bursts up to 40 |
| `CORS_ALLOWED_ORIGINS` | gateway | `http://localhost:*` | Front-end origins allowed to call the API |

## Optional: load balancer and Kong (HLD edge layer)

The HLD puts a load balancer and Kong in front of the services. The `edge` profile in `docker-compose.yml` reproduces that locally. Kong listens on port 8000. It spreads requests round-robin across two API gateway instances, on ports 8080 and 8090, and stops sending to an instance whose `/actuator/health` check fails. Kong also applies per-IP rate limiting and adds an `X-Request-Id` header (config in `kong/kong.yml`).

1. Start Kong along with the other containers:

   ```powershell
   docker compose --profile edge up -d
   ```

2. Run two gateway instances. Kong now does the per-IP rate limiting, so turn it off in the gateways (behind Kong they would only see Kong's address):

   ```powershell
   $env:RATE_LIMIT_ENABLED = "false"
   java -jar ApiGateway\target\ApiGateway-0.0.1-SNAPSHOT.jar
   java -jar ApiGateway\target\ApiGateway-0.0.1-SNAPSHOT.jar --server.port=8090
   ```

3. Send clients to `http://localhost:8000`. Set `GATEWAY_URL=http://localhost:8000` on the services so Swagger's "Try it out" goes through Kong, and `PAYMENT_CALLBACK_BASE_URL=http://localhost:8000` on the payment service.

4. Check that Kong sees both gateways as healthy:

   ```powershell
   (Invoke-RestMethod http://localhost:8001/upstreams/api-gateway-cluster/health).data | Select-Object target, health
   ```

   If you stop one gateway, Kong marks it `UNHEALTHY` within a few seconds and sends all requests to the other one. Once it is back up, Kong marks it `HEALTHY` again. More than 20 requests per second from one IP get `429 Too Many Requests`.

In AWS the same role is played by an Elastic Load Balancer in front of Kong or the gateway instances.

## Project layout

Each service follows the same structure as the original services:

```
controllers/      REST endpoints + ControllerAdvisor (error responses)
services/         I<Name>Service interfaces and implementations
repos/            Spring Data repositories
models/           entities / documents (BaseModel with id, createdAt, lastUpdatedAt, state)
dtos/             request/response objects
clients/          calls to other services, Kafka producer, cache clients
consumers/        Kafka listeners
config/           Swagger (OpenApiConfig), RestTemplate, other beans
utils/mappers/    entity <-> DTO mapping
```

## Tests

```powershell
.\mvnw.cmd -B test
```

Unit tests cover:

- authentication and the user's order summary;
- products and the cart;
- checkout, order status rules, status events and SMS;
- payments: link creation, Razorpay request and chosen payment method, idempotency, signature checks and receipts;
- email/SMS notifications;
- the gateway's public-path rules.
