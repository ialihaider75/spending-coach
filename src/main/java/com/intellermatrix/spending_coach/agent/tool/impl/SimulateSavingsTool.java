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
import java.util.ArrayList;
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
        LocalDate latestDate = transactions.stream().map(Transaction::getDate).max(Comparator.naturalOrder())
            .orElse(null);
        if (latestDate == null) {
            return new ToolExecutionResult(name(), null, null, false, "no transactions found for this user");
        }

        if (!(arguments.get("adjustments") instanceof List<?> rawAdjustments)) {
            return new ToolExecutionResult(name(), null, null, false,
                "adjustments must be a list of {category, cutPercent} objects");
        }

        List<Map<?, ?>> adjustments = new ArrayList<>();
        for (Object rawAdjustment : rawAdjustments) {
            if (!(rawAdjustment instanceof Map<?, ?> adjustment)) {
                return new ToolExecutionResult(name(), null, null, false,
                    "each adjustment must be an object with a category and a cutPercent");
            }
            adjustments.add(adjustment);
        }

        Map<String, Integer> cutPercentByCategory = new HashMap<>();
        for (Map<?, ?> adjustment : adjustments) {
            if (!(adjustment.get("category") instanceof String category)
                || !(adjustment.get("cutPercent") instanceof Number cutPercent)) {
                return new ToolExecutionResult(name(), null, null, false,
                    "each adjustment requires a string category and a numeric cutPercent");
            }
            int cut = cutPercent.intValue();
            if (cut < MIN_CUT_PERCENT || cut > MAX_CUT_PERCENT) {
                return new ToolExecutionResult(name(), null, null, false,
                    "cutPercent must be between 0 and 100");
            }
            int totalCutForCategory = cutPercentByCategory.merge(category, cut, Integer::sum);
            if (totalCutForCategory > MAX_CUT_PERCENT) {
                return new ToolExecutionResult(name(), null, null, false,
                    String.format(Locale.ROOT, "combined cutPercent for category '%s' exceeds 100", category));
            }
        }

        YearMonth currentMonth = YearMonth.from(latestDate);
        List<Transaction> monthTransactions = transactions.stream()
            .filter(t -> YearMonth.from(t.getDate()).equals(currentMonth))
            .toList();

        long baselineBalance = monthTransactions.stream()
            .mapToLong(t -> t.getKind() == Kind.INCOME ? t.getAmount() : -t.getAmount())
            .sum();

        Map<String, Long> expenseTotalsByCategory = monthTransactions.stream()
            .filter(t -> t.getKind() == Kind.EXPENSE)
            .collect(Collectors.groupingBy(Transaction::getCategory, Collectors.summingLong(Transaction::getAmount)));

        long projectedSavings = cutPercentByCategory.entrySet().stream()
            .mapToLong(entry -> {
                long categoryTotal = expenseTotalsByCategory.getOrDefault(entry.getKey(), 0L);
                return Math.round(categoryTotal * entry.getValue() / PERCENT_DIVISOR);
            })
            .sum();

        long projectedBalance = baselineBalance + projectedSavings;
        var result = new SimulationResult(baselineBalance, projectedBalance, projectedSavings);
        String summary = String.format(Locale.ROOT, "simulated %d adjustment(s)", adjustments.size());
        return new ToolExecutionResult(name(), result, summary, false, null);
    }
}
