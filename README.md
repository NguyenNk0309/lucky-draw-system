# Lucky Draw microservices

Complete local demo of the supplied architecture using Java 17, Spring Boot, React, MySQL, Redis, and a Kafka-compatible Redpanda broker.

## Run

Docker Desktop with Docker Compose v2 is the only prerequisite.

```bash
docker compose up --build
```

Open <http://localhost:8080>. Stop with `docker compose down`; reset all local application data with `docker compose down -v`.

The application starts with no campaigns, orders, tickets, entries, notifications, or rewards.

## UI test flow

| Role | Username | Password |
| --- | --- | --- |
| Customer | `customer` | `customer123` |
| Seller | `seller` | `seller123` |

1. Sign in as `seller` and open **Campaigns**.
2. Create a campaign: enter a name, duration such as `30`, entry limit such as `2`, reward type, and reward reference. Click **Create draft**, then **Publish**.
3. Sign out, sign in as `customer`, and open **Orders**.
4. Create an order with a total greater than `1,000,000`. Refresh **My tickets** until an `ISSUED` ticket appears.
5. Open **Lucky wheel**, select the active campaign, and click **Spin with ticket**. The customer wheel submits an entry; it does not select the winner.
6. Sign out, sign in as `seller`, open **Analytics**, and refresh to see the projected entry.
7. Open **Lucky wheel**, select the campaign, click **End & freeze snapshot**, then **Spin final draw**.
8. Sign out, sign in as `customer`, open **Lucky wheel**, and verify the result, winner notification, and reward.

## Exact diagram mapping

| Diagram component | Implementation / deployment |
| --- | --- |
| Customer App + Seller/Admin Portal | `frontend` React application |
| API Gateway: auth, rate limit, routing | `api-gateway` Spring Boot service |
| Order Service + Order Outbox Relay | `order-service` |
| Order DB + outbox table | MySQL `orders` schema |
| Campaign Service | separate `campaign-service` |
| Lucky Draw Write Service + Scheduler + Relay | `lucky-draw-service` |
| Campaign Scheduler | internal scheduled component in `lucky-draw-service` |
| Lucky Draw Outbox Relay | internal scheduled component in `lucky-draw-service` |
| Write DB: campaign/ticket/quota/entry/outbox/snapshot | MySQL `luckydraw` schema shared by Campaign and Lucky Draw services |
| Analytics Read Service + Analytics Projector | `analytics-service` |
| Read Model DB | Redis |
| Message broker | Redpanda Kafka API |
| Notification Service | `notification-service` |
| Reward Delivery strategies | `reward-service` with Product/Coupon strategies |

Ticket, quota, entry, draw, scheduler, and outbox relay are deliberately combined in the single `lucky-draw-service` module, deployment, and write database. Campaign Service is separate as shown in the diagram, but saves campaign configuration and its outbox event into that same write database. Analytics Read Service and Projector are one deployment around Redis.

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

The repeatable smoke test creates its own isolated campaign and orders.
