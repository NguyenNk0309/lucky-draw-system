# Lucky Draw microservices

Complete local demo of the supplied architecture using Java 17, Spring Boot, React, MySQL, Redis, and a Kafka-compatible Redpanda broker.

## Run

Docker Desktop with Docker Compose v2 is the only prerequisite.

```bash
docker compose up --build
```

Open <http://localhost:8080>. Stop with `docker compose down`; reset all local application data with `docker compose down -v`.

The application starts with no persisted campaigns, orders, tickets, entries, notifications, or rewards. Shop items are a frontend-only mock catalog.

## UI test flow

| Role | Username | Password |
| --- | --- | --- |
| Customer | `customer` | `customer123` |
| Seller | `seller` | `seller123` |

1. Sign in as `seller` and open **Campaigns**.
2. Create a campaign: enter a name, from/to dates, entry limit such as `2`, reward type, and reward reference. Click **Create draft**, then **Publish**.
3. Sign out, sign in as `customer`, and open **Shop & orders**.
4. Buy a product priced above `1,000,000`. Refresh **My tickets** until an `ISSUED` ticket appears.
5. Open **Submit tickets**, select the active campaign, and click **Submit ticket**. Each consumed ticket creates exactly one entry/slot; there is no customer wheel.
6. The customer receives the submission notification asynchronously and sees it in realtime over WebSocket.
7. Sign out, sign in as `seller`, and open **Winner draw**. Click **End & freeze snapshot**, or wait for the scheduler to close an expired campaign.
8. Click **Spin final draw**. The server securely selects one winner from every submitted ticket in the frozen snapshot and moves the campaign `ENDED → DRAWN`.
9. Use **View winner details** to inspect the selected customer's order summary.
10. The winner receives a realtime notification; reward delivery runs independently through Kafka and appears under **Reward delivery**.

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

Ticket, quota, entry outcome, campaign closing, scheduler, and outbox relay are deliberately combined in the single `lucky-draw-service` module, deployment, and write database. Campaign Service is separate as shown in the diagram, but saves campaign configuration and its outbox event into that same write database. Analytics Read Service and Projector are one deployment around Redis.

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

Login is `POST /auth/login`; use the returned token as `Authorization: Bearer <token>`. Main gateway routes are `GET|POST /api/orders`, `GET /api/tickets`, `GET|POST /api/campaigns`, campaign `activate|cancel|end|draw|entries`, seller customer details, analytics `stats|me`, notifications, rewards, and `/ws/realtime`.

## Correctness

Order creation commits order and `OrderCompleted` outbox row together. It never calls Lucky Draw synchronously. Ticket issuing is idempotent by unique order ID.

Entry submission is one local transaction: `SELECT campaign FOR SHARE`, atomically consume the ticket, reserve campaign/user quota, insert one entry, append `EntrySubmitted`, commit. Redis is updated only by Kafka projection.

Closing takes `SELECT campaign FOR UPDATE`, moves `ACTIVE → ENDED`, freezes ordered snapshot items, and persists their SHA-256 hash in the same transaction. The scheduler uses the same close flow when time expires. A later seller draw locks the campaign again, chooses one snapshot index with `SecureRandom`, records the audit row, moves `ENDED → DRAWN`, and appends exactly one `WinnerPicked`. Replaying draw returns the recorded winner.

Notification consumes both `EntrySubmitted` and `WinnerPicked`; Reward consumes only `WinnerPicked`. Both consumers deduplicate Kafka delivery. Persisted updates are signaled through Kafka to the gateway WebSocket, while the REST lists remain the durable source of truth.

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
