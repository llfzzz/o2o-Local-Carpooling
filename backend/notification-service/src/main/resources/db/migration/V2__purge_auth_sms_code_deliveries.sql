-- Login verification codes are no longer inbox messages: the demo login code now lives only in a
-- challenge-bound, login-page-only store inside auth-service. Remove every historical login-code
-- delivery (including expired and previously revealed ones) so the Message Center holds no
-- login-code records of any kind. NotificationService additionally rejects new AUTH_SMS_CODE
-- deliveries, and inbox queries exclude the category defensively.
DELETE FROM notification_deliveries WHERE category = 'AUTH_SMS_CODE';
