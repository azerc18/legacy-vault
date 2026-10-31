-- F2: Thêm cột token_jti (UNIQUE) vào bảng dms_checkins để đảm bảo link check-in 1 chạm chỉ dùng được 1 lần duy nhất

ALTER TABLE dms_checkins
    ADD COLUMN token_jti VARCHAR(100) NULL UNIQUE;

