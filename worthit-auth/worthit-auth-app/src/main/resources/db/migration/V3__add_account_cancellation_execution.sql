-- M3 account cancellation: execution state and persistent Auth idempotency.

ALTER TABLE auth_account_cancellation
  DROP CHECK chk_cancellation_time,
  DROP INDEX uk_cancellation_pending_user,
  DROP COLUMN pending_user_id,
  ADD COLUMN open_user_id BIGINT
    GENERATED ALWAYS AS (
      CASE WHEN status IN ('PENDING', 'EXECUTING') THEN user_id ELSE NULL END
    ) VIRTUAL AFTER update_time,
  ADD UNIQUE KEY uk_cancellation_open_user (open_user_id),
  ADD CONSTRAINT chk_cancellation_time CHECK (
    (
      status IN ('PENDING', 'EXECUTING')
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
  );

CREATE TABLE auth_idempotency_record (
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
  UNIQUE KEY uk_auth_idem (user_id, operation_code, idempotency_key),
  KEY idx_auth_idem_expire (status, expires_at),
  KEY idx_auth_idem_proc_expire (status, processing_expire_at),
  CONSTRAINT chk_auth_idem_status CHECK (
    status IN ('PROCESSING', 'SUCCEEDED', 'FAILED')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
