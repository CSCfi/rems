# Event notification

REMS can be configured to send events notifications over HTTP.

## Configuration

The configuration option `:event-notification-targets` should be an array of targets, each containing:
- `:url`, the URL to send HTTP PUT methods to
- `:event-types`, an array of event types to send. This is optional, and a missing value means "send everything".

An example:
```
:event-notification-targets [{:url "http://events/everything"}
                             {:url "http://events/filtered"
                              :event-types [:application.event/created :application.event/submitted]}]
```

## Schema

The body of the HTTP PUT request will be a JSON object that contains:

- `"event/type"`: the type of the event, a string
- `"event/actor"`: who caused this event
- `"event/time"`: when the event occured
- `"application/id"`: the id of the application
- `"event/application"`: the state of the application, in the same format as the `/api/applications/:id/raw` endpoint returns (see Swagger docs)

Other keys may also be present depending on the event type.

## Error handling

Event notifications are retried with exponential backoff for up to 12
hours. Everything except a HTTP 200 status counts as a failure.
Failures are logged. You can also inspect the `outbox` db table for
the retry state of notifications.
