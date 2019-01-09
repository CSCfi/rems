CREATE TYPE application_event_type AS ENUM (
  'apply',   -- draft or returned --> applied
  'approve', -- applied --> applied or approved
  'autoapprove', -- like approve but when there are no approvers for the round
  'reject',  -- applied --> rejected
  'return',   -- applied --> returned
  'review',   -- applied --> applied or approved
  'review-request', -- applied --> applied
  'withdraw',   -- applied --> withdrawn
  'close',   -- any --> closed
  'third-party-review' -- applied --> applied
);
--;;
-- I'm sorry, you're going to lose all unsupported events
DELETE FROM application_event
  WHERE event NOT IN (SELECT unnest(enum_range(NULL::application_event_type))::text);
--;;
ALTER TABLE application_event
  ALTER COLUMN event TYPE application_event_type
    USING CAST(event AS application_event_type);
