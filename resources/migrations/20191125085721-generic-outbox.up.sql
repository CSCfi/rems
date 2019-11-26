CREATE TABLE outbox (
  id serial NOT NULL PRIMARY KEY,
  outboxData jsonb NOT NULL
)
