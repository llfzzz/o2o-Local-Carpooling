-- Inbox keyset pagination sorts and cursors on `id` (WHERE user_id = ? [AND id < ?] ORDER BY id DESC),
-- but the existing per-user indexes are keyed on created_at, so MySQL filesorted each page. A
-- (user_id, id) index lets the id-ordered keyset scan run straight off the index. It also serves the
-- category-filtered variant, where `category` becomes a residual filter on the already id-ordered scan.
create index idx_notif_user_id_seq on notification_deliveries (user_id, id);
