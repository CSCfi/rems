DROP TABLE IF EXISTS transfer.user_mapping;

CREATE TABLE transfer.user_mapping (
expandoId integer NOT NULL PRIMARY KEY,
userId varchar(255),
userAttrs jsonb);

INSERT INTO transfer.user_mapping(expandoId)
SELECT DISTINCT er.classPK
FROM transfer.ExpandoRow er;

UPDATE transfer.user_mapping
SET userId = (
    SELECT data_
    FROM transfer.ExpandoTable et
    LEFT OUTER JOIN transfer.ExpandoRow er ON (er.tableId = et.tableId AND er.companyId = et.companyId)
    LEFT OUTER JOIN transfer.ExpandoColumn ec ON (ec.tableId = et.tableId AND ec.companyId = et.companyId)
    LEFT OUTER JOIN transfer.ExpandoValue ev ON (ev.tableId = et.tableId AND ev.companyId = et.companyId AND ev.rowId_ = er.rowId_ AND ev.columnId = ec.columnId)
    WHERE ec.name = 'eppn' AND er.classPK = expandoId AND et.name = 'CUSTOM_FIELDS');

UPDATE transfer.user_mapping
SET userAttrs = ed.userAttrs
FROM (SELECT er.classPK as expandoId, jsonb_object_agg(ec.name, ev.data_) AS userAttrs
      FROM transfer.ExpandoTable et
      LEFT OUTER JOIN transfer.ExpandoRow er ON (er.tableId = et.tableId AND er.companyId = et.companyId)
      LEFT OUTER JOIN transfer.ExpandoColumn ec ON (ec.tableId = et.tableId AND ec.companyId = et.companyId)
      LEFT OUTER JOIN transfer.ExpandoValue ev ON (ev.tableId = et.tableId AND ev.companyId = et.companyId AND ev.rowId_ = er.rowId_ AND ev.columnId = ec.columnId)
      WHERE et.name = 'CUSTOM_FIELDS'
      AND ev.data_ IS NOT NULL
      GROUP BY er.classPK) ed
WHERE user_mapping.expandoId = ed.expandoId;

DELETE FROM public.users CASCADE;

INSERT INTO public.users (userId, userAttrs)
SELECT userId, userAttrs
FROM transfer.user_mapping
WHERE userId IS NOT NULL
ON CONFLICT DO NOTHING;
