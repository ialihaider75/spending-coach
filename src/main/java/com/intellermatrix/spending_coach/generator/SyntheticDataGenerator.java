package com.intellermatrix.spending_coach.generator;

import com.intellermatrix.spending_coach.enums.Kind;
import com.intellermatrix.spending_coach.generator.models.SeedParameters;
import com.intellermatrix.spending_coach.generator.models.TransactionLine;
import com.intellermatrix.spending_coach.generator.models.TransactionTemplate;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Component
public class SyntheticDataGenerator {

    private static final String HEADER = "date,amount,category,note,kind";
    private static final int MONTHS = 6;
    private static final int ANCHOR_MONTH_INDEX = MONTHS - 1;
    private static final double WEEKEND_RATIO = 0.7;
    private static final long CROSSING_BUFFER = 1000;
    private static final long MIN_ORDER_AMOUNT = 100;

    private static final String INCOME = Kind.INCOME.name().toLowerCase();
    private static final String EXPENSE = Kind.EXPENSE.name().toLowerCase();

    public String generateCsv(long seed, LocalDate anchorDate) {
        SeedParameters params = SeedParameters.from(new Random(seed));
        YearMonth anchorMonth = YearMonth.from(anchorDate);

        String body = IntStream.range(0, MONTHS)
            .mapToObj(monthIndex -> monthLines(anchorMonth, monthIndex, anchorDate, params))
            .flatMap(List::stream)
            .sorted(Comparator.comparing(TransactionLine::date))
            .map(TransactionLine::toCsvLine)
            .collect(Collectors.joining("\n"));

        return HEADER + "\n" + body + "\n";
    }

    private List<TransactionLine> monthLines(YearMonth anchorMonth, int monthIndex, LocalDate anchorDate,
                                              SeedParameters p) {
        YearMonth month = anchorMonth.minusMonths((long) ANCHOR_MONTH_INDEX - monthIndex);
        boolean isAnchorMonth = monthIndex == ANCHOR_MONTH_INDEX;
        int lastDay = isAnchorMonth ? anchorDate.getDayOfMonth() : month.lengthOfMonth();
        boolean isQuarterMonth = (monthIndex + 1) % 3 == 0;

        List<TransactionLine> lines = new ArrayList<>(fixedCosts(month, lastDay, isQuarterMonth, p));
        lines.add(grocery(month, monthIndex, lastDay, p));
        lines.add(subscription(month, monthIndex, lastDay, p));
        if (isBaselineDiningMonth(monthIndex)) {
            lines.add(baselineDining(month, lastDay, p));
        }
        if (isOneOffMonth(monthIndex)) {
            lines.addAll(oneOffAndDuplicate(month, lastDay, p));
        }
        if (isAnchorMonth) {
            lines.addAll(diningSpike(month, lastDay, balanceSoFar(lines), p));
        }
        return lines;
    }

    // --- P1's "prior 3-month average" is drawn from the 3 months right before the spike ---
    private boolean isBaselineDiningMonth(int monthIndex) {
        return monthIndex >= ANCHOR_MONTH_INDEX - 3 && monthIndex <= ANCHOR_MONTH_INDEX - 1;
    }

    // --- P2/P4 just need to land somewhere outside the spike month; month 0 is as good as any ---
    private boolean isOneOffMonth(int monthIndex) {
        return monthIndex == 0;
    }

    private long balanceSoFar(List<TransactionLine> lines) {
        return lines.stream()
            .mapToLong(line -> INCOME.equals(line.kind()) ? line.amount() : -line.amount())
            .sum();
    }

    // --- P6 fixed costs: salary day 1, rent day 3, quarterly school fee ---
    private List<TransactionLine> fixedCosts(YearMonth month, int lastDay, boolean isQuarterMonth,
                                              SeedParameters p) {
        List<TransactionLine> costs = new ArrayList<>();
        costs.add(new TransactionLine(month.atDay(1), p.salary(), TransactionTemplate.SALARY.category(),
            TransactionTemplate.SALARY.note(), INCOME));
        costs.add(new TransactionLine(month.atDay(clampDay(3, lastDay)), p.rent(), TransactionTemplate.RENT.category(),
            TransactionTemplate.RENT.note(), EXPENSE));
        if (isQuarterMonth) {
            costs.add(new TransactionLine(month.atDay(clampDay(10, lastDay)), p.schoolFee(),
                TransactionTemplate.SCHOOL_FEE.category(), TransactionTemplate.SCHOOL_FEE.note(), EXPENSE));
        }
        return costs;
    }

    // --- P5 grocery trend: monthly total grows by a constant percent ---
    private TransactionLine grocery(YearMonth month, int monthIndex, int lastDay, SeedParameters p) {
        long amount = Math.round(p.groceryBase() * Math.pow(1 + p.groceryGrowth(), monthIndex));
        return new TransactionLine(month.atDay(clampDay(5, lastDay)), amount, TransactionTemplate.GROCERIES.category(),
            TransactionTemplate.GROCERIES.note(), EXPENSE);
    }

    // --- P3 subscription creep: steps up once at the halfway point ---
    private TransactionLine subscription(YearMonth month, int monthIndex, int lastDay, SeedParameters p) {
        long amount = monthIndex < MONTHS / 2 ? p.subscriptionStart() : p.subscriptionStart() + p.subscriptionIncrease();
        return new TransactionLine(month.atDay(clampDay(20, lastDay)), amount,
            TransactionTemplate.SUBSCRIPTION.category(), TransactionTemplate.SUBSCRIPTION.note(), EXPENSE);
    }

    // --- P1 baseline: the "prior 3-month average" the spike month is compared against ---
    private TransactionLine baselineDining(YearMonth month, int lastDay, SeedParameters p) {
        return new TransactionLine(month.atDay(clampDay(8, lastDay)), p.diningBaseline(),
            TransactionTemplate.DINING_BASELINE.category(), TransactionTemplate.DINING_BASELINE.note(), EXPENSE);
    }

    // --- P2 one-off + P4 duplicate charge, both in month 0 ---
    private List<TransactionLine> oneOffAndDuplicate(YearMonth month, int lastDay, SeedParameters p) {
        TransactionLine oneOff = new TransactionLine(month.atDay(clampDay(15, lastDay)), p.oneOffAmount(),
            p.oneOffCategory(), TransactionTemplate.ONE_OFF.note(), EXPENSE);

        LocalDate duplicateDate = month.atDay(clampDay(12, lastDay));
        Stream<TransactionLine> duplicateCharges = Stream.generate(() -> new TransactionLine(duplicateDate,
                p.duplicateAmount(), TransactionTemplate.SHOPPING.category(), TransactionTemplate.SHOPPING.note(),
                EXPENSE))
            .limit(2);

        return Stream.concat(Stream.of(oneOff), duplicateCharges).toList();
    }

    // --- P1 spike + P7 zero-crossing: the first order is sized to drive that day's
    //     running balance negative, so the crossing lands exactly on the target day ---
    private List<TransactionLine> diningSpike(YearMonth anchorMonth, int lastDay, long balanceAtDay21,
                                               SeedParameters p) {
        int targetDay = Math.min(p.crossingDayTarget(), lastDay);

        int remainingOrderCount = p.spikeOrderCount() - 1;
        long firstOrderAmount = Math.max(balanceAtDay21 + CROSSING_BUFFER, MIN_ORDER_AMOUNT);
        long spikeTotal = Math.max(p.diningBaseline() + p.spikeExcess(),
            firstOrderAmount + remainingOrderCount * MIN_ORDER_AMOUNT);
        long remainingTotal = spikeTotal - firstOrderAmount;
        long perOrder = remainingTotal / remainingOrderCount;
        long remainder = remainingTotal % remainingOrderCount;

        List<LocalDate> orderDates = spikeOrderDates(anchorMonth, targetDay, lastDay, p.spikeOrderCount());
        int lastOrderIndex = orderDates.size() - 1;

        return IntStream.range(0, orderDates.size())
            .mapToObj(i -> new TransactionLine(orderDates.get(i),
                spikeOrderAmount(i, lastOrderIndex, firstOrderAmount, perOrder, remainder),
                TransactionTemplate.DINING_SPIKE.category(), TransactionTemplate.DINING_SPIKE.note(), EXPENSE))
            .toList();
    }

    private long spikeOrderAmount(int orderIndex, int lastOrderIndex, long firstOrderAmount, long perOrder,
                                   long remainder) {
        if (orderIndex == 0) {
            return firstOrderAmount;
        }
        return orderIndex == lastOrderIndex ? perOrder + remainder : perOrder;
    }

    private List<LocalDate> spikeOrderDates(YearMonth anchorMonth, int targetDay, int lastDay, int orderCount) {
        List<LocalDate> window = IntStream.rangeClosed(targetDay, lastDay).mapToObj(anchorMonth::atDay).toList();
        Map<Boolean, List<LocalDate>> byWeekend = window.stream().collect(Collectors.partitioningBy(this::isWeekend));
        List<LocalDate> weekendDays = byWeekend.get(true);
        List<LocalDate> weekdayDays = byWeekend.get(false);

        LocalDate crossingDate = anchorMonth.atDay(targetDay);
        int weekendStillNeeded = (int) Math.ceil(orderCount * WEEKEND_RATIO) - (isWeekend(crossingDate) ? 1 : 0);

        List<LocalDate> dates = new ArrayList<>();
        dates.add(crossingDate);
        for (int i = 0; i < orderCount - 1; i++) {
            List<LocalDate> pool = i < weekendStillNeeded ? preferOrFallback(weekendDays, weekdayDays)
                                                             : preferOrFallback(weekdayDays, weekendDays);
            dates.add(pool.get(i % pool.size()));
        }
        return dates;
    }

    private static List<LocalDate> preferOrFallback(List<LocalDate> preferred, List<LocalDate> fallback) {
        return preferred.isEmpty() ? fallback : preferred;
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day.equals(DayOfWeek.SATURDAY) || day.equals(DayOfWeek.SUNDAY);
    }

    private static int clampDay(int day, int lastDay) {
        return Math.min(day, lastDay);
    }
}
