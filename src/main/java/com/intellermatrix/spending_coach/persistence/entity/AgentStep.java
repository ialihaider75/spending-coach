package com.intellermatrix.spending_coach.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "agent_steps")
@IdClass(AgentStepId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgentStep {

    @Id
    @Column(name = "run_id")
    private UUID runId;

    @Id
    @Column(name = "step_number")
    private int stepNumber;

    @Column(nullable = false)
    private String tool;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "arguments", columnDefinition = "jsonb")
    private String argumentsJson;

    @Column(name = "result_summary")
    private String resultSummary;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    private Integer tokens;

    private String error;
}
