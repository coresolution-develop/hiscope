-- Fix broken seed password hash for demo accounts.
-- password123 -> $2a$10$4xz0wb3IviKPspSjvh2WcOCrL89BxHjLMKdxohCgjK3XZtVYiA6Ee

UPDATE accounts
SET password_hash = '$2a$10$4xz0wb3IviKPspSjvh2WcOCrL89BxHjLMKdxohCgjK3XZtVYiA6Ee'
WHERE login_id IN ('super', 'admin', 'admin2')
  AND password_hash = '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.';

UPDATE user_accounts
SET password_hash = '$2a$10$4xz0wb3IviKPspSjvh2WcOCrL89BxHjLMKdxohCgjK3XZtVYiA6Ee'
WHERE login_id LIKE 'emp0%'
  AND password_hash = '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.';
