ALTER TABLE memory_entry
    ADD COLUMN IF NOT EXISTS task_id UUID REFERENCES task_item(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_memory_entry_task_id ON memory_entry(task_id);
