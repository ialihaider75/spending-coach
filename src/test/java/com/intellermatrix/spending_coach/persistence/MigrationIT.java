package com.intellermatrix.spending_coach.persistence;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MigrationIT extends IntegrationTestSupport {

    @Test
    void migrationCreatesExpectedTables(@Autowired JdbcTemplate jdbc) {
        List<String> tables = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains(
            "users", "agent_runs", "agent_steps", "transactions", "budgets", "budget_proposals");
    }

    @Test
    void transactionsTableHasRequiredIndexes(@Autowired JdbcTemplate jdbc) {
        List<String> indexes = jdbc.queryForList(
            "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'transactions'",
            String.class);

        assertThat(indexes).contains("idx_transactions_user_date", "idx_transactions_user_category_date");
    }
}
