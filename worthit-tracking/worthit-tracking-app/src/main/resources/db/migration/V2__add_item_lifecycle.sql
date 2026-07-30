-- Source: 值不值小程序 数据库模型 V0.3.5
-- Baseline: PRD V0.15.6 / 架构 V0.3.17 / 接口 V0.2
-- Keep in sync with docs/数据库文档/值不值小程序_数据库模型_V0.3.5.docx
-- Schema: worthit_tracking M2 lifecycle
-- Do NOT run with M1 V1; apply when M2 ships

CREATE TABLE trk_item_disposal (
  id            BIGINT         NOT NULL,
  user_id       BIGINT         NOT NULL,
  item_id       BIGINT         NOT NULL,
  disposal_type VARCHAR(32)    NOT NULL,
  disposal_date DATE           NOT NULL,
  purchase_price_snapshot DECIMAL(18, 6) NOT NULL,
  sale_amount   DECIMAL(18, 6) NULL,
  remark        VARCHAR(512)   NULL,
  create_time   DATETIME(3)    NOT NULL,
  update_time   DATETIME(3)    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_disposal_item (item_id),
  KEY idx_disposal_user_type (user_id, disposal_type),
  CONSTRAINT chk_disposal_sale CHECK (sale_amount IS NULL OR sale_amount >= 0),
  CONSTRAINT chk_disposal_purchase_snapshot CHECK (purchase_price_snapshot >= 0),
  CONSTRAINT chk_disposal_type CHECK (disposal_type IN ('RETURNED','SOLD','SCRAPPED')),
  CONSTRAINT chk_disposal_sale_by_type CHECK (
    (disposal_type = 'SOLD' AND sale_amount IS NOT NULL)
    OR (disposal_type IN ('RETURNED','SCRAPPED') AND sale_amount IS NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trk_item_replacement (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  old_item_id BIGINT      NOT NULL,
  new_item_id BIGINT      NOT NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_repl_old_item (old_item_id),
  UNIQUE KEY uk_repl_new_item (new_item_id),
  KEY idx_repl_user (user_id),
  CONSTRAINT chk_repl_different CHECK (old_item_id <> new_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
