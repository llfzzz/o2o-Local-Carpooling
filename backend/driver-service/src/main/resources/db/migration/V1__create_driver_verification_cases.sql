CREATE TABLE IF NOT EXISTS driver_verification_cases (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  case_id VARCHAR(64) NOT NULL UNIQUE,
  user_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  uploaded_file_ids_json JSON NOT NULL,
  ocr_result_json JSON NOT NULL,
  submitted_at TIMESTAMP(3) NOT NULL,
  reviewed_at TIMESTAMP(3) NULL,
  reviewer_id VARCHAR(64) NULL,
  review_reason VARCHAR(500) NULL,
  INDEX idx_driver_verification_user_id (user_id),
  INDEX idx_driver_verification_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
