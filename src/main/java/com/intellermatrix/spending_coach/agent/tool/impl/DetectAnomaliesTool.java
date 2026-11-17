package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.agent.dto.provider.ToolDefinition;
import com.intellermatrix.spending_coach.agent.dto.tool.AnomalyCandidate;
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
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DetectAnomaliesTool implements AgentTool {

    private static final String SCHEMA = """
        {
        "type":"object",
        "properties":{"from":{"type":"string"},"to":{"type":"string"}}
        }
        """;
    private static final int PRIOR_MONTHS_WINDOW = 3;
    private static final double SPIKE_MULTIPLIER = 2.0;

    private final TransactionRepository transactionRepository;

    @Override
    public String name() {
        return ToolCatalog.DETECT_ANOMALIES.id();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(name(), ToolCatalog.DETECT_ANOMALIES.definition(), SCHEMA);
    }

    @Override
    public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
        LocalDate from;
        LocalDate to;
        try {
            from = arguments.get("from") != null ? LocalDate.parse((String) arguments.get("from")) : null;
            to = arguments.get("to") != null ? LocalDate.parse((String) arguments.get("to")) : null;
        } catch (DateTimeParseException e) {
            return new ToolExecutionResult(name(), null, null, false,
                "from and to must be ISO yyyy-MM-dd dates: " + e.getMessage());
        }

        List<Transaction> expenses = transactionRepository.findByUserId(userId).stream()
            .filter(t -> t.getKind() == Kind.EXPENSE)
            .filter(t -> from == null || !t.getDate().isBefore(from))
            .filter(t -> to == null || !t.getDate().isAfter(to))
            .toList();

        List<AnomalyCandidate> candidates = new ArrayList<>();
        candidates.addAll(duplicates(expenses));
        candidates.addAll(priceChanges(expenses));
        candidates.addAll(spikes(expenses));

        String summary = String.format(Locale.ROOT, "%d anomaly candidates", candidates.size());
        return new ToolExecutionResult(name(), candidates, summary, false, null);
    }

    private List<AnomalyCandidate> duplicates(List<Transaction> expenses) {
        return expenses.stream()
            .collect(Collectors.groupingBy(t -> List.of(t.getCategory(), t.getAmount(), t.getDate())))
            .values().stream()
            .filter(group -> group.size() >= 2)
            .flatMap(List::stream)
            .map(t -> toCandidate("duplicate", t))
            .toList();
    }

    private List<AnomalyCandidate> priceChanges(List<Transaction> expenses) {
        return expenses.stream()
            .collect(Collectors.groupingBy(t -> new SimpleEntry<>(t.getCategory(), t.getNote())))
            .values().stream()
            .flatMap(group -> {
                List<Transaction> sorted = group.stream().sorted(Comparator.comparing(Transaction::getDate)).toList();
                List<AnomalyCandidate> changes = new ArrayList<>();
                for (int i = 1; i < sorted.size(); i++) {
                    if (sorted.get(i).getAmount() != sorted.get(i - 1).getAmount()) {
                        changes.add(toCandidate("price_change", sorted.get(i)));
                    }
                }
                return changes.stream();
            })
            .toList();
    }

    private List<AnomalyCandidate> spikes(List<Transaction> expenses) {
        if (expenses.isEmpty()) {
            return List.of();
        }
        YearMonth recentMonth = expenses.stream().map(t -> YearMonth.from(t.getDate()))
            .max(Comparator.naturalOrder()).orElseThrow();
        YearMonth priorWindowStart = recentMonth.minusMonths(PRIOR_MONTHS_WINDOW);

        Map<String, Long> recentTotals = totalsForMonth(expenses, recentMonth);
        Map<String, Long> priorTotals = expenses.stream()
            .filter(t -> {
                YearMonth month = YearMonth.from(t.getDate());
                return !month.isBefore(priorWindowStart) && month.isBefore(recentMonth);
            })
            .collect(Collectors.groupingBy(Transaction::getCategory, Collectors.summingLong(Transaction::getAmount)));

        List<AnomalyCandidate> result = new ArrayList<>();
        for (var entry : recentTotals.entrySet()) {
            String category = entry.getKey();
            Long priorTotal = priorTotals.get(category);
            if (priorTotal == null) {
                continue;
            }
            double averagePrior = priorTotal / (double) PRIOR_MONTHS_WINDOW;
            if (entry.getValue() > SPIKE_MULTIPLIER * averagePrior) {
                expenses.stream()
                    .filter(t -> YearMonth.from(t.getDate()).equals(recentMonth) && t.getCategory().equals(category))
                    .max(Comparator.comparingLong(Transaction::getAmount))
                    .ifPresent(representative -> result.add(toCandidate("spike", representative)));
            }
        }
        return result;
    }

    private Map<String, Long> totalsForMonth(List<Transaction> expenses, YearMonth month) {
        return expenses.stream()
            .filter(t -> YearMonth.from(t.getDate()).equals(month))
            .collect(Collectors.groupingBy(Transaction::getCategory, Collectors.summingLong(Transaction::getAmount)));
    }

    private AnomalyCandidate toCandidate(String reason, Transaction t) {
        return new AnomalyCandidate(reason, t.getId(), t.getDate(), t.getAmount(), t.getCategory(), t.getNote());
    }
}
