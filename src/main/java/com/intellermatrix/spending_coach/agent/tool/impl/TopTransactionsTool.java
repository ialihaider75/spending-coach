package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.agent.dto.provider.ToolDefinition;
import com.intellermatrix.spending_coach.agent.dto.tool.ToolExecutionResult;
import com.intellermatrix.spending_coach.agent.dto.tool.TransactionRow;
import com.intellermatrix.spending_coach.agent.tool.AgentTool;
import com.intellermatrix.spending_coach.agent.util.ToolArguments;
import com.intellermatrix.spending_coach.enums.ToolCatalog;
import com.intellermatrix.spending_coach.persistence.entity.Transaction;
import com.intellermatrix.spending_coach.persistence.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TopTransactionsTool implements AgentTool {

    private static final String SCHEMA = """
        {
        "type":"object",
        "properties":{"category":{"type":"string"},"from":{"type":"string"},"to":{"type":"string"},"minAmount":{"type":"integer"},"limit":{"type":"integer","maximum":50}}
        }
        """;
    private static final int DEFAULT_LIMIT = 20;

    private final TransactionRepository transactionRepository;

    @Override
    public String name() {
        return ToolCatalog.TOP_TRANSACTIONS.id();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(name(), ToolCatalog.TOP_TRANSACTIONS.definition(), SCHEMA);
    }

    @Override
    public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
        String category = ToolArguments.optionalString(arguments, "category");
        LocalDate from;
        LocalDate to;
        try {
            from = arguments.get("from") != null ? LocalDate.parse((String) arguments.get("from")) : null;
            to = arguments.get("to") != null ? LocalDate.parse((String) arguments.get("to")) : null;
        } catch (DateTimeParseException e) {
            return new ToolExecutionResult(name(), null, null, false,
                "from and to must be ISO yyyy-MM-dd dates: " + e.getMessage());
        }
        Long minAmount = arguments.get("minAmount") != null ? ((Number) arguments.get("minAmount")).longValue() : null;
        int limit = arguments.get("limit") != null ? ((Number) arguments.get("limit")).intValue() : DEFAULT_LIMIT;

        List<Transaction> matching = transactionRepository.findByUserId(userId).stream()
            .filter(t -> category == null || t.getCategory().equals(category))
            .filter(t -> from == null || !t.getDate().isBefore(from))
            .filter(t -> to == null || !t.getDate().isAfter(to))
            .filter(t -> minAmount == null || t.getAmount() >= minAmount)
            .sorted(Comparator.comparingLong(Transaction::getAmount).reversed())
            .toList();

        List<TransactionRow> rows = matching.stream().limit(limit).map(TransactionRow::from).toList();
        boolean truncated = matching.size() > limit;
        String summary = String.format(Locale.ROOT, "%d rows", rows.size());
        return new ToolExecutionResult(name(), rows, summary, truncated, null);
    }
}
