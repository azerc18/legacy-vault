-- K-of-N không còn là một kiểu phân chia riêng (JOINT_CONSENSUS) mà là điều kiện tuỳ chọn của "Chia theo tỷ lệ".
-- Dữ liệu cũ: chuyển các dòng JOINT_CONSENSUS sang PROPORTIONAL chia đều, GIỮ NGUYÊN consensus_threshold /
-- consensus_share / consensus_key_hash / consensus_approved_at (khoá Shamir và các lượt đồng ý đã có vẫn dùng được).
-- Người cuối (theo id) nhận phần làm tròn để tổng luôn đúng 100.00.
-- Giá trị 'JOINT_CONSENSUS' vẫn để trong kiểu enum của cột để không phải đổi kiểu cột; API không nhận giá trị này nữa.
UPDATE `asset_allocations` al
JOIN (
    SELECT `id`,
           ROW_NUMBER() OVER (PARTITION BY `asset_id` ORDER BY `id`) AS rn,
           COUNT(*) OVER (PARTITION BY `asset_id`) AS n
    FROM `asset_allocations`
    WHERE `allocation_mode` = 'JOINT_CONSENSUS'
) x ON x.`id` = al.`id`
SET al.`allocation_mode` = 'PROPORTIONAL',
    al.`share_percent` = CASE
        WHEN x.rn = x.n THEN 100.00 - (x.n - 1) * (FLOOR(10000 / x.n) / 100)
        ELSE FLOOR(10000 / x.n) / 100
    END;
