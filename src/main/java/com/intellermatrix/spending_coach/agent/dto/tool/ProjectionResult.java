package com.intellermatrix.spending_coach.agent.dto.tool;

import java.util.List;

public record ProjectionResult(List<DailyBalance> dailyBalances, Integer zeroCrossingDay) {
}
