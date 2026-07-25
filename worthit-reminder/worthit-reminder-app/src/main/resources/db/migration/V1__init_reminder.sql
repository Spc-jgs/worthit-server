-- Source: 值不值小程序 数据库模型 V0.3.4
-- Baseline: PRD V0.15.6 / 架构 V0.3.14
-- Keep in sync with docs/数据库文档/值不值小程序_数据库模型_V0.3.4.docx
-- Schema: worthit_reminder
-- No del_flag; last_source_version default 0; aggregate version starts at 1

CREATE TABLE rem_binding (
  id                  BIGINT      NOT NULL,
  user_id             BIGINT      NOT NULL,
  business_type       VARCHAR(32) NOT NULL,
  business_id         BIGINT      NOT NULL,
  reminder_type       VARCHAR(32) NOT NULL,
  reminder_enabled    TINYINT(1)  NOT NULL,
  last_source_version BIGINT      NOT NULL DEFAULT 0,
  create_time         DATETIME(3) NOT NULL,
  update_time         DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_binding_biz (user_id, business_type, business_id, reminder_type),
  KEY idx_binding_user (user_id),
  CONSTRAINT chk_binding_enabled CHECK (reminder_enabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Concurrent create: INSERT ... ON DUPLICATE KEY UPDATE id = id; then SELECT ... FOR UPDATE

CREATE TABLE rem_instance (
  id                       BIGINT       NOT NULL,
  binding_id               BIGINT       NOT NULL,
  user_id                  BIGINT       NOT NULL,
  business_date            DATE         NOT NULL,
  remind_at                DATETIME(3)  NOT NULL COMMENT 'immutable after insert',
  timezone                 VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai',
  status                   VARCHAR(32)  NOT NULL,
  resolved_at              DATETIME(3)  NULL,
  resolution_reason        VARCHAR(64)  NULL,
  created_source_event_id  VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NULL,
  resolved_source_event_id VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NULL,
  pending_marker           TINYINT
    GENERATED ALWAYS AS (
      CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END
    ) VIRTUAL,
  create_time              DATETIME(3)  NOT NULL,
  update_time              DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_instance_pending (binding_id, pending_marker),
  KEY idx_inst_user_pending (user_id, status, remind_at),
  KEY idx_inst_user_resolved (user_id, status, resolved_at),
  KEY idx_inst_binding_date (binding_id, business_date),
  CONSTRAINT chk_rem_instance_resolution CHECK (
    (
      status = 'PENDING'
      AND resolved_at IS NULL
      AND resolution_reason IS NULL
    )
    OR
    (
      status IN ('PROCESSED', 'IGNORED', 'CANCELED')
      AND resolved_at IS NOT NULL
      AND resolution_reason IS NOT NULL
    )
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rem_command_log (
  id                     BIGINT       NOT NULL,
  event_id               VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  binding_id             BIGINT       NOT NULL,
  source_version         BIGINT       NOT NULL,
  schema_version         INT          NOT NULL,
  payload_digest         CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation_type         VARCHAR(64)  NOT NULL,
  result_code            VARCHAR(32)  NOT NULL COMMENT 'APPLIED|IGNORED_OLD only',
  result_message         VARCHAR(256) NULL,
  conflict_count         INT          NOT NULL DEFAULT 0,
  last_conflict_event_id VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NULL,
  last_conflict_digest   CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NULL,
  last_conflict_at       DATETIME(3)  NULL,
  create_time            DATETIME(3)  NOT NULL,
  update_time            DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cmd_event (event_id),
  UNIQUE KEY uk_cmd_binding_ver (binding_id, source_version),
  CONSTRAINT chk_cmd_result CHECK (result_code IN ('APPLIED', 'IGNORED_OLD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Idempotent API success does NOT rewrite result_code to IDEMPOTENT
-- Same version different digest: bump conflict_* on authoritative row, keep result_code
