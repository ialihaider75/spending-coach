package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.agent.dto.provider.ToolDefinition;
import com.intellermatrix.spending_coach.agent.dto.tool.RecurringCharge;
import com.intellermatrix.spending_coach.agent.dto.tool.ToolExecutionResult;
import com.intellermatrix.spending_coach.agent.tool.AgentTool;
import com.intellermatrix.spending_coach.enums.Kind;
import com.intellermatrix.spending_coach.enums.ToolCatalog;
import com.intellermatrix.spending_coach.persistence.entity.Transaction;
import com.intellermatrix.spending_coach.persistence.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class FindRecurringChargesTool implements AgentTool {

    private static final String SCHEMA = """
        {
        "type":"object",
        "properties":{"minOccurrences":{"type":"integer","minimum":1}}
        }
        """;
    private static final int DEFAULT_MIN_OCCURRENCES = 2;
    private static final long MONTHLY_GAP_DAYS = 30;
    private static final long QUARTERLY_GAP_DAYS = 90;

    private final TransactionRepository transactionRepository;

    @Override
    public String name() {
        return ToolCatalog.FIND_RECURRING_CHARGES.id();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(name(), ToolCatalog.FIND_RECURRING_CHARGES.definition(), SCHEMA);
    }

    @Override
    public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
        int minOccurrences = arguments.get("minOccurrences") != null
            ? ((Number) arguments.get("minOccurrences")).intValue() : DEFAULT_MIN_OCCURRENCES;
        if (minOccurrences < 1) {
            return new ToolExecutionResult(name(), null, null, false, "minOccurrences must be at least 1");
        }

        List<RecurringCharge> charges = transactionRepository.findByUserId(userId).stream()
            .filter(t -> t.getKind() == Kind.EXPENSE)
            .collect(Collectors.groupingBy(t -> new SimpleEntry<>(t.getCategory(), t.getNote())))
            .values().stream()
            .filter(group -> group.size() >= minOccurrences)
            .map(this::toRecurringCharge)
            .toList();

        String summary = String.format(Locale.ROOT, "%d recurring charges", charges.size());
        return new ToolExecutionResult(name(), charges, summary, false, null);
    }

    private RecurringCharge toRecurringCharge(List<Transaction> group) {
        List<Transaction> sorted = group.stream().sorted(Comparator.comparing(Transaction::getDate)).toList();
        Transaction latest = sorted.getLast();
        Long previousAmount = null;
        if (sorted.size() >= 2) {
            Transaction previous = sorted.get(sorted.size() - 2);
            previousAmount = previous.getAmount() != latest.getAmount() ? previous.getAmount() : null;
        }
        return new RecurringCharge(latest.getCategory(), latest.getNote(), sorted.size(), cadence(sorted),
            latest.getAmount(), previousAmount);
    }

    private String cadence(List<Transaction> sorted) {
        if (sorted.size() < 2) {
            return "unknown";
        }
        double averageGapDays = IntStream.range(1, sorted.size())
            .mapToLong(i -> ChronoUnit.DAYS.between(sorted.get(i - 1).getDate(), sorted.get(i).getDate()))
            .average()
            .orElseThrow();
        boolean closerToMonthly = Math.abs(averageGapDays - MONTHLY_GAP_DAYS) <= Math.abs(averageGapDays - QUARTERLY_GAP_DAYS);
        return closerToMonthly ? "monthly" : "quarterly";
    }
}
