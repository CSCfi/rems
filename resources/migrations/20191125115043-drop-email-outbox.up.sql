-- no migration from email_outbox to outbox: all pending emails will be lost
DROP TABLE email_outbox;
