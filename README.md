# Spending Coach

> **🚧 Under active development.** Nothing here is stable yet — schema, package layout, and this README are all still moving as each milestone lands.

A tool-calling AI agent that investigates a user's spending history to answer open-ended money questions — a hand-written agent loop over a Postgres transaction store, not a chat wrapper around an LLM.

## Architecture

This section documents how the system is actually put together as it gets built — right now that's just the database shape (below), but it will grow to cover the API surface, the agent loop, and the provider layer as those milestones land.

### ERD

The schema below is created by `src/main/resources/db/migration/V1__initial_schema.sql` and mapped by the JPA entities in `persistence/entity`. Every table but `users` is scoped to a user via a `user_id` foreign key; `agent_steps` belongs to one `agent_runs` row, and a `budget_proposals` row optionally records which `agent_runs` produced it.

```mermaid
erDiagram
    USERS ||--o{ TRANSACTIONS : "owns"
    USERS ||--o{ BUDGETS : "owns"
    USERS ||--o{ BUDGET_PROPOSALS : "owns"
    USERS ||--o{ AGENT_RUNS : "owns"
    AGENT_RUNS ||--o{ AGENT_STEPS : "has"
    AGENT_RUNS |o--o{ BUDGET_PROPOSALS : "may produce"

    USERS {
        UUID id PK
        string display_name
        timestamp created_at
    }
    TRANSACTIONS {
        UUID id PK
        UUID user_id FK
        date date
        bigint amount
        string kind
        string category
        string note
        string source
    }
    BUDGETS {
        UUID user_id PK, FK
        string category PK
        bigint monthly_limit
    }
    BUDGET_PROPOSALS {
        UUID id PK
        UUID user_id FK
        jsonb changes
        string status
        UUID run_id FK
    }
    AGENT_RUNS {
        UUID id PK
        UUID user_id FK
        text question
        string provider
        string status
        int total_tokens
        int total_latency_ms
        timestamp created_at
    }
    AGENT_STEPS {
        UUID run_id PK, FK
        int step_number PK
        string tool
        jsonb arguments
        text result_summary
        int latency_ms
        int tokens
        string error
    }
```

## Prerequisites

- Java 21
- Docker (for Postgres/Redis locally, and for the Testcontainers-backed integration tests)

## Running locally

Start Postgres and Redis:

```bash
docker compose up -d
```

Run the app:

```bash
./mvnw spring-boot:run
```

## Running tests

Unit tests only (`*Test.java`, no Spring context, no containers):

```bash
./mvnw test
```

Full suite, including Testcontainers-backed integration tests (`*IT.java`) — requires Docker running:

```bash
./mvnw verify
```
