-- Ngôn ngữ người dùng muốn nhận email (VI hoặc EN). Người dùng có sẵn mặc định tiếng Việt.
ALTER TABLE users ADD COLUMN preferred_language VARCHAR(5) NOT NULL DEFAULT 'VI';
