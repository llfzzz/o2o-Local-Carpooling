CREATE TABLE IF NOT EXISTS ocr_tasks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id VARCHAR(64) NOT NULL UNIQUE,
  file_object_id VARCHAR(64) NOT NULL,
  result_json JSON NOT NULL,
  completed_at TIMESTAMP(3) NOT NULL,
  INDEX idx_ocr_tasks_file_completed (file_object_id, completed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
