-- Idempotent, re-runnable cleanup of EVERY login-code notification, broadened beyond the
-- original AUTH_SMS_CODE purge (V2) to cover equivalent/historical category spellings that an
-- older component might have written. Login verification codes are never user messages; they
-- live only in auth-service's challenge-bound store. This DELETE is safe to run more than once
-- and touches no other (business) notification. Kept in sync with LoginCodeCategories.java.
DELETE FROM notification_deliveries
WHERE category IN ('AUTH_SMS_CODE', 'SMS_CODE', 'LOGIN_CODE', 'VERIFICATION_CODE');
