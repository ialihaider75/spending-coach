package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.agent.dto.provider.ToolDefinition;
import com.intellermatrix.spending_coach.agent.dto.tool.CategoryBreakdown;
import com.intellermatrix.spending_coach.agent.dto.tool.ToolExecutionResult;
import com.intellermatrix.spending_coach.agent.tool.AgentTool;
import com.intellermatrix.spending_coach.enums.Kind;
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
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SpendingByCategoryTool implements AgentTool {

    private static final String SCHEMA = """
        {
        "type":"object",
        "properties":{"from":{"type":"string"},"to":{"type":"string"}},
        "required":["from","to"]
        }
        """;
    private static final double PERCENT_MULTIPLIER = 100.0;

    private final TransactionRepository transactionRepository;

    @Override
    public String name() {
        return ToolCatalog.SPENDING_BY_CATEGORY.id();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(name(), ToolCatalog.SPENDING_BY_CATEGORY.definition(), SCHEMA);
    }

    @Override
    public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse((String) arguments.get("from"));
            to = LocalDate.parse((String) arguments.get("to"));
        } catch (DateTimeParseException e) {
            return new ToolExecutionResult(name(), null, null, false,
                "from and to must be ISO yyyy-MM-dd dates: " + e.getMessage());
        }

        List<Transaction> expenses = transactionRepository.findByUserIdAndDateBetween(userId, from, to).stream()
            .filter(t -> t.getKind() == Kind.EXPENSE)
            .toList();
        long totalExpense = expenses.stream().mapToLong(Transaction::getAmount).sum();

        List<CategoryBreakdown> breakdown = expenses.stream()
            .collect(Collectors.groupingBy(Transaction::getCategory, Collectors.summingLong(Transaction::getAmount)))
            .entrySet().stream()
            .map(entry -> new CategoryBreakdown(entry.getKey(), entry.getValue(),
                totalExpense == 0 ? 0.0 : entry.getValue() * PERCENT_MULTIPLIER / totalExpense))
            .sorted(Comparator.comparingLong(CategoryBreakdown::total).reversed())
            .toList();

        String summary = String.format(Locale.ROOT, "%d categories, total expense %d", breakdown.size(), totalExpense);
        return new ToolExecutionResult(name(), breakdown, summary, false, null);
    }
}
