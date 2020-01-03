UPDATE application_event
SET eventdata = jsonb_set(eventdata, '{event/type}', '"application.event/reviewed"')
WHERE eventdata ->> 'event/type' = 'application.event/commented';
--;;
UPDATE application_event
SET eventdata = jsonb_set(eventdata, '{event/type}', '"application.event/review-requested"')
WHERE eventdata ->> 'event/type' = 'application.event/comment-requested';
