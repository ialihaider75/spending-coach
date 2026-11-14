CREATE TABLE users (
    id UUID PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE agent_runs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    question TEXT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('RUNNING', 'AWAITING_USER', 'COMPLETED', 'FAILED')),
    total_tokens INTEGER,
    total_latency_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_agent_runs_user_id ON agent_runs(user_id);

CREATE TABLE agent_steps (
    run_id UUID NOT NULL REFERENCES agent_runs(id),
    step_number INTEGER NOT NULL,
    tool VARCHAR(100) NOT NULL,
    arguments JSONB,
    result_summary TEXT,
    latency_ms INTEGER,
    tokens INTEGER,
    error TEXT,
    PRIMARY KEY (run_id, step_number)
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    date DATE NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('EXPENSE', 'INCOME')),
    category VARCHAR(100) NOT NULL,
    note TEXT,
    source VARCHAR(20) NOT NULL CHECK (source IN ('CSV', 'MANUAL'))
);

CREATE INDEX idx_transactions_user_date ON transactions(user_id, date);
CREATE INDEX idx_transactions_user_category_date ON transactions(user_id, category, date);

CREATE TABLE budgets (
    user_id UUID NOT NULL REFERENCES users(id),
    category VARCHAR(100) NOT NULL,
    monthly_limit BIGINT NOT NULL,
    PRIMARY KEY (user_id, category)
);

CREATE TABLE budget_proposals (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    changes JSONB NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    run_id UUID REFERENCES agent_runs(id)
);

CREATE INDEX idx_budget_proposals_user_id ON budget_proposals(user_id);
CREATE INDEX idx_budget_proposals_run_id ON budget_proposals(run_id);
