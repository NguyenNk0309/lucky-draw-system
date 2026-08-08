# Codex Build Instructions — Lucky Draw Microservices Application

Build the complete Lucky Draw application in the CURRENT WORKING DIRECTORY.

## IMPORTANT

- Read these files completely before writing code:
  - `lucky-draw-solution-proposal.md`
  - `lucky-draw-architecture.png`
- Treat the proposal and architecture diagram as the source of truth.
- Do not simplify or replace architectural decisions from the proposal merely because another implementation would be easier.
- Where this instruction conflicts with the proposal's "minimal demo" scope, THIS instruction wins: implement the complete locally runnable application.

---

## 1. GOAL

Implement a production-style Lucky Draw system following the supplied solution proposal and architecture diagram.

### Backend

- Java 17
- Spring Boot
- Gradle multi-project build using Kotlin DSL if consistent with proposal
- MySQL 8 for write-side transactional data
- Redis for CQRS read model
- Kafka-compatible broker for asynchronous events
- Flyway for database migrations

### Frontend

- React
- TypeScript
- Vite
- React Router
- Use a conventional maintainable component/service structure
- Use a common API-state library such as TanStack Query if useful

### Infrastructure

- Docker
- Docker Compose
- Everything necessary to run the application locally must be containerized.

I should be able to start the complete system with:

```bash
docker compose up --build
```

and stop it with:

```bash
docker compose down
```

Do not require locally installed Java, Gradle, Node, MySQL, Redis or Kafka other than Docker/Docker Compose.

---

## 2. ARCHITECTURE MUST FOLLOW THE PROPOSAL

Implement these patterns as described in the proposal:

1. Event-Driven Architecture
2. CQRS
3. Transactional Outbox
4. Strategy Pattern for reward delivery
5. DDD-style bounded-context/service boundaries
6. Idempotent event consumers
7. Local ACID transaction for ticket + quota + entry
8. Eventually consistent analytics read model

Important architectural constraint:

**DO NOT split Ticket, Quota and Entry into independent databases/services.**

They belong to the same Lucky Draw write bounded context and must use the same MySQL database because submitting an entry must remain one local transaction.

The write side must NEVER update Redis/read-model state directly. Changes reach the read model only through Kafka events and the projector.

Do not introduce:

- Event sourcing
- Saga orchestration for submit-entry
- Distributed Redis locks around entry submission
- One-microservice-per-entity architecture

unless the proposal explicitly requires them.

---

## 3. PROJECT STRUCTURE

Follow the structure from the proposal as closely as possible:

```text
lucky-draw/
├── common-events/
├── lucky-draw-write/
├── lucky-draw-relay/
├── lucky-draw-scheduler/
├── analytics-service/
├── notification-service/
├── reward-service/
├── frontend/
├── deploy/
│   └── docker-compose.yml
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
└── ...
```

Inside Spring Boot applications follow the package separation from the proposal:

```text
api/
events/
service/
domain/
domain/port/
infrastructure/
```

Domain packages must not depend on Spring, Kafka, JDBC, Redis or other infrastructure packages where the proposal says they should not.

Repository interfaces belong in `domain/port`.
Infrastructure implementations depend inward on those interfaces.

Do NOT make Spring Boot application modules depend directly on each other.
Shared event contracts belong in `common-events`.

---

## 4. FUNCTIONAL REQUIREMENTS

### A. Campaign management

Seller/Admin can:

- create campaign
- configure:
  - name
  - startAt
  - endAt
  - maxEntriesPerUser
  - reward type
  - reward reference
- publish/activate campaign
- view campaigns
- view campaign details
- draw winner when eligible

Campaign state machine:

```text
DRAFT -> ACTIVE -> ENDED -> DRAWN
```

Support `CANCELLED` where specified by the proposal.

Respect all allowed state transitions.

### B. Order simulation

The proposal treats Order Service as an existing external service.

For the standalone local application, provide a minimal/mock Order Service or clearly isolated local simulator so the complete flow can be tested.

Customer can create an order.

Orders above the proposal threshold must generate `OrderCompleted` using a transactional outbox.

Checkout/order creation must NOT synchronously call Lucky Draw Write Service.

### C. Ticket issuing

Consume `OrderCompleted`.

Issue exactly one ticket per qualifying order.

Use database constraints/idempotency so replaying `OrderCompleted` cannot create duplicate tickets.

Customer must be able to view their available tickets through an appropriate API/UI for the standalone demo.

### D. Entry submission

Implement:

```text
POST /campaigns/{id}/entries
```

The transaction must follow the proposal:

1. `SELECT campaign ... FOR SHARE`
2. verify campaign is ACTIVE and within time window
3. atomically consume ticket
4. atomically reserve user quota
5. insert entry
6. insert `EntrySubmitted` into outbox
7. COMMIT

All writes above belong to ONE MySQL transaction.

Return distinguishable 409 responses for:

- campaign closed
- ticket invalid/already consumed
- entry quota reached

Concurrency correctness is more important than application-level pre-checks.

### E. Transactional outbox

Implement outbox table and relay.

Relay must:

- poll unpublished rows
- publish to Kafka
- mark them published
- support at-least-once delivery
- be safe if the process crashes/restarts
- preserve event IDs

Implement events including:

- `OrderCompleted`
- `EntrySubmitted`
- `WinnerPicked`

Include useful metadata such as:

- eventId
- eventType
- occurredAt
- aggregateId
- correlationId where appropriate

### F. Campaign scheduler

Implement scheduler that moves eligible ACTIVE campaigns to ENDED.

Correctly handle the submit-vs-close race described in the proposal.

Freeze/build the draw snapshot as required by the proposal.

### G. Winner draw

Implement:

```text
POST /campaigns/{id}/draw
```

Rules:

- campaign must be ENDED
- draw from the frozen write-side snapshot
- do not determine the winner from Redis/read model
- use cryptographically secure randomness
- persist draw audit information
- persist snapshot hash
- update ENDED -> DRAWN atomically
- create `WinnerPicked` outbox event in same transaction
- repeated draw request must NEVER select a new winner

Return the existing winner for an already-drawn campaign where appropriate.

### H. CQRS Analytics

Consume:

- `EntrySubmitted`
- `WinnerPicked`

Maintain Redis read model.

Provide APIs including:

```text
GET /campaigns/{id}/stats
GET /campaigns/{id}/me
```

Stats should include appropriate values such as:

- total entries
- distinct participants
- winner/result if available
- lastUpdatedAt

Expose `lastUpdatedAt` because projection is eventually consistent.

Consumers must be idempotent.

Duplicate Kafka delivery must not increment counters twice.

### I. Notification Service

Consume `WinnerPicked` independently.

Implement a local/demo notification provider rather than requiring an external paid service.

For example:

- log/store notification
- expose it for the frontend/demo if useful

Keep provider behind an interface so email/SMS/push could replace it.

### J. Reward Service

Consume `WinnerPicked` independently.

Implement Strategy:

```text
RewardStrategy
├── ProductReward
└── CouponReward
```

Reward delivery must be idempotent.

Create/persist reward claim before external delivery as described in the proposal.

Duplicate `WinnerPicked` delivery must NEVER create two rewards.

Provide local mock implementations for fulfillment/coupon systems.

---

## 5. FRONTEND

Build a usable React frontend, not just placeholder pages.

Provide two logical areas.

### Customer UI

- create/demo order
- view issued tickets
- list/view active campaigns
- submit entry using ticket
- view own entries
- view remaining quota
- view campaign result
- see whether they won
- view notification/reward status where practical

### Seller/Admin UI

- create campaign
- configure campaign
- publish campaign
- list campaigns
- view campaign stats
- see `lastUpdatedAt`
- end/wait for campaign end
- trigger draw
- see winner and snapshot hash

The UI does not need elaborate visual design but must be clean, responsive, consistent and usable.

Add loading, empty and error states.

Display meaningful API error messages, especially the three different 409 entry-submission failures.

---

## 6. AUTHENTICATION

The architecture diagram shows authentication/authorization at the gateway, but the proposal does not require integration with a real identity provider.

For local development implement a simple, clearly isolated demo-auth solution.

Support at least:

- CUSTOMER
- SELLER

Do not spend disproportionate effort implementing OAuth infrastructure unless already required by the proposal.

Make replacing demo auth later straightforward.

Do not allow one seller to manage another seller's campaign.

---

## 7. DATABASE

Use Flyway migrations.

Implement schema constraints/invariants from the proposal rather than relying only on Java checks.

In particular preserve:

- unique `order_id` for ticket
- ticket consumed at most once
- campaign/user quota uniqueness
- one entry per ticket
- stable entry `seq`
- draw snapshot items
- outbox
- processed event deduplication
- reward claim uniqueness
- relevant indexes

Do not silently change concurrency semantics defined in the proposal.

---

## 8. DOCKER COMPOSE

Create a root-level `docker-compose.yml` or make the root command work even if the canonical file is under `deploy/`.

```bash
docker compose up --build
```

must start everything needed.

Include at least:

- frontend
- gateway/API entry point if implemented as a separate process
- local order service/simulator
- lucky-draw-write
- lucky-draw-relay
- lucky-draw-scheduler
- analytics-service/projector
- notification-service
- reward-service
- MySQL
- Redis
- Kafka-compatible broker

Add health checks.

Use service names instead of `localhost` for container-to-container networking.

Use persistent named volumes where appropriate.

Use environment variables for configuration.

Provide sensible defaults so no `.env` file is required for first startup.

Avoid host-port conflicts where possible.

---

## 9. DEVELOPMENT DATA

Provide deterministic local/demo seed data.

Also document how to reset the system.

Seed data should make it easy to reproduce the proposal's demonstration:

- campaign with `maxEntriesPerUser = 2`
- qualifying/non-qualifying orders
- ticket issuance
- successful entries
- quota failure
- ticket-reuse failure
- draw
- duplicate-event/idempotency scenario

Do not make startup dependent on manually entering database rows.

---

## 10. TESTING

Do NOT consider a feature complete only because it compiles.

Add tests at appropriate levels.

### Unit tests

- campaign business rules
- reward strategies
- validation/state transitions
- domain behavior

### Repository/integration tests

- MySQL conditional updates
- ticket idempotency
- quota concurrency
- entry transaction rollback
- draw idempotency
- snapshot creation
- outbox behavior

### Messaging/integration tests

- duplicate `OrderCompleted`
- duplicate `EntrySubmitted`
- duplicate `WinnerPicked`
- projector deduplication
- reward deduplication

### API tests

- campaign endpoints
- entry success/error cases
- stats/result endpoints
- draw endpoint

### Frontend tests

- important component/workflow tests

End-to-end test the major scenario.

Where appropriate use Testcontainers or equivalent.

IMPORTANT concurrency test:

Run multiple concurrent entry requests for the same campaign/user and prove that `maxEntriesPerUser` is never exceeded.

Also test submit racing with campaign close.

---

## 11. VERIFICATION BEFORE COMPLETION

Before saying the implementation is complete:

1. Run backend unit/integration tests.
2. Run frontend tests.
3. Build all Gradle modules.
4. Build frontend production bundle.
5. Run `docker compose build`.
6. Start complete stack using Docker Compose.
7. Wait for health checks.
8. Execute an end-to-end smoke test through the running containers.
9. Verify Redis projection catches up.
10. Verify duplicate events do not duplicate counters/rewards.
11. Verify second draw does not change winner.
12. Check logs for unexpected errors.

If something fails:

- debug it
- fix it
- rerun the relevant tests

Do not leave known failing tests and describe the project as complete.

---

## 12. GIT WORKFLOW

Initialize a LOCAL git repository in this directory if one does not already exist.

Do NOT configure or push to any remote repository.

Make atomic commits after each COMPLETE, WORKING feature.

Suggested progression:

```text
chore: initialize gradle multi-project structure
feat: add common event contracts
feat: implement campaign management
feat: add local order service and transactional outbox
feat: implement ticket issuance
feat: implement transactional entry submission
feat: add lucky draw outbox relay
feat: add campaign scheduler and snapshots
feat: implement idempotent winner draw
feat: implement analytics projection and read APIs
feat: implement notification consumer
feat: implement reward delivery strategies
feat: add customer frontend
feat: add seller frontend
feat: add docker compose environment
test: add integration and concurrency coverage
docs: add setup and architecture documentation
```

Before every commit:

- run formatter
- run relevant tests
- inspect `git diff`

Never commit a feature with known failing tests.

Do not squash the final history.
Keep the feature-by-feature commits so the implementation history is visible.

---

## 13. CODE QUALITY

Prioritize readable and maintainable code.

### Java

- consistent formatting
- meaningful names
- small focused classes/methods
- avoid unnecessary abstractions
- constructor injection
- clear transaction boundaries

### TypeScript/React

- Prettier
- ESLint
- strict TypeScript
- reusable components where useful
- no giant page components
- separate API access from presentation components

Do not add abstractions simply for the sake of patterns.

Add comments mainly where explaining:

- concurrency
- transaction boundaries
- idempotency
- architectural decisions

not obvious syntax.

---

## 14. DOCUMENTATION

Create a useful `README.md` containing:

- architecture overview
- project/module structure
- prerequisites
- exact startup command
- exact shutdown command
- URLs/ports
- demo users/roles
- sample workflow
- API documentation location
- how to run tests
- how to reset data
- service responsibilities
- event flows
- eventual consistency explanation
- important concurrency/idempotency decisions
- known local-demo substitutions for external systems

Provide OpenAPI/Swagger documentation for HTTP APIs.

---

## 15. WORKING STYLE

Work autonomously.

Do not stop after scaffolding.
Do not generate TODO-only implementations.
Do not leave core flows mocked unless they represent external systems that the proposal explicitly treats as external.

When a detail is not specified by the proposal:

1. choose a common, boring, maintainable solution;
2. document the choice in README;
3. continue without asking me unless the decision would fundamentally alter the architecture.

Before coding, inspect the full proposal and diagram and create a short implementation plan/checklist.

Then implement the system feature by feature until the entire application is running and tested.

**Do not merely describe what should be built. Build it.**
