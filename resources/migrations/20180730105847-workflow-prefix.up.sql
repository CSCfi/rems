alter table workflow
  add column prefix varchar(255) not null default 'default';
--;;
alter table workflow
  alter column prefix drop default;
