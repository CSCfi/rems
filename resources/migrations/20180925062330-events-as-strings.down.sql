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
ALTER TABLE application_event
  ALTER COLUMN event TYPE application_event_type
    USING CAST(event AS application_event_type);
--;;
