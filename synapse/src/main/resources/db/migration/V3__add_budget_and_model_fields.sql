-- Add budget and model fields to support Phase 1 requirements

-- Add budget fields to organization
ALTER TABLE organization ADD COLUMN IF NOT EXISTS total_budget_usd NUMERIC(12, 2) DEFAULT 10000.00;
ALTER TABLE organization ADD COLUMN IF NOT EXISTS spent_budget_usd NUMERIC(12, 2) DEFAULT 0.00;

-- Drop old agent table and recreate with correct schema
DROP INDEX IF EXISTS idx_agent_status;
DROP INDEX IF EXISTS idx_agent_team_id;
DROP INDEX IF EXISTS idx_agent_organization_id;
DROP TABLE IF EXISTS agent CASCADE;

-- Recreate agent table with correct schema
CREATE TABLE agent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    team_id UUID REFERENCES team(id) ON DELETE SET NULL,
    status VARCHAR(50) DEFAULT 'IDLE',
    tokens_used BIGINT DEFAULT 0,
    last_active_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Add role fields
ALTER TABLE role ADD COLUMN IF NOT EXISTS system_prompt TEXT;
ALTER TABLE role ADD COLUMN IF NOT EXISTS model_tier VARCHAR(50) DEFAULT 'HAIKU';
ALTER TABLE role ADD COLUMN IF NOT EXISTS token_budget BIGINT DEFAULT 1000000;
ALTER TABLE role ADD COLUMN IF NOT EXISTS escalates_to_role_id UUID REFERENCES role(id) ON DELETE SET NULL;
ALTER TABLE role ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- Add team fields
ALTER TABLE team ADD COLUMN IF NOT EXISTS topology VARCHAR(50) DEFAULT 'HIERARCHICAL';
ALTER TABLE team ADD COLUMN IF NOT EXISTS parent_team_id UUID REFERENCES team(id) ON DELETE SET NULL;
ALTER TABLE team ADD COLUMN IF NOT EXISTS lead_agent_id UUID REFERENCES agent(id) ON DELETE SET NULL;
ALTER TABLE team ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- Create indices for agent queries
CREATE INDEX idx_agent_role_id ON agent(role_id);
CREATE INDEX idx_agent_team_id ON agent(team_id);
CREATE INDEX idx_agent_status ON agent(status);
CREATE INDEX idx_agent_lead_team_id ON team(lead_agent_id);
CREATE INDEX idx_role_escalates_to ON role(escalates_to_role_id);
CREATE INDEX idx_team_parent_id ON team(parent_team_id);
