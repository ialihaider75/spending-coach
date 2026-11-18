package com.intellermatrix.spending_coach.agent.loop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.loop")
public record AgentLoopProperties(int maxSteps, long maxWallClockSeconds, int loopNudgeThreshold,
                                   int loopAbortThreshold) {
}
