# Event notification

REMS can be configured to send events notifications over HTTP.

## Configuration

See `:event-notification-targets` in [config-defaults.edn](../resources/config-defaults.edn).

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
