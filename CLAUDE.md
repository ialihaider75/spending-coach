# CLAUDE.md

Project-specific coding rules for whoever (human or Claude) writes code in this repo.
This file is a living document — update it whenever a code-review comment reveals a
rule that isn't obvious from reading the code alone. Keep entries short and concrete;
link to a file/class as the example instead of re-explaining it in prose. Rules only —
no rationale, no history of why a decision was made.

## Lombok

- **JPA entities** (`persistence/entity/*`): `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`. Never `@Data`.
- **`@IdClass` composite-key types** (e.g. `BudgetId`, `AgentStepId`): `@Getter @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor`.
- **Any class whose constructor only assigns `final` fields** (Spring beans or plain
  classes alike): `@RequiredArgsConstructor` instead of a hand-written constructor.
- **Records**: never annotate with Lombok.
- **Utility classes** (private constructor, only static members, e.g. `ConversationKey`): `@UtilityClass` (`lombok.experimental.UtilityClass`) instead of a hand-written private constructor and explicit `static` modifiers.

## Package layout

- `persistence/entity`, `persistence/repository` — split by role, not flat under `domain`.
- `<feature>/service`, `<feature>/dto` — e.g. `ingestion/service`, `ingestion/dto`.
- `controller` — REST endpoints only. No repository/entity imports; delegate to a
  `<feature>/service` for anything beyond wiring the HTTP layer.
- Package names are action-oriented: `ingestion` not `csv`, `generator` not
  `syntheticdata`, `controller` not `web`.
- `enums` — standalone top-level package, not nested inside the entity that uses it.
- A tool's JSON Schema stays in the tool's own implementation class, never in `agent/dto`.

## Testing

- `*Test.java` — unit tests. No Spring context, no Testcontainers.
- `*IT.java` — integration tests. Extend `IntegrationTestSupport`.
- Never assert on a repository's global state (`repository.findAll()`, a bare count
  query). Always scope assertions to the user (or other entity) the test itself created.
- Don't add custom Surefire/Failsafe include/exclude config for the `*Test`/`*IT` split.

## Agent tools

- A tool's `SCHEMA` text block: opening `{` and closing `}` each on their own line, one
  top-level property per line (see `GetOverviewTool`, `SpendingByCategoryTool`).

## Misc style

- Build a message from multiple values with `String.format`, not `+` concatenation.
- `String.format` always takes an explicit `Locale` (`Locale.ROOT` unless told otherwise).
- Magic numbers get a named constant, not a repeated literal.
- A parser/importer processing a batch of inputs never lets one bad input abort the
  whole batch — validate and reject per-item with a reason string.
- Prefer returning a structured error over throwing at API/tool boundaries.
