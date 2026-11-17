package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.agent.dto.provider.ToolDefinition;
import com.intellermatrix.spending_coach.agent.dto.tool.PeriodComparisonRow;
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
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ComparePeriodsTool implements AgentTool {

    private static final String SCHEMA = """
        {
        "type":"object",
        "properties":{"periodAFrom":{"type":"string"},"periodATo":{"type":"string"},"periodBFrom":{"type":"string"},"periodBTo":{"type":"string"}},
        "required":["periodAFrom","periodATo","periodBFrom","periodBTo"]
        }
        """;
    private static final double PERCENT_MULTIPLIER = 100.0;

    private final TransactionRepository transactionRepository;

    @Override
    public String name() {
        return ToolCatalog.COMPARE_PERIODS.id();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(name(), ToolCatalog.COMPARE_PERIODS.definition(), SCHEMA);
    }

    @Override
    public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
        LocalDate periodAFrom;
        LocalDate periodATo;
        LocalDate periodBFrom;
        LocalDate periodBTo;
        try {
            periodAFrom = LocalDate.parse((String) arguments.get("periodAFrom"));
            periodATo = LocalDate.parse((String) arguments.get("periodATo"));
            periodBFrom = LocalDate.parse((String) arguments.get("periodBFrom"));
            periodBTo = LocalDate.parse((String) arguments.get("periodBTo"));
        } catch (DateTimeParseException e) {
            return new ToolExecutionResult(name(), null, null, false,
                "periodAFrom/periodATo/periodBFrom/periodBTo must be ISO yyyy-MM-dd dates: " + e.getMessage());
        }

        if (periodBFrom.isAfter(periodBTo)) {
            return new ToolExecutionResult(name(), null, null, false, "periodBFrom must be on or before periodBTo");
        }

        Map<String, Long> totalsA = totalsByCategory(userId, periodAFrom, periodATo);
        Map<String, Long> totalsB = totalsByCategory(userId, periodBFrom, periodBTo);
        long monthsInPeriodB = YearMonth.from(periodBFrom).until(YearMonth.from(periodBTo), ChronoUnit.MONTHS) + 1;

        Set<String> categories = new HashSet<>();
        categories.addAll(totalsA.keySet());
        categories.addAll(totalsB.keySet());

        List<PeriodComparisonRow> rows = categories.stream()
            .map(category -> {
                long amountA = totalsA.getOrDefault(category, 0L);
                long monthlyAverageB = totalsB.getOrDefault(category, 0L) / monthsInPeriodB;
                long change = amountA - monthlyAverageB;
                double changePercent = monthlyAverageB == 0 ? 0.0 : change * PERCENT_MULTIPLIER / monthlyAverageB;
                return new PeriodComparisonRow(category, amountA, monthlyAverageB, change, changePercent);
            })
            .sorted(Comparator.comparingLong((PeriodComparisonRow r) -> Math.abs(r.change())).reversed())
            .toList();

        String summary = String.format(Locale.ROOT, "%d categories compared", rows.size());
        return new ToolExecutionResult(name(), rows, summary, false, null);
    }

    private Map<String, Long> totalsByCategory(UUID userId, LocalDate from, LocalDate to) {
        return transactionRepository.findByUserIdAndDateBetween(userId, from, to).stream()
            .filter(t -> t.getKind() == Kind.EXPENSE)
            .collect(Collectors.groupingBy(Transaction::getCategory, Collectors.summingLong(Transaction::getAmount)));
    }
}
