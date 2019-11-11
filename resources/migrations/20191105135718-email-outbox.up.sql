CREATE TABLE email_outbox
(
    id             serial      NOT NULL PRIMARY KEY,
    email          jsonb       NOT NULL,
    created        timestamptz NOT NULL DEFAULT now(),
    latest_attempt timestamptz NULL     DEFAULT NULL,
    latest_error   text        NOT NULL DEFAULT '',
    next_attempt   timestamptz NULL     DEFAULT now(),
    backoff        interval    NOT NULL DEFAULT interval '10 seconds',
    deadline       timestamptz NOT NULL DEFAULT now()
);
