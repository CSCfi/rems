CREATE TABLE blacklist_event (
  id serial NOT NULL PRIMARY KEY,
  eventdata jsonb
);
--;;
CREATE INDEX index_blacklist_event_resource_user ON blacklist_event(
  (eventdata->>'blacklist/resource'),
  (eventdata->>'blacklist/user')
)
