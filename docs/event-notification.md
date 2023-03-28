# Event notification

REMS can be configured to send events notifications over HTTP.

Event notifications are performed _one at a time_, waiting for a HTTP
200 response from the notification endpoint. Everything except a HTTP
200 response counts as a failure. Failed notifications are retried
with exponential backoff for up to 12 hours.

Due to retries, the event notification endpoint _should be_ idempotent.

Initial event notifications for each event are _guaranteed to be in
order_ of event creation, but retries might be out-of-order.

Event notification failures are logged. You can also inspect the
`outbox` db table for the retry state of notifications.

## Configuration

See `:event-notification-targets` in [config-defaults.edn](../resources/config-defaults.edn).

## Schema

The body of the HTTP PUT request will be a JSON object that contains:

- `"event/type"`: the type of the event, a string
- `"event/actor"`: who caused this event
- `"event/id"`: unique event id
- `"event/time"`: when the event occured
- `"application/id"`: the id of the application
- `"event/application"`: the entire application, with this event applied, in the same format as the `/api/applications/:id/raw` endpoint returns (see Swagger docs)
  This can be left out with `:send-application? false` in the configuration.

Other keys may also be present depending on the event type.

## Examples

See [Bona fide pusher for an example use case](../resources/addons/bona-fide-pusher/README.md)
