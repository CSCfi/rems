-- :disable-transaction
-- IF NOT EXISTS because we don't have a corresponding down migration
ALTER TYPE itemtype
  ADD VALUE IF NOT EXISTS 'multiselect';
