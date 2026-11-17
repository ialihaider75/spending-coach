package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.agent.dto.ToolDefinition;
import com.intellermatrix.spending_coach.agent.tool.AgentTool;
import com.intellermatrix.spending_coach.agent.tool.ToolExecutionResult;
import com.intellermatrix.spending_coach.agent.tool.impl.dto.IncomeEntry;
import com.intellermatrix.spending_coach.agent.tool.impl.dto.MonthlyTotal;
import com.intellermatrix.spending_coach.agent.tool.impl.dto.OverviewResult;
import com.intellermatrix.spending_coach.enums.Kind;
import com.intellermatrix.spending_coach.enums.ToolCatalog;
import com.intellermatrix.spending_coach.persistence.entity.Transaction;
import com.intellermatrix.spending_coach.persistence.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GetOverviewTool implements AgentTool {

    private static final String SCHEMA = """
        {
        "type":"object",
        "properties":{}
        }
        """;

    private final TransactionRepository transactionRepository;

    @Override
    public String name() {
        return ToolCatalog.GET_OVERVIEW.id();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(name(), ToolCatalog.GET_OVERVIEW.definition(), SCHEMA);
    }

    @Override
    public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
        List<Transaction> transactions = transactionRepository.findByUserId(userId);

        var fromDate = transactions.stream().map(Transaction::getDate).min(Comparator.naturalOrder()).orElse(null);
        var toDate = transactions.stream().map(Transaction::getDate).max(Comparator.naturalOrder()).orElse(null);
        var categories = transactions.stream().map(Transaction::getCategory).distinct().toList();
        var incomeEntries = transactions.stream()
            .filter(t -> t.getKind() == Kind.INCOME)
            .map(t -> new IncomeEntry(t.getDate(), t.getAmount(), t.getNote()))
            .toList();
        var monthlyTotals = transactions.stream()
            .collect(Collectors.groupingBy(t -> YearMonth.from(t.getDate())))
            .entrySet().stream()
            .map(entry -> new MonthlyTotal(entry.getKey(),
                entry.getValue().stream().filter(t -> t.getKind() == Kind.INCOME).mapToLong(Transaction::getAmount).sum(),
                entry.getValue().stream().filter(t -> t.getKind() == Kind.EXPENSE).mapToLong(Transaction::getAmount).sum()))
            .sorted(Comparator.comparing(MonthlyTotal::month))
            .toList();

        var result = new OverviewResult(fromDate, toDate, transactions.size(), categories, incomeEntries, monthlyTotals);
        String summary = String.format(Locale.ROOT, "%d transactions from %s to %s",
            transactions.size(), fromDate, toDate);
        return new ToolExecutionResult(name(), result, summary, false, null);
    }
}
