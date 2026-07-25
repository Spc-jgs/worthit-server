-- Source: 值不值小程序 数据库模型 V0.3.4
-- Baseline: PRD V0.15.6 / 架构 V0.3.14
-- Keep in sync with docs/数据库文档/值不值小程序_数据库模型_V0.3.4.docx
-- Schema: worthit_auth

CREATE TABLE auth_user (
  id             BIGINT       NOT NULL,
  nickname       VARCHAR(64)  NULL,
  avatar_file_id BIGINT       NULL,
  status         VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  create_time    DATETIME(3)  NOT NULL,
  update_time    DATETIME(3)  NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_external_identity (
  id               BIGINT       NOT NULL,
  user_id          BIGINT       NOT NULL,
  identity_type    VARCHAR(32)  NOT NULL,
  app_id           VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  external_subject VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  union_id         VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
  verified         TINYINT(1)   NOT NULL DEFAULT 0,
  create_time      DATETIME(3)  NOT NULL,
  update_time      DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ext_identity (identity_type, app_id, external_subject),
  KEY idx_ext_user (user_id),
  CONSTRAINT chk_ext_verified CHECK (verified IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- WECHAT_MINI: app_id = real mini-program AppId
-- MOBILE: app_id = 'GLOBAL'

CREATE TABLE auth_login_audit (
  id             BIGINT       NOT NULL,
  user_id        BIGINT       NULL,
  identity_type  VARCHAR(32)  NOT NULL,
  result         VARCHAR(32)  NOT NULL,
  fail_reason    VARCHAR(128) NULL,
  ip             VARCHAR(64)  NULL,
  device_summary VARCHAR(256) NULL,
  trace_id       VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NULL,
  create_time    DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_login_user_time (user_id, create_time),
  KEY idx_login_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_account_cancellation (
  id              BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  apply_at        DATETIME(3)  NOT NULL,
  effective_at    DATETIME(3)  NOT NULL,
  completed_at    DATETIME(3)  NULL,
  status          VARCHAR(32)  NOT NULL,
  revoked_at      DATETIME(3)  NULL,
  version         BIGINT       NOT NULL DEFAULT 1,
  create_time     DATETIME(3)  NOT NULL,
  update_time     DATETIME(3)  NOT NULL,
  pending_user_id BIGINT
    GENERATED ALWAYS AS (
      CASE WHEN status = 'PENDING' THEN user_id ELSE NULL END
    ) VIRTUAL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cancellation_pending_user (pending_user_id),
  KEY idx_cancel_user (user_id),
  KEY idx_cancel_status_effective (status, effective_at),
  CONSTRAINT chk_cancellation_time CHECK (
    (
      status = 'PENDING'
      AND completed_at IS NULL
      AND revoked_at IS NULL
    )
    OR
    (
      status = 'COMPLETED'
      AND completed_at IS NOT NULL
      AND revoked_at IS NULL
    )
    OR
    (
      status = 'REVOKED'
      AND revoked_at IS NOT NULL
      AND completed_at IS NULL
    )
  ),
  CONSTRAINT chk_cancellation_effective CHECK (effective_at >= apply_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
