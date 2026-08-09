-- M3 account cancellation: service-local linearizable user write fence.

CREATE TABLE rem_user_write_fence (
  user_id         BIGINT       NOT NULL,
  status          VARCHAR(32)  NOT NULL,
  cancellation_id VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NULL,
  completed_at    DATETIME(3)  NULL,
  create_time     DATETIME(3)  NOT NULL,
  update_time     DATETIME(3)  NOT NULL,
  PRIMARY KEY (user_id),
  KEY idx_rem_fence_status (status, update_time),
  CONSTRAINT chk_rem_fence_state CHECK (
    (status = 'ACTIVE' AND cancellation_id IS NULL AND completed_at IS NULL)
    OR (status = 'CANCELLING' AND cancellation_id IS NOT NULL AND completed_at IS NULL)
    OR (status = 'CANCELLED' AND cancellation_id IS NOT NULL AND completed_at IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
