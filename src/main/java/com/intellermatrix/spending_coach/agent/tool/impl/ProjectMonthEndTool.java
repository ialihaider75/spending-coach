package com.intellermatrix.spending_coach.agent.tool.impl;

import com.intellermatrix.spending_coach.agent.dto.provider.ToolDefinition;
import com.intellermatrix.spending_coach.agent.dto.tool.DailyBalance;
import com.intellermatrix.spending_coach.agent.dto.tool.ProjectionResult;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProjectMonthEndTool implements AgentTool {

    private static final String SCHEMA = """
        {
        "type":"object",
        "properties":{}
        }
        """;

    private final TransactionRepository transactionRepository;

    @Override
    public String name() {
        return ToolCatalog.PROJECT_MONTH_END.id();
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(name(), ToolCatalog.PROJECT_MONTH_END.definition(), SCHEMA);
    }

    @Override
    public ToolExecutionResult execute(UUID userId, Map<String, Object> arguments) {
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        LocalDate latestDate = transactions.stream().map(Transaction::getDate).max(Comparator.naturalOrder())
            .orElse(null);
        if (latestDate == null) {
            return new ToolExecutionResult(name(), null, null, false, "no transactions found for this user");
        }

        YearMonth currentMonth = YearMonth.from(latestDate);
        Map<LocalDate, Long> deltaByDate = transactions.stream()
            .filter(t -> YearMonth.from(t.getDate()).equals(currentMonth))
            .collect(Collectors.groupingBy(Transaction::getDate, TreeMap::new,
                Collectors.summingLong(t -> t.getKind() == Kind.INCOME ? t.getAmount() : -t.getAmount())));

        List<DailyBalance> dailyBalances = new ArrayList<>();
        long runningBalance = 0;
        Integer zeroCrossingDay = null;
        for (Map.Entry<LocalDate, Long> entry : deltaByDate.entrySet()) {
            runningBalance += entry.getValue();
            dailyBalances.add(new DailyBalance(entry.getKey(), runningBalance));
            if (zeroCrossingDay == null && runningBalance < 0) {
                zeroCrossingDay = entry.getKey().getDayOfMonth();
            }
        }

        var result = new ProjectionResult(dailyBalances, zeroCrossingDay);
        String summary = zeroCrossingDay != null
            ? String.format(Locale.ROOT, "balance goes negative on day %d", zeroCrossingDay)
            : "balance stays non-negative through month end";
        return new ToolExecutionResult(name(), result, summary, false, null);
    }
}
