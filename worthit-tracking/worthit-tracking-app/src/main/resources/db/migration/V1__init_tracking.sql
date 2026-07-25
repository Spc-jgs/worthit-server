-- Source: 值不值小程序 数据库模型 V0.3.4
-- Baseline: PRD V0.15.6 / 架构 V0.3.14
-- Keep in sync with docs/数据库文档/值不值小程序_数据库模型_V0.3.4.docx
-- Schema: worthit_tracking (M1 only)
-- M2: tracking_m2/V2__add_item_lifecycle.sql
-- create_by/update_by: system tasks use NULL (never 0)

CREATE TABLE trk_category (
  id                 BIGINT       NOT NULL,
  user_id            BIGINT       NOT NULL,
  name               VARCHAR(32)  NOT NULL,
  system_code        VARCHAR(32)  NULL COMMENT 'UNCATEGORIZED',
  version            BIGINT       NOT NULL DEFAULT 1,
  create_by          BIGINT       NULL,
  create_time        DATETIME(3)  NOT NULL,
  update_by          BIGINT       NULL,
  update_time        DATETIME(3)  NOT NULL,
  del_flag           TINYINT(1)   NOT NULL DEFAULT 0,
  delete_time        DATETIME(3)  NULL,
  active_name        VARCHAR(32)
    GENERATED ALWAYS AS (CASE WHEN del_flag = 0 THEN name ELSE NULL END) VIRTUAL,
  active_system_code VARCHAR(32)
    GENERATED ALWAYS AS (CASE WHEN del_flag = 0 THEN system_code ELSE NULL END) VIRTUAL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_category_active_name (user_id, active_name),
  UNIQUE KEY uk_category_active_system (user_id, active_system_code),
  KEY idx_category_user_del (user_id, del_flag),
  CONSTRAINT chk_category_del CHECK (del_flag IN (0, 1)),
  CONSTRAINT chk_category_del_time CHECK (
    (del_flag = 0 AND delete_time IS NULL)
    OR (del_flag = 1 AND delete_time IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Uncategorized: Tracking getOrCreateUncategorized(userId) on first write; Auth never writes this table

CREATE TABLE trk_item (
  id                        BIGINT         NOT NULL,
  user_id                   BIGINT         NOT NULL,
  category_id               BIGINT         NOT NULL,
  name                      VARCHAR(64)    NOT NULL,
  purchase_price            DECIMAL(18, 6) NOT NULL,
  expected_years            DECIMAL(8, 3)  NOT NULL,
  residual_value            DECIMAL(18, 6) NULL,
  purchase_date             DATE           NULL,
  warranty_expire_date      DATE           NULL,
  warranty_reminder_enabled TINYINT(1)     NOT NULL DEFAULT 0,
  brand_model               VARCHAR(128)   NULL,
  remark                    VARCHAR(512)   NULL,
  source_wish_id            BIGINT         NULL COMMENT 'set only when converted from wish; unique',
  lifecycle_status          VARCHAR(32)    NOT NULL DEFAULT 'HOLDING',
  version                   BIGINT         NOT NULL DEFAULT 1,
  create_by                 BIGINT         NULL,
  create_time               DATETIME(3)    NOT NULL,
  update_by                 BIGINT         NULL,
  update_time               DATETIME(3)    NOT NULL,
  del_flag                  TINYINT(1)     NOT NULL DEFAULT 0,
  delete_time               DATETIME(3)    NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_item_source_wish (source_wish_id),
  KEY idx_item_status (user_id, del_flag, lifecycle_status),
  KEY idx_item_category (user_id, del_flag, category_id),
  KEY idx_item_list (user_id, del_flag, create_time),
  KEY idx_item_name (user_id, del_flag, name),
  CONSTRAINT chk_item_price CHECK (purchase_price >= 0),
  CONSTRAINT chk_item_years CHECK (expected_years > 0),
  CONSTRAINT chk_item_residual CHECK (residual_value IS NULL OR residual_value >= 0),
  CONSTRAINT chk_item_del CHECK (del_flag IN (0, 1)),
  CONSTRAINT chk_item_warranty_rem CHECK (warranty_reminder_enabled IN (0, 1)),
  CONSTRAINT chk_item_warranty_rem_date CHECK (
    warranty_expire_date IS NOT NULL
    OR warranty_reminder_enabled = 0
  ),
  CONSTRAINT chk_item_lifecycle CHECK (
    lifecycle_status IN ('HOLDING','RETURNED','SOLD','SCRAPPED')
  ),
  CONSTRAINT chk_item_del_time CHECK (
    (del_flag = 0 AND delete_time IS NULL)
    OR (del_flag = 1 AND delete_time IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trk_subscription (
  id                       BIGINT         NOT NULL,
  user_id                  BIGINT         NOT NULL,
  category_id              BIGINT         NOT NULL,
  name                     VARCHAR(64)    NOT NULL,
  amount                   DECIMAL(18, 6) NOT NULL,
  currency                 CHAR(3)        NOT NULL DEFAULT 'CNY',
  billing_cycle_type       VARCHAR(32)    NOT NULL,
  billing_cycle_value      INT            NULL,
  cny_reference_amount     DECIMAL(18, 6) NULL,
  next_renewal_date        DATE           NULL,
  auto_renew               VARCHAR(16)    NOT NULL DEFAULT 'UNKNOWN',
  renewal_reminder_enabled TINYINT(1)     NOT NULL DEFAULT 0,
  status                   VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',
  remark                   VARCHAR(512)   NULL,
  version                  BIGINT         NOT NULL DEFAULT 1,
  create_by                BIGINT         NULL,
  create_time              DATETIME(3)    NOT NULL,
  update_by                BIGINT         NULL,
  update_time              DATETIME(3)    NOT NULL,
  del_flag                 TINYINT(1)     NOT NULL DEFAULT 0,
  delete_time              DATETIME(3)    NULL,
  PRIMARY KEY (id),
  KEY idx_sub_status (user_id, del_flag, status),
  KEY idx_sub_category (user_id, del_flag, category_id),
  KEY idx_sub_list (user_id, del_flag, create_time),
  KEY idx_sub_name (user_id, del_flag, name),
  CONSTRAINT chk_sub_amount CHECK (amount >= 0),
  CONSTRAINT chk_sub_cny_ref CHECK (cny_reference_amount IS NULL OR cny_reference_amount >= 0),
  CONSTRAINT chk_sub_del CHECK (del_flag IN (0, 1)),
  CONSTRAINT chk_sub_renew_rem CHECK (renewal_reminder_enabled IN (0, 1)),
  CONSTRAINT chk_sub_auto_renew CHECK (auto_renew IN ('YES', 'NO', 'UNKNOWN')),
  CONSTRAINT chk_sub_billing_cycle CHECK (
    (
      billing_cycle_type IN ('MONTHLY', 'YEARLY')
      AND billing_cycle_value IS NULL
    )
    OR
    (
      billing_cycle_type IN ('MULTI_MONTH', 'FIXED_DAYS')
      AND billing_cycle_value > 0
    )
  ),
  CONSTRAINT chk_sub_renew_rem_date CHECK (
    next_renewal_date IS NOT NULL
    OR renewal_reminder_enabled = 0
  ),
  CONSTRAINT chk_sub_status CHECK (
    status IN ('ACTIVE','PAUSED','ENDED')
  ),
  CONSTRAINT chk_sub_del_time CHECK (
    (del_flag = 0 AND delete_time IS NULL)
    OR (del_flag = 1 AND delete_time IS NOT NULL)
  ),
  CONSTRAINT chk_sub_cny_ref_currency CHECK (
    currency <> 'CNY' OR cny_reference_amount IS NULL
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trk_wish (
  id                     BIGINT         NOT NULL,
  user_id                BIGINT         NOT NULL,
  category_id            BIGINT         NOT NULL,
  name                   VARCHAR(64)    NOT NULL,
  expected_price         DECIMAL(18, 6) NOT NULL,
  expected_years         DECIMAL(8, 3)  NOT NULL,
  residual_value         DECIMAL(18, 6) NULL,
  reason                 VARCHAR(512)   NULL,
  remark                 VARCHAR(512)   NULL,
  watch_deadline         DATE           NULL,
  watch_reminder_enabled TINYINT(1)     NOT NULL DEFAULT 0,
  status                 VARCHAR(32)    NOT NULL DEFAULT 'CONSIDERING',
  last_abandon_reason    VARCHAR(256)   NULL,
  last_abandon_at        DATETIME(3)    NULL,
  converted_item_id      BIGINT         NULL,
  conversion_key         VARCHAR(64)    CHARACTER SET ascii COLLATE ascii_bin NULL,
  version                BIGINT         NOT NULL DEFAULT 1,
  create_by              BIGINT         NULL,
  create_time            DATETIME(3)    NOT NULL,
  update_by              BIGINT         NULL,
  update_time            DATETIME(3)    NOT NULL,
  del_flag               TINYINT(1)     NOT NULL DEFAULT 0,
  delete_time            DATETIME(3)    NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wish_conversion (conversion_key),
  KEY idx_wish_status (user_id, del_flag, status),
  KEY idx_wish_list (user_id, del_flag, create_time),
  KEY idx_wish_category (user_id, del_flag, category_id, create_time),
  KEY idx_wish_name (user_id, del_flag, name),
  CONSTRAINT chk_wish_price CHECK (expected_price >= 0),
  CONSTRAINT chk_wish_years CHECK (expected_years > 0),
  CONSTRAINT chk_wish_residual CHECK (residual_value IS NULL OR residual_value >= 0),
  CONSTRAINT chk_wish_del CHECK (del_flag IN (0, 1)),
  CONSTRAINT chk_wish_watch_rem CHECK (watch_reminder_enabled IN (0, 1)),
  CONSTRAINT chk_wish_watch_rem_date CHECK (
    watch_deadline IS NOT NULL
    OR watch_reminder_enabled = 0
  ),
  CONSTRAINT chk_wish_status CHECK (
    status IN ('CONSIDERING','PURCHASED','ABANDONED')
  ),
  CONSTRAINT chk_wish_del_time CHECK (
    (del_flag = 0 AND delete_time IS NULL)
    OR (del_flag = 1 AND delete_time IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trk_outbox_event (
  id             BIGINT       NOT NULL,
  event_id       VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  aggregate_type VARCHAR(32)  NOT NULL,
  aggregate_id   BIGINT       NOT NULL,
  user_id        BIGINT       NOT NULL,
  source_version BIGINT       NOT NULL,
  event_type     VARCHAR(64)  NOT NULL,
  payload_json   JSON         NOT NULL,
  schema_version INT          NOT NULL DEFAULT 1,
  status         VARCHAR(32)  NOT NULL,
  retry_count    INT          NOT NULL DEFAULT 0,
  next_retry_at  DATETIME(3)  NULL,
  locked_by      VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NULL,
  locked_at      DATETIME(3)  NULL,
  last_error     VARCHAR(512) NULL,
  processed_at   DATETIME(3)  NULL,
  create_time    DATETIME(3)  NOT NULL,
  update_time    DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_outbox_event_id (event_id),
  UNIQUE KEY uk_outbox_agg_ver (aggregate_type, aggregate_id, source_version, event_type),
  KEY idx_outbox_relay (status, next_retry_at, id),
  KEY idx_outbox_lock (status, locked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trk_idempotency_record (
  id                   BIGINT       NOT NULL,
  user_id              BIGINT       NOT NULL,
  operation_code       VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  idempotency_key      VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash         CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  response_json        JSON         NULL,
  status               VARCHAR(32)  NOT NULL,
  error_code           VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NULL,
  error_message        VARCHAR(256) NULL,
  processing_expire_at DATETIME(3)  NULL,
  expires_at           DATETIME(3)  NOT NULL,
  create_time          DATETIME(3)  NOT NULL,
  update_time          DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_idem (user_id, operation_code, idempotency_key),
  KEY idx_idem_expire (status, expires_at),
  KEY idx_idem_proc_expire (status, processing_expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- FAILED = terminal business failure: do not replay; same key+hash returns stored failure
-- PROCESSING lease timeout: allow reclaim for technical retry
