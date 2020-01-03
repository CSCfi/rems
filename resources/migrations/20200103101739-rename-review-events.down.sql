UPDATE application_event
SET eventdata = jsonb_set(eventdata, '{event/type}', '"application.event/commented"')
WHERE eventdata ->> 'event/type' = 'application.event/reviewed';
--;;
UPDATE application_event
SET eventdata = jsonb_set(eventdata, '{event/type}', '"application.event/comment-requested"')
WHERE eventdata ->> 'event/type' = 'application.event/review-requested';
