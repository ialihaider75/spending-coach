package com.intellermatrix.spending_coach.generator.models;

public enum TransactionTemplate {
    SALARY("salary", "monthly salary"),
    RENT("rent", "monthly rent"),
    SCHOOL_FEE("school-fees", "quarterly school fee"),
    GROCERIES("groceries", "weekly groceries"),
    SUBSCRIPTION("subscription", "monthly subscription"),
    DINING_BASELINE("dining", "dinner out"),
    DINING_SPIKE("dining", "delivery order"),
    SHOPPING("shopping", "purchase"),
    // category is seed-chosen (repairs/medical/travel) - only note() is used for this one
    ONE_OFF("", "one-time expense");

    private final String category;
    private final String note;

    TransactionTemplate(String category, String note) {
        this.category = category;
        this.note = note;
    }

    public String category() {
        return category;
    }

    public String note() {
        return note;
    }
}
