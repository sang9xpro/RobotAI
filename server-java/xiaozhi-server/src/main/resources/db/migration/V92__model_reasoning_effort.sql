-- Nullable so existing configurations retain their enableThinking behavior.
ALTER TABLE sys_config ADD COLUMN reasoningEffort VARCHAR(16) NULL AFTER enableThinking;
