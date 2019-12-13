UPDATE application_event
SET eventdata = jsonb_set(eventdata, '{workflow/type}', '"workflow/default"')
WHERE eventdata ->> 'event/type' = 'application.event/created'
  AND eventdata ->> 'workflow/type' = 'workflow/dynamic';
--;;
UPDATE workflow
SET workflowbody = jsonb_set(workflowbody, '{type}', '"workflow/default"')
WHERE workflowbody ->> 'type' = 'workflow/dynamic';
