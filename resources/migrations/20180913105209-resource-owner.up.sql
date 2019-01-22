alter table resource
  add column ownerUserId varchar(255);
--;;
update resource
  set ownerUserId = modifierUserId;
--;;
alter table resource
  alter column ownerUserId set not null;
