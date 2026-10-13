CREATE TABLE IF NOT EXISTS robot_person_memory (
 scopeId CHAR(64) NOT NULL PRIMARY KEY,
 userId INT UNSIGNED NOT NULL,
 deviceId VARCHAR(128) NOT NULL,
 roleId INT UNSIGNED NOT NULL,
 personId CHAR(36) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 revision BIGINT NOT NULL DEFAULT 0,
 KEY idx_person_memory_user (userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS robot_person_fact (
 scopeId CHAR(64) NOT NULL,
 factKey VARCHAR(32) NOT NULL,
 factValue VARCHAR(160) NOT NULL,
 PRIMARY KEY (scopeId,factKey),
 CONSTRAINT fk_person_fact_scope FOREIGN KEY (scopeId) REFERENCES robot_person_memory(scopeId) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
