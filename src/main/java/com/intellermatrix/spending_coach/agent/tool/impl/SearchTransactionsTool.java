package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.agent.dto.provider.ToolDefinition;
import com.intellermatrix.spending_coach.agent.dto.tool.SearchResult;
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
public class SearchTransactionsTool implements AgentTool {

    private static final String SCHEMA = """
        {
        "type":"object",
        "properties":{"text":{"type":"string"},"category":{"type":"string"},"from":{"type":"string"},"to":{"type":"string"},"limit":{"type":"integer","maximum":50}}
        }
        """;
    private static final int DEFAULT_LIMIT = 20;

    private final TransactionRepository transactionRepository;

    @Override
    public String name() {
        return ToolCatalog.SEARCH_TRANSACTIONS.id();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(name(), ToolCatalog.SEARCH_TRANSACTIONS.definition(), SCHEMA);
    }

    @Override
    public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
        String text = ToolArguments.optionalString(arguments, "text");
        String categoryArgument = ToolArguments.optionalString(arguments, "category");
        LocalDate from;
        LocalDate to;
        try {
            from = arguments.get("from") != null ? LocalDate.parse((String) arguments.get("from")) : null;
            to = arguments.get("to") != null ? LocalDate.parse((String) arguments.get("to")) : null;
        } catch (DateTimeParseException e) {
            return new ToolExecutionResult(name(), null, null, false,
                "from and to must be ISO yyyy-MM-dd dates: " + e.getMessage());
        }
        int limit = arguments.get("limit") != null ? ((Number) arguments.get("limit")).intValue() : DEFAULT_LIMIT;
        String needle = text != null ? text.toLowerCase(Locale.ROOT) : null;

        List<Transaction> matching = transactionRepository.findByUserId(userId).stream()
            .filter(t -> needle == null || (t.getNote() != null && t.getNote().toLowerCase(Locale.ROOT).contains(needle)))
            .filter(t -> categoryArgument == null || t.getCategory().equals(categoryArgument))
            .filter(t -> from == null || !t.getDate().isBefore(from))
            .filter(t -> to == null || !t.getDate().isAfter(to))
            .sorted(Comparator.comparing(Transaction::getDate).reversed())
            .toList();

        List<TransactionRow> rows = matching.stream().limit(limit).map(TransactionRow::from).toList();
        boolean truncated = matching.size() > limit;
        var result = new SearchResult(rows, matching.size());
        String summary = String.format(Locale.ROOT, "%d of %d rows", rows.size(), matching.size());
        return new ToolExecutionResult(name(), result, summary, truncated, null);
    }
}
