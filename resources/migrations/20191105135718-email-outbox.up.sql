CREATE TABLE email_outbox
(
    id                 serial      NOT NULL PRIMARY KEY,
    email              jsonb       NOT NULL,
    created            timestamptz NOT NULL DEFAULT now(),
    latest_attempt     timestamptz,
    latest_error       text        NOT NULL DEFAULT '',
    remaining_attempts integer     NOT NULL DEFAULT 1
        CONSTRAINT positive_remaining_attempts CHECK ( remaining_attempts >= 0 )
);
