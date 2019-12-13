UPDATE application_event
SET eventdata = jsonb_set(eventdata, '{workflow/type}', '"workflow/dynamic"')
WHERE eventdata ->> 'event/type' = 'application.event/created'
  AND eventdata ->> 'workflow/type' = 'workflow/default';
--;;
UPDATE workflow
SET workflowbody = jsonb_set(workflowbody, '{type}', '"workflow/dynamic"')
WHERE workflowbody ->> 'type' = 'workflow/default';
