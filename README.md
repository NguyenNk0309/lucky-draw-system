# Lucky Draw microservices

Complete local demo of the supplied architecture using Java 17, Spring Boot, React, MySQL, Redis, and a Kafka-compatible Redpanda broker.

## Run

Docker Desktop with Docker Compose v2 is the only prerequisite.

```bash
docker compose up --build
```

Open <http://localhost:8080>. Stop with `docker compose down`; reset demo data with `docker compose down -v`.

## Demo login and workflow

| Role | Username | Password |
| --- | --- | --- |
| Customer | `customer` | `customer123` |
| Seller | `seller` | `seller123` |

Customer workflow: sign in, buy a product over ₫1,000,000, wait briefly for the Kafka-issued ticket, open **Lucky wheel**, and spin with that ticket. The customer spin submits an entry; it does not choose a winner. This preserves the architecture’s `submit entry → view result` contract.

Seller workflow: sign in, create and publish a campaign, monitor Analytics, end/freeze the campaign, then spin the final wheel. The Lucky Draw Service securely selects the winner from the frozen MySQL snapshot. The customer then sees the notification and reward.

## Exact diagram mapping

| Diagram component | Implementation / deployment |
| --- | --- |
| Customer App + Seller/Admin Portal | `frontend` React application |
| API Gateway: auth, rate limit, routing | `api-gateway` Spring Boot service |
| Order Service + Order Outbox Relay | `order-service` |
| Order DB + outbox table | MySQL `orders` schema |
| Campaign Service | separate `campaign-service` |
| Lucky Draw Write Service | `lucky-draw-service` from Gradle module `lucky-draw-write` |
| Campaign Scheduler | internal scheduled component in `lucky-draw-service` |
| Lucky Draw Outbox Relay | internal scheduled component in `lucky-draw-service` |
| Write DB: campaign/ticket/quota/entry/outbox/snapshot | MySQL `luckydraw` schema shared by Campaign and Lucky Draw services |
| Analytics Read Service + Analytics Projector | `analytics-service` |
| Read Model DB | Redis |
| Message broker | Redpanda Kafka API |
| Notification Service | `notification-service` |
| Reward Delivery strategies | `reward-service` with Product/Coupon strategies |

Ticket, quota, entry, draw, scheduler, and outbox relay are deliberately combined in one Lucky Draw deployment and one write database. Campaign Service is separate as shown in the diagram, but saves campaign configuration and its outbox event into that same write database. Analytics Read Service and Projector are one deployment around Redis.

## URLs

| Component | URL |
| --- | --- |
| Web application | <http://localhost:8080> |
| API Gateway health | <http://localhost:18080/actuator/health> |
| Order OpenAPI | <http://localhost:18081/swagger-ui.html> |
| Lucky Draw OpenAPI | <http://localhost:18082/swagger-ui.html> |
| Campaign OpenAPI | <http://localhost:18083/swagger-ui.html> |
| Analytics OpenAPI | <http://localhost:18085/swagger-ui.html> |
| MySQL / Redis / Kafka | `3307` / `6380` / `19092` |

Login is `POST /auth/login`; use the returned token as `Authorization: Bearer <token>`. Main gateway routes are `GET|POST /api/orders`, `GET /api/tickets`, `GET|POST /api/campaigns`, campaign `activate|cancel|end|entries|draw`, analytics `stats|me`, notifications, and rewards.

## Correctness

Order creation commits order and `OrderCompleted` outbox row together. It never calls Lucky Draw synchronously. Ticket issuing is idempotent by unique order ID.

Entry submission is one local transaction: `SELECT campaign FOR SHARE`, atomically consume ticket, reserve campaign/user quota, insert entry, append `EntrySubmitted`, commit. Redis is updated only by Kafka projection. Closing takes the exclusive campaign lock and freezes ordered snapshot items. Draw uses `SecureRandom`, records selected index and SHA-256 snapshot hash, moves `ENDED → DRAWN`, and appends `WinnerPicked` atomically. Repeated draw returns the persisted winner.

Analytics, notification, and reward consumers independently deduplicate events. Reward claims are persisted before local Product/Coupon delivery.

## Verification

```bash
docker run --rm -v "$PWD:/workspace" -w /workspace gradle:8.10.2-jdk17-alpine gradle test bootJar --no-daemon
cd frontend
npm ci
npm test
npm run lint
npm run format
npm run build
```

After the stack is healthy:

```powershell
.\deploy\smoke-test.ps1
```

The repeatable smoke test creates its own campaign and orders, so existing demo data is preserved.
