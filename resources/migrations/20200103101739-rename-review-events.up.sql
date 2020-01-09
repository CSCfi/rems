UPDATE application_event
SET eventdata = jsonb_set(eventdata, '{event/type}', '"application.event/reviewed"')
WHERE eventdata ->> 'event/type' = 'application.event/commented';
--;;
UPDATE application_event
SET eventdata =
            jsonb_set(
                    jsonb_set(eventdata,
                              '{event/type}', '"application.event/review-requested"'),
                    '{application/reviewers}', eventdata -> 'application/commenters')
            - 'application/commenters'
WHERE eventdata ->> 'event/type' = 'application.event/comment-requested';
