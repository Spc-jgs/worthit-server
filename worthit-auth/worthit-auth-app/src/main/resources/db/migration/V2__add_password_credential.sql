-- Adds first-class username/password login for App, H5 and local development.

CREATE TABLE auth_password_credential (
  user_id       BIGINT       NOT NULL,
  username      VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  create_time   DATETIME(3)  NOT NULL,
  update_time   DATETIME(3)  NOT NULL,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_password_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
