ALTER TABLE file_objects
  ADD COLUMN upload_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE' AFTER visibility,
  ADD COLUMN upload_expires_at TIMESTAMP(3) NULL AFTER upload_status,
  ADD COLUMN uploaded_at TIMESTAMP(3) NULL AFTER upload_expires_at;

CREATE INDEX idx_file_upload_status ON file_objects (upload_status, upload_expires_at);
