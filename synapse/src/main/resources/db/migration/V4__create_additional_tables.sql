-- Create additional tables for Phase 1: Goal, TaskItem, ExecutionLog, MemoryEntry, etc.

CREATE TABLE goal (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'IN_PROGRESS',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

CREATE TABLE task_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id UUID NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    parent_task_id UUID REFERENCES task_item(id) ON DELETE SET NULL,
    description TEXT NOT NULL,
    required_skill_tags TEXT[] DEFAULT ARRAY[]::TEXT[],
    status VARCHAR(50) DEFAULT 'PENDING',
    assigned_agent_id UUID REFERENCES agent(id) ON DELETE SET NULL,
    priority INTEGER DEFAULT 5,
    deadline TIMESTAMP WITH TIME ZONE,
    estimated_cost_usd NUMERIC(12, 4),
    estimated_latency_ms BIGINT,
    actual_cost_usd NUMERIC(12, 4),
    actual_latency_ms BIGINT,
    confidence INTEGER,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

CREATE TABLE execution_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES task_item(id) ON DELETE CASCADE,
    role_name VARCHAR(255),
    required_skill_tag_count INTEGER,
    description_length INTEGER,
    model_tier VARCHAR(50),
    batch_size_at_execution INTEGER,
    context_overlap_score NUMERIC(5, 2),
    deadline_proximity_minutes INTEGER,
    priority INTEGER,
    actual_tokens_used BIGINT,
    actual_cost_usd NUMERIC(12, 4),
    actual_latency_ms BIGINT,
    confidence INTEGER,
    was_escalated BOOLEAN DEFAULT FALSE,
    retry_count INTEGER DEFAULT 0,
    succeeded BOOLEAN DEFAULT TRUE,
    human_approval_required BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE memory_entry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    agent_id UUID REFERENCES agent(id) ON DELETE SET NULL,
    scope VARCHAR(50) DEFAULT 'PRIVATE',
    content TEXT NOT NULL,
    embedding vector(1024),
    confidence INTEGER,
    superseded BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE memory_relation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_memory_id UUID NOT NULL REFERENCES memory_entry(id) ON DELETE CASCADE,
    to_memory_id UUID NOT NULL REFERENCES memory_entry(id) ON DELETE CASCADE,
    relation_type VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE approval (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES task_item(id) ON DELETE CASCADE,
    action_description TEXT NOT NULL,
    effect_type VARCHAR(50),
    confidence INTEGER,
    status VARCHAR(50) DEFAULT 'PENDING',
    resolved_by VARCHAR(255),
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE dead_letter_entry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID REFERENCES task_item(id) ON DELETE SET NULL,
    original_prompt TEXT,
    failure_reason TEXT,
    failed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved BOOLEAN DEFAULT FALSE
);

CREATE TABLE message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL,
    trace_id UUID NOT NULL,
    parent_message_id UUID REFERENCES message(id) ON DELETE SET NULL,
    sender_agent_id UUID NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    recipient_agent_id UUID REFERENCES agent(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    confidence INTEGER,
    sent_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indices for common queries
CREATE INDEX idx_goal_organization_id ON goal(organization_id);
CREATE INDEX idx_goal_status ON goal(status);
CREATE INDEX idx_task_item_goal_id ON task_item(goal_id);
CREATE INDEX idx_task_item_organization_id ON task_item(organization_id);
CREATE INDEX idx_task_item_assigned_agent_id ON task_item(assigned_agent_id);
CREATE INDEX idx_task_item_status ON task_item(status);
CREATE INDEX idx_task_item_parent_id ON task_item(parent_task_id);
CREATE INDEX idx_execution_log_task_id ON execution_log(task_id);
CREATE INDEX idx_memory_entry_organization_id ON memory_entry(organization_id);
CREATE INDEX idx_memory_entry_agent_id ON memory_entry(agent_id);
CREATE INDEX idx_memory_relation_from ON memory_relation(from_memory_id);
CREATE INDEX idx_memory_relation_to ON memory_relation(to_memory_id);
CREATE INDEX idx_approval_task_id ON approval(task_id);
CREATE INDEX idx_approval_status ON approval(status);
CREATE INDEX idx_message_run_id ON message(run_id);
CREATE INDEX idx_message_trace_id ON message(trace_id);
CREATE INDEX idx_message_sender_id ON message(sender_agent_id);
CREATE INDEX idx_message_recipient_id ON message(recipient_agent_id);
