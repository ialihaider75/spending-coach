package com.intellermatrix.spending_coach.enums;

public enum AgentLoopMessage {
    STEP_LIMIT_REACHED("Reached the step limit before finishing — here is what was found so far."),
    WALL_CLOCK_LIMIT_REACHED("Reached the time limit before finishing — here is what was found so far."),
    LOOP_NUDGE("You've already called this tool with these exact arguments — try a different tool or different arguments."),
    LOOP_ABORTED("Aborted after calling the same tool with the same arguments three times.");

    private final String text;

    AgentLoopMessage(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
