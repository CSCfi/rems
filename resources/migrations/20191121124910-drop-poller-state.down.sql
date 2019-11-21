CREATE TABLE poller_state (
  name varchar(64) primary key,
  state jsonb not null
);
