package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.agent.dto.provider.ToolDefinition;
import com.intellermatrix.spending_coach.agent.dto.tool.SimulationResult;
import com.intellermatrix.spending_coach.agent.dto.tool.ToolExecutionResult;
import com.intellermatrix.spending_coach.agent.tool.AgentTool;
import com.intellermatrix.spending_coach.enums.Kind;
import com.intellermatrix.spending_coach.enums.ToolCatalog;
import com.intellermatrix.spending_coach.persistence.entity.Transaction;
import com.intellermatrix.spending_coach.persistence.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SimulateSavingsTool implements AgentTool {

    private static final String SCHEMA = """
        {
        "type":"object",
        "properties":{"adjustments":{"type":"array","items":{"type":"object","properties":{"category":{"type":"string"},"cutPercent":{"type":"integer","minimum":0,"maximum":100}},"required":["category","cutPercent"]}}},
        "required":["adjustments"]
        }
        """;
    private static final int MIN_CUT_PERCENT = 0;
    private static final int MAX_CUT_PERCENT = 100;
    private static final double PERCENT_DIVISOR = 100.0;

    private final TransactionRepository transactionRepository;

    @Override
    public String name() {
        return ToolCatalog.SIMULATE_SAVINGS.id();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(name(), ToolCatalog.SIMULATE_SAVINGS.definition(), SCHEMA);
    }

    @Override
    public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        LocalDate latestDate = latestTransactionDate(transactions);
        if (latestDate == null) {
            return errorResult("no transactions found for this user");
        }

        AdjustmentsValidation validation = validateAdjustments(arguments.get("adjustments"));
        if (validation.error() != null) {
            return errorResult(validation.error());
        }

        List<Transaction> monthTransactions = transactionsInMonth(transactions, YearMonth.from(latestDate));
        long baselineBalance = netBalance(monthTransactions);
        Map<String, Long> expenseTotalsByCategory = expenseTotalsByCategory(monthTransactions);
        long projectedSavings = projectedSavings(validation.cutPercentByCategory(), expenseTotalsByCategory);
        long projectedBalance = baselineBalance + projectedSavings;

        var result = new SimulationResult(baselineBalance, projectedBalance, projectedSavings);
        String summary = String.format(Locale.ROOT, "simulated %d adjustment(s)", validation.count());
        return new ToolExecutionResult(name(), result, summary, false, null);
    }

    private LocalDate latestTransactionDate(List<Transaction> transactions) {
        return transactions.stream().map(Transaction::getDate).max(Comparator.naturalOrder()).orElse(null);
    }

    private AdjustmentsValidation validateAdjustments(Object rawArgument) {
        if (!(rawArgument instanceof List<?> rawAdjustments)) {
            return AdjustmentsValidation.error("adjustments must be a list of {category, cutPercent} objects");
        }

        Map<String, Integer> cutPercentByCategory = new HashMap<>();
        for (Object rawAdjustment : rawAdjustments) {
            if (!(rawAdjustment instanceof Map<?, ?> adjustment)) {
                return AdjustmentsValidation.error("each adjustment must be an object with a category and a cutPercent");
            }
            if (!(adjustment.get("category") instanceof String category)
                || !(adjustment.get("cutPercent") instanceof Number cutPercent)) {
                return AdjustmentsValidation.error("each adjustment requires a string category and a numeric cutPercent");
            }
            int cut = cutPercent.intValue();
            if (cut < MIN_CUT_PERCENT || cut > MAX_CUT_PERCENT) {
                return AdjustmentsValidation.error("cutPercent must be between 0 and 100");
            }
            int totalCutForCategory = cutPercentByCategory.merge(category, cut, Integer::sum);
            if (totalCutForCategory > MAX_CUT_PERCENT) {
                return AdjustmentsValidation.error(
                    String.format(Locale.ROOT, "combined cutPercent for category '%s' exceeds 100", category));
            }
        }
        return AdjustmentsValidation.ok(cutPercentByCategory, rawAdjustments.size());
    }

    private List<Transaction> transactionsInMonth(List<Transaction> transactions, YearMonth month) {
        return transactions.stream().filter(t -> YearMonth.from(t.getDate()).equals(month)).toList();
    }

    private long netBalance(List<Transaction> monthTransactions) {
        return monthTransactions.stream()
            .mapToLong(t -> t.getKind() == Kind.INCOME ? t.getAmount() : -t.getAmount())
            .sum();
    }

    private Map<String, Long> expenseTotalsByCategory(List<Transaction> monthTransactions) {
        return monthTransactions.stream()
            .filter(t -> t.getKind() == Kind.EXPENSE)
            .collect(Collectors.groupingBy(Transaction::getCategory, Collectors.summingLong(Transaction::getAmount)));
    }

    private long projectedSavings(Map<String, Integer> cutPercentByCategory, Map<String, Long> expenseTotalsByCategory) {
        return cutPercentByCategory.entrySet().stream()
            .mapToLong(entry -> {
                long categoryTotal = expenseTotalsByCategory.getOrDefault(entry.getKey(), 0L);
                return Math.round(categoryTotal * entry.getValue() / PERCENT_DIVISOR);
            })
            .sum();
    }

    private ToolExecutionResult errorResult(String message) {
        return new ToolExecutionResult(name(), null, null, false, message);
    }

    private record AdjustmentsValidation(Map<String, Integer> cutPercentByCategory, int count, String error) {
        static AdjustmentsValidation error(String message) {
            return new AdjustmentsValidation(null, 0, message);
        }

        static AdjustmentsValidation ok(Map<String, Integer> cutPercentByCategory, int count) {
            return new AdjustmentsValidation(cutPercentByCategory, count, null);
        }
    }
}
