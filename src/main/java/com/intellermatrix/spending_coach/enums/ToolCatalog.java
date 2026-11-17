package com.intellermatrix.spending_coach.enums;

public enum ToolCatalog {
    GET_OVERVIEW("get_overview",
        "Returns the date range, row count, categories, income entries, and monthly totals."),
    SPENDING_BY_CATEGORY("spending_by_category",
        "Returns the total and share of expenses per category over a date range."),
    TOP_TRANSACTIONS("top_transactions",
        "Returns the largest matching transactions for a category, date range, and minimum amount."),
    SEARCH_TRANSACTIONS("search_transactions",
        "Returns matching transactions by text or category, plus the total count."),
    COMPARE_PERIODS("compare_periods",
        "Returns the per-group change in amount and percent between two periods, sorted by size."),
    FIND_RECURRING_CHARGES("find_recurring_charges",
        "Returns recurring charges with cadence, amount, and any change in amount."),
    DETECT_ANOMALIES("detect_anomalies",
        "Returns anomaly candidates with a reason: duplicate, spike, or price change."),
    PROJECT_MONTH_END("project_month_end",
        "Returns the projected balance by day and the day it reaches zero."),
    SIMULATE_SAVINGS("simulate_savings",
        "Returns the projected month-end balance and savings against the baseline for a list of adjustments."),
    ASK_USER("ask_user",
        "Pauses the loop and returns the user's answer to a question."),
    PROPOSE_BUDGET_CHANGE("propose_budget_change",
        "Creates a pending proposal for a list of budget changes; nothing changes until approved.");

    private final String id;
    private final String definition;

    ToolCatalog(String id, String definition) {
        this.id = id;
        this.definition = definition;
    }

    public String id() {
        return id;
    }

    public String definition() {
        return definition;
    }
}
