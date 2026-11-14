# Spending Coach

A tool-calling AI agent that investigates a user's spending history to answer open-ended money questions — a hand-written agent loop over a Postgres transaction store, not a chat wrapper around an LLM. Full design: `local/superpowers/specs/2026-11-12-spending-coach-agent-design.md` (only readable from a local checkout — `local/` is gitignored and won't render on GitHub).

This README reflects what exists right now, after the foundation milestone's infra task. It will grow as CSV import, the synthetic data generator, the tool layer, and the agent loop land.

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
