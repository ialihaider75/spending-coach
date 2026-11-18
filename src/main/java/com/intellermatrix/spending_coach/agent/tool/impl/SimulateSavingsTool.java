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
    @SuppressWarnings("unchecked")
    public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        LocalDate latestDate = transactions.stream().map(Transaction::getDate).max(Comparator.naturalOrder())
            .orElse(null);
        if (latestDate == null) {
            return new ToolExecutionResult(name(), null, null, false, "no transactions found for this user");
        }

        List<Map<String, Object>> adjustments = (List<Map<String, Object>>) arguments.get("adjustments");
        if (adjustments == null) {
            return new ToolExecutionResult(name(), null, null, false, "adjustments is required");
        }
        for (Map<String, Object> adjustment : adjustments) {
            if (!(adjustment.get("category") instanceof String)
                || !(adjustment.get("cutPercent") instanceof Number cutPercent)) {
                return new ToolExecutionResult(name(), null, null, false,
                    "each adjustment requires a string category and a numeric cutPercent");
            }
            int cut = cutPercent.intValue();
            if (cut < MIN_CUT_PERCENT || cut > MAX_CUT_PERCENT) {
                return new ToolExecutionResult(name(), null, null, false,
                    "cutPercent must be between 0 and 100");
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

        long projectedSavings = adjustments.stream()
            .mapToLong(adjustment -> {
                String category = (String) adjustment.get("category");
                int cutPercent = ((Number) adjustment.get("cutPercent")).intValue();
                long categoryTotal = expenseTotalsByCategory.getOrDefault(category, 0L);
                return Math.round(categoryTotal * cutPercent / PERCENT_DIVISOR);
            })
            .sum();

        long projectedBalance = baselineBalance + projectedSavings;
        var result = new SimulationResult(baselineBalance, projectedBalance, projectedSavings);
        String summary = String.format(Locale.ROOT, "simulated %d adjustment(s)", adjustments.size());
        return new ToolExecutionResult(name(), result, summary, false, null);
    }
}
