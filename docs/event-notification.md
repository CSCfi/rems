# Event notification

REMS can be configured to send events notifications over HTTP.

Event notifications are sent _one at a time_, waiting for a HTTP
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

## Basics

- To react you must first check the event type to see if it's interesting.
- Always return HTTP 200 `"OK"` if the message was received. Otherwise it will be retried.
- Use the application state from under key `event/application` or consider not requesting it at all.

### Case: Listening to "new" applications

Most handling organizations will only want to listen to `SubmittedEvent`. This is the point when an applicant has deemed the application ready for review. By default the application is not visible to anyone else before this.

### Case: Listening to "closed" applications

Applications that reach an "end state" are such that there will be one of these events:
- `ApprovedEvent` normal
- `RejectedEvent` normal
- `ClosedEvent` normal
- `RevokedEvent` misbehavior by user (not always in use)
- `DeletedEvent` applicant deleted the application or it has expired (not always in use)

NB: `DecidedEvent` is sent when a potential decider has marked the decision they've made. The application is not yet considered approved or rejected.

## Event Types

The body of the HTTP PUT request will be a JSON object that contains at a minimum:

- Properties of `EventBase`
- Optionally the properties of `EventWithcomment`
- Properties of the concrete event type (e.g. `SubmittedEvent`)
- `event/application`: Optionally the entire application, with this event applied, in the same format as the `/api/applications/:id/raw` endpoint returns (see Swagger docs)
  This can be left out with `:send-application? false` in the configuration but it is included by default as many integrations would need to fetch some of it anyway.

### EventBase

The `EventBase` forms the base event data that is common for all events. Each event can have more specific data such as comment, attachments, person invited etc.

- `event/id`  A unique event identifier, a sequential growing number
- `event/type` Type of the event. A keyword.
- `event/actor`: Who caused the event, e.g. did an action. A string whose value is derived from the IdP identity attributes.
- `event/time` When the event occured.
- `application/id`: A unique identifier of the application this event belongs to. A number.

### EventWithComment

This is a further base type for events that the actor can comment or add attachments to.
Many different event types support these.

- `application/comment` Optional comment text
- `event/attachments` Optional array of attachment ids (numbers)

### ApprovedEvent

The `ApprovedEvent` happens when a handler or decider (depending on workflow type) approves the application.

- `event/type` always `application.event/approved`
- `entitlement/end` Optional time when the entitlement will expire.

```json
{
  "event/id": 668,
  "event/type": "application.event/approved",
  "event/actor": "HIJ123XYZ789",
  "event/time": "2023-12-22T08:01:53.606Z",
  "application/comment": "Thank you! Approved!",
  "application/id": 136,
  "event/application": {...}
}
```

NB: can include comments

### ClosedEvent

The `ClosedEvent` happens when either the applicant or a handler closes the application for good. An application can't be re-opened. This could be for example when a research project ends and the access rights are not required anymore.

- `event/type` always `application.event/closed`

No additional fields.

NB: can include comments

```json
{
  "event/id": 693,
  "event/type": "application.event/closed",
  "event/actor": "HIJ123XYZ789",
  "event/time": "2023-12-22T08:01:54.107Z",
  "application/comment": "Research project complete, closing.",
  "application/id": 139,
  "event/application": {...}
}
```

### ReviewedEvent

The `ReviewedEvent` happens when a reviewer has feedback on the application. This could be in the form of the comment or an attachment.

- `event/type` always `application.event/reviewed`
- `application/request-id` A unique identifier for the review request event that this is a review for. A string

NB: can include comments

```json
{
  "event/id": 744,
  "event/type": "application.event/reviewed",
  "event/actor": "RST123XYZ123",
  "event/time": "2023-12-22T08:01:55.397Z",
  "event/attachments": [{
    "attachment/id": 34
  }],
  "application/id": 149,
  "application/comment": "here are my thoughts. see attachments for details",
  "application/request-id": "a13c67ea-4a92-4083-bd0e-b6fe9f046208"
  "event/application": {...}
}
```

### ReviewRequestedEvent

The `ReviewRequestedEvent` happens when a handler has requested another person to review the application. The reviewers are existing REMS users.

- `event/type` always `application.event/review-requested`
- `application/request-id` A unique identifier for the review request. A string
- `application/reviewers` Array of user identities asked to review the application.

NB: can include comments

```json
{
  "event/id": 743,
  "event/type": "application.event/review-requested",
  "event/actor": "HIJ123XYZ789",
  "event/time": "2023-12-22T08:01:55.382Z",
  "event/attachments": [{
    "attachment/id": 33
  }],
  "application/id": 149,
  "application/comment": "please have a look. see attachment for details",
  "application/reviewers": [ "RST123XYZ123" ],
  "application/request-id": "a13c67ea-4a92-4083-bd0e-b6fe9f046208",
  "event/application": {...}
}
```

### CopiedFromEvent

The `CopiedFromEvent` is a technical event that happens when an application is copied by the applicant. The new application will record from where it came from.

- `event/type` always `application.event/copied-from`
- `application/copied-from` This application is copied from an existing application. The value is a map of `application/id` (number) and `application/external-id` (string) that identify the application.

```json
{
  "event/id": 922,
  "event/type": "application.event/copied-from",
  "event/actor": "ABC123XYZ456",
  "event/time": "2023-12-22T08:02:11.925Z",
  "application/id": 186,
  "application/copied-from": {
    "application/id": 185,
    "application/external-id": "abc123"
  },
  "event/application": {...}
}
```

### CopiedToEvent

The `CopiedToEvent` is a technical event that happens when an application is copied by the applicant. The old application will record where it was copied to.

- `event/type` always `application.event/copied-to`
- `application/copied-to` This application is copied to an existing application. The value is a map of `application/id` (number) and `application/external-id` (string) that identify the application.

```json
{
  "event/id": 923,
  "event/type": "application.event/copied-to",
  "event/actor": "ABC123XYZ456",
  "event/time": "2023-12-22T08:02:11.925Z",
  "application/id": 185,
  "application/copied-to": {
    "application/id": 186,
    "application/external-id": "2023/2"
  },
  "event/application": {...}
}
```

### CreatedEvent

The `CreatedEvent` happens whenever a new application is created, either from scratch or as a copy of an existing one.

The `CreatedEvent` could be reacted to, for example, to pre-fill some application fields. Overall a created application is still a draft and likely uninteresting to the handling organization until it is actually submitted for processing.

- `event/type` always `application.event/created`
- `application/external-id` External identifier of the application (a default sequential value generated in REMS). A string
- `application/resources` An array of maps with `catalogue-item/id` (number) and `:resource/ext-id` (string).
- `application/licenses` An array of maps with license identifiers `license/id` (number).
- `application/forms` An array of maps with form identifiers `form/id` (number).
- `workflow/id` The workflow identifgier (number)
- `workflow/type` The type of the workflow (string)

```json
{
  "event/id": 924,
  "event/type": "application.event/created",
  "event/actor": "ABC123XYZ456",
  "event/time": "2023-12-22T08:02:12.089Z",
  "workflow/id": 191,
  "workflow/type": "workflow/default",
  "application/id": 187,
  "application/external-id": "2023/1",
  "application/resources": [{
    "catalogue-item/id": 179
    "resource/ext-id": "urn:uuid:284f585e-9004-4d05-9a3e-d4d740092ea4",
  }],
  "application/forms": [{
    "form/id": 182
  }],
  "application/licenses": [],
  "event/application": {...}
}
```

### DecidedEvent

The `DecidedEvent` event happens when a decider has marked their decision. After this the application will still be approved or rejected.

- `event/type` always `application.event/decided`
- `application/request-id` A unique identifier for the review request event that this is a review for. A string
- `application/decision` The decision. Either `approved` or `rejected` (string).

NB: can include comments

```json
{
  "event/id": 6,
  "event/type": "application.event/decided",
  "event/actor": "DEF123XYZ789",
  "event/time": "2003-01-01T00:00:00.000Z",
  "application/id": 1,
  "application/comment": "I have decided",
  "application/decision": "approved",
  "application/request-id": "7ecedf2d-7bd6-4fc5-8b81-a9c38b139550",
  "event/application": {...}
}
```

### DecisionRequestedEvent

- `event/type` always `application.event/decision-requested`
- `application/request-id` A unique identifier for the review request event that this is a review for. A string
- `application/deciders` An array of user identifies whom a decision is requested from

NB: can include comments

```json
{
  "event/id": 14,
  "event/type": "application.event/decision-requested",
  "event/actor": "HIJ123XYZ123",
  "event/time": "2003-01-01T00:00:00.000Z",
  "application/id": 2,
  "application/comment": "please decide",
  "application/deciders": [ "DEF123XYZ789" ],
  "application/request-id": "1bc3d6e9-be4f-4c2d-ab23-1cb0d3f7d123",
  "event/application": {...}
}
```

### DeletedEvent

- `event/type` always `application.event/deleted`

No additional fields.

```json
{
  "event/id" : 816,
  "event/type" : "application.event/deleted",
  "event/actor" : "expirer-bot",
  "event/time" : "2023-01-11T00:00:00.000Z",
  "application/id" : 162,
  "event/application": {...}
}
```

### DraftSavedEvent

- `event/type` always `application.event/draft-saved`
- `application/field-values` An array of maps with `form`, `field` and `value` recording the answers.
- `application/duo-codes` An optional array of `DuoCode`.

```json
{
  "event/id": 741,
  "event/type": "application.event/draft-saved",
  "event/actor": "ABC123XYZ456",
  "event/time": "2023-12-22T08:01:55.342Z",
  "application/id": 149,
  "application/field-values": [{
    "form": 140,
    "field": "fld1",
    "value": "complicated application with lots of attachments and five special characters \"åöâīē\""
  }, {
    "form": 140,
    "field": "fld2",
    "value": "32"
  }],
  "event/application": {...}
}
```

### ExternalIdAssignedEvent

- `event/type` always `application.event/external-id-assigned`
- `application/external-id` External identifier of the application (a default sequential value generated in REMS). A string

```json
{
  "event/id": 903,
  "event/type": "application.event/external-id-assigned",
  "event/actor": "HIJ123XYZ789",
  "event/time": "2023-12-22T08:02:11.455Z",
  "application/external-id": "abc123",
  "application/id": 185,
  "event/application": {...}
}
```

### ExpirationNotificationsSentEvent

- `event/type` always `application.event/expiration-notifications-sent`
- `application/expires-on` When the application will expire (and be deleted).

```json
{
  "event/id": 815,
  "event/type": "application.event/expiration-notifications-sent",
  "event/actor": "expirer-bot",
  "event/time": "2023-01-01T00:00:00.000Z",
  "application/id": 162,
  "application/expires-on": "2023-01-08T00:00:00.000Z",
  "event/application": {...}
}
```

### LicensesAcceptedEvent

- `event/type` always `application.event/licenses-accepted`
- `application/accepted-licenses` An array of license identifiers that were accepted.

```json
{
  "event/id": 900,
  "event/type": "application.event/licenses-accepted",
  "event/actor": "ABC123XYZ456",
  "event/time": "2023-12-22T08:02:11.309Z",
  "application/id": 185,
  "application/accepted-licenses": [ 46, 50, 47 ],
  "event/application": {...}
}
```

### LicensesAddedEvent

- `event/type` always `application.event/licenses-added`
- `application/licenses` An array of licenses that were added with identifiers `license/id` (number)

NB: can include comments

```json
{
  "event/id": 911,
  "event/type": "application.event/licenses-added",
  "event/actor": "HIJ123XYZ789",
  "event/time": "2023-12-22T08:02:11.699Z",
  "application/id": 185,
  "application/comment": "Please approve these new terms",
  "application/licenses": [{
    "license/id": 49
  }]
  "event/application": {...}
}
```

### MemberAddedEvent

- `event/type` always `application.event/member-added`
- `application/member` The member that was added as a map identified by the `userid` attribute.

```json
{
  "event/id": 963,
  "event/type": "application.event/member-added",
  "event/actor": "HIJ123XYZ789",
  "event/time": "2023-12-22T08:02:14.585Z",
  "application/id": 195,
  "application/member": {
    "userid": "DEF123XYZ123"
  },
  "event/application": {...}
}
```

### MemberInvitedEvent

- `event/type` always `application.event/member-invited`
- `application/member` A map describing the person invited `name` (string) and `email` (string)
- `invitation/token` A unique token with which this invitation can be accepted.

```json
{
  "event/id": 739,
  "event/type": "application.event/member-invited",
  "event/actor": "ABC123XYZ456",
  "event/time": "2023-12-22T08:01:55.326Z",
  "application/member": {
    "email": "mary.jones@test_example.org",
    "name": "Mary K. Jones"
  },
  "invitation/token": "115294a9c0a108a1fd703a35621eb848",
  "application/id": 149,
  "event/application": {...}
}
```

### MemberJoinedEvent

- `event/type` always `application.event/member-joined`
- `invitation/token` A unique token with which this invitation can be accepted.

```json
{
  "event/id": 740
  "event/type": "application.event/member-joined",
  "event/actor": "RST123XYZ123",
  "event/time": "2023-12-22T08:01:55.336Z",
  "invitation/token": "115294a9c0a108a1fd703a35621eb848",
  "application/id": 149,
  "event/application": {...}
}
```

### MemberRemovedEvent

- `event/type` always `application.event/member-removed`
- `application/member` The member that was removed as a map identified by the `userid` attribute.

NB: can include comments

```json
{
  "event/id": 756
  "event/type": "application.event/member-removed",
  "event/actor": "OPQ123XYZ456",
  "event/time": "2050-01-01T00:00:00.000Z",
  "application/id": 150,
  "application/comment": "Left team",
  "application/member": {
    "userid": "EFG123XYZ789"
  },
  "event/application": {...}
}
```

### MemberUninvitedEvent

- `event/type` always `application.event/member-uninvited`
- `application/member` A map describing the person uninvited `name` (string) and `email` (string)

NB: can include comments

```json
```

### AttachmentsRedactedEvent

- `event/type` `application.event/attachments-redacted`
- `event/redacted-attachments` An array of maps with `attachment/id` identifying the attachment.
- `event/public` Whether this event is shown to the applying users (applicant, members) (boolean)

NB: can include comments

```json
{
  "event/id": 748,
  "event/type": "application.event/attachments-redacted",
  "event/actor": "RST123XYZ123",
  "event/time": "2023-12-22T08:01:55.498Z",
  "event/public": true,
  "event/redacted-attachments": [{
    "attachment/id": 36
  }, {
    "attachment/id": 37
  }],
  "event/attachments": [{
    "attachment/id": 40
  }, {
    "attachment/id": 41
  }],
  "application/id": 149,
  "application/comment": "updated the process documents to latest version",
  "event/application": {...}
}
```

### ApplicantChangedEvent

- `event/type` always `application.event/applicant-changed`
- `application/applicant` The member that was made the applicant as a map identified by the `userid` attribute.

NB: can include comments

```json
{
  "event/id": 985,
  "event/type": "application.event/applicant-changed",
  "event/actor": "DEF123XYZ123",
  "event/time": "2023-12-22T08:02:16.160Z",
  "application/id": 201,
  "application/applicant": {
    "userid": "CEF123XYZ789"
  },
  "event/application": {...}
}
```

### ReviewerInvitedEvent

- `event/type` always `application.event/reviewer-invited`
- `application/reviewer` A map describing the person invited `name` (string) and `email` (string)
- `invitation/token` A unique token with which this invitation can be accepted.

NB: can include comments

```json
{
  "event/id": 875,
  "event/type": "application.event/reviewer-invited",
  "event/actor": "DEF123XYZ123",
  "event/time": "2023-12-22T08:02:10.065Z",
  "invitation/token": "1e33b5a8f4fe5deaf2abbea36f44060a",
  "application/id": 178,
  "application/reviewer": {
    "email": "joe@example.com",
    "name": "Joe Smithee"
  },
  "event/application": {...}
}
```

### ReviewerJoinedEvent

- `event/type` always `application.event/reviewer-joined`
- `application/request-id` A unique identifier for the review request event that this is a review for. A string
- `invitation/token` A unique token with which this invitation can be accepted.

```json
{
  "event/id": 876,
  "event/type": "application.event/reviewer-joined",
  "event/actor": "RST123XYZ123",
  "event/time": "2023-12-22T08:02:10.100Z",
  "invitation/token": "1e33b5a8f4fe5deaf2abbea36f44060a",
  "application/id": 178,
  "application/request-id": "988f78a2-aca4-4fff-9095-2e3092474a49",
  "event/application": {...}
}
```

### DeciderInvitedEvent

- `event/type` always `application.event/decider-invited`
- `application/decider` A map describing the person invited `name` (string) and `email` (string)
- `invitation/token` A unique token with which this invitation can be accepted.

NB: can include comments

```json
{
  "event/id": 877,
  "event/type": "application.event/decider-invited",
  "event/actor": "DEF123XYZ123",
  "event/time": "2023-12-22T08:02:10.133Z",
  "invitation/token": "bcc6d5046b00dd56205fe47719adfb37",
  "application/id": 178,
  "application/decider": {
    "email": "janet@example.com",
    "name": "Janet Smith"
  },
  "event/application": {...}
}
```

### DeciderJoinedEvent

- `event/type` always `application.event/decider-joined`
- `application/request-id` A unique identifier for the review request event that this is a review for. A string
- `invitation/token` A unique token with which this invitation can be accepted.

```json
{
  "event/id": 878,
  "event/type": "application.event/decider-joined",
  "event/actor": "DEF123XYZ123",
  "event/time": "2023-12-22T08:02:10.160Z",
  "invitation/token": "bcc6d5046b00dd56205fe47719adfb37",
  "application/id": 178,
  "application/request-id": "7703f4e6-b548-439b-87bb-8fc65b114174",
  "event/application": {...}
}
```

### RejectedEvent

- `event/type` always `application.event/rejected`

No additional fields.

NB: can include comments

```json
{
  "event/id": 673,
  "event/type": "application.event/rejected",
  "event/actor": "HIJ123XYZ789",
  "event/time": "2023-12-22T08:01:53.693Z",
  "application/id": 137,
  "application/comment": "Never going to happen",
  "event/application": {...}
}
```

### RemarkedEvent

- `event/type` always `application.event/remarked`
- `event/public` Whether this event is shown to the applying users (applicant, members) (boolean)

NB: can include comments

```json
{
  "event/id": 680,
  "event/type": "application.event/remarked",
  "event/actor": "RST123XYZ123",
  "event/time": "2023-12-22T08:01:53.823Z",
  "event/public": true,
  "application/id": 138,
  "application/comment": "application is missing key purpose",
  "event/application": {...}
}
```

### ResourcesChangedEvent

- `event/type` always `application.event/resources-changed`
- `application/resources` An array of maps with `catalogue-item/id` (number) and `:resource/ext-id` (string).
- `application/licenses` An array of maps with license identifiers `license/id` (number).
- `application/forms` An array of maps with form identifiers `form/id` (number).

NB: can include comments

```json
{
  "event/id": 757,
  "event/type": "application.event/resources-changed",
  "event/actor": "OPQ123XYZ456",
  "event/time": "2050-01-01T00:00:00.000Z",
  "application/id": 150,
  "application/comment": "Removed second resource, added third resource",
  "application/resources": [{
    "resource/ext-id": "resource1",
    "catalogue-item/id": 134
  }, {
    "resource/ext-id": "resource3",
    "catalogue-item/id": 136
  }],
  "application/forms": [{
    "form/id": 141
  }],
  "application/licenses": [{
    "license/id": 41
  }],
  "event/application": {...}
}
```

### ReturnedEvent

- `event/type` always `application.event/returned`

No additional fields.

NB: can include comments

```json
{
  "event/id": 904,
  "event/type": "application.event/returned",
  "event/actor": "HIJ123XYZ789",
  "event/time": "2023-12-22T08:02:11.477Z",
  "application/id": 185,
  "application/comment": "Please check again",
  "event/application": {...}
}
```

### RevokedEvent

- `event/type` always `application.event/revoked`

No additional fields.

NB: can include comments

```json
{
  "event/id": 767,
  "event/type": "application.event/revoked",
  "event/actor": "OPQ123XYZ456",
  "event/time": "2050-01-01T00:00:00.000Z",
  "application/id": 152,
  "application/comment": "Banned",
  "event/application": {...}
}
```

### SubmittedEvent

- `event/type` always `application.event/submitted`

No additional fields.

```json
{
  "event/id": 802
  "event/type": "application.event/submitted",
  "event/actor": "ABC123XYZ456",
  "event/time": "2023-12-22T08:02:05.493Z",
  "application/id": 161,
  "event/application": {...}
}
```

### VotedEvent

- `event/type` always `application.event/voted`
- `vote/value` The vote given (string)

NB: can include comments
```json
{
  "event/id": 943,
  "event/type": "application.event/voted",
  "event/actor": "HIJ123XYZ789",
  "event/time": "2023-12-22T08:02:13.143Z",
  "vote/value": "Accept",
  "application/id": 189,
  "event/application": {...}
}
```

### Example of `event/application`

The application itself should look something like this:

```json
{
  "application/user-roles": {
    "ABC123XYZ456": [
      "applicant"
    ],
    "MNO123XYZ456": [
      "member"
    ],
    "CDE123XYZ456": [
      "reviewer"
    ],
    "DEF123XYZ789": [
      "handler"
    ],
    "HIJ123XYZ789": [
      "handler"
    ],
    "rejecter-bot": [
      "handler"
    ],
    "RST123XYZ123": [
      "reporter"
    ],
    "expirer-bot": [
      "expirer"
    ]
  },
  "application/workflow": {
    "workflow/id": 1,
    "workflow/type": "workflow/default",
    "workflow.dynamic/handlers": [
      {
        "userid": "DEF123XYZ789",
        "name": "Developer",
        "email": "developer@example.com"
      },
      {
        "userid": "HIJ123XYZ789",
        "name": "Hannah Handler",
        "email": "handler@example.com",
        "handler/active?": true
      },
      {
        "userid": "rejecter-bot",
        "name": "Rejecter Bot",
        "email": null
      }
    ]
  },
  "application/external-id": "2023/14",
  "application/first-submitted": "2023-10-31T07:17:52.505Z",
  "application/blacklist": [],
  "application/id": 14,
  "application/applicant": {
    "userid": "ABC123XYZ456",
    "name": "Alice Applicant",
    "email": "alice@example.com",
    "researcher-status-by": "so"
  },
  "application/todo": "waiting-for-review",
  "application/members": [
    {
      "userid": "MNO123XYZ456",
      "name": "Malice Applicant",
      "email": "malice@example.com"
    }
  ],
  "application/resources": [
    {
      "catalogue-item/end": null,
      "catalogue-item/expired": false,
      "catalogue-item/enabled": true,
      "resource/id": 1,
      "catalogue-item/title": {
        "en": "Default workflow with public and private fields",
        "fi": "Testityövuo julkisilla ja yksityisillä lomakekentillä"
      },
      "catalogue-item/infourl": {},
      "resource/ext-id": "urn:nbn:fi:lb-201403262",
      "catalogue-item/start": "2023-10-31T07:17:52.340Z",
      "catalogue-item/archived": false,
      "catalogue-item/id": 14
    },
    {
      "catalogue-item/end": null,
      "catalogue-item/expired": false,
      "catalogue-item/enabled": true,
      "resource/id": 2,
      "catalogue-item/title": {
        "en": "Default workflow with private form",
        "fi": "Oletustyövuo yksityisellä lomakkeella"
      },
      "catalogue-item/infourl": {},
      "resource/ext-id": "Extra Data",
      "catalogue-item/start": "2023-10-31T07:17:52.379Z",
      "catalogue-item/archived": false,
      "catalogue-item/id": 15
    }
  ],
  "application/accepted-licenses": {
    "ABC123XYZ456": [
      1,
      9,
      8
    ]
  },
  "application/forms": [
    {
      "form/id": 3,
      "form/title": "Public and private fields form",
      "form/internal-name": "Public and private fields form",
      "form/external-title": {
        "fi": "Lomake",
        "en": "Form"
      },
      "form/fields": [
        {
          "field/private": false,
          "field/info-text": {
            "fi": "Selitys tekstikentän täyttämisestä",
            "en": "Explanation of how to fill in text field",
            "sv": "Förklaring till hur man fyller i textfält"
          },
          "field/title": {
            "fi": "Tekstikenttä",
            "en": "Text field"
          },
          "field/max-length": 100,
          "field/visible": true,
          "field/type": "text",
          "field/value": "two-form draft application",
          "field/id": "fld1",
          "field/optional": false,
          "field/placeholder": {
            "fi": "Täyteteksti",
            "en": "Placeholder text",
            "sv": "Textexempel"
          }
        },
        {
          "field/private": false,
          "field/info-text": {
            "fi": "Selitys tekstikentän täyttämisestä",
            "en": "Explanation of how to fill in text field",
            "sv": "Förklaring till hur man fyller i textfält"
          },
          "field/title": {
            "fi": "Yksityinen tekstikenttä",
            "en": "Private text field"
          },
          "field/max-length": 100,
          "field/privacy": "private",
          "field/visible": true,
          "field/type": "text",
          "field/value": "two-form draft application",
          "field/id": "fld2",
          "field/optional": false,
          "field/placeholder": {
            "fi": "Täyteteksti",
            "en": "Placeholder text",
            "sv": "Textexempel"
          }
        }
      ]
    },
    {
      "form/id": 4,
      "form/title": "Simple form",
      "form/internal-name": "Simple form",
      "form/external-title": {
        "fi": "Lomake",
        "en": "Form"
      },
      "form/fields": [
        {
          "field/private": false,
          "field/info-text": {
            "fi": "Selitys tekstikentän täyttämisestä",
            "en": "Explanation of how to fill in text field",
            "sv": "Förklaring till hur man fyller i textfält"
          },
          "field/title": {
            "fi": "Tekstikenttä",
            "en": "Text field"
          },
          "field/max-length": 100,
          "field/privacy": "private",
          "field/visible": true,
          "field/type": "text",
          "field/value": "two-form draft application",
          "field/id": "fld1",
          "field/optional": false,
          "field/placeholder": {
            "fi": "Täyteteksti",
            "en": "Placeholder text",
            "sv": "Textexempel"
          }
        }
      ]
    }
  ],
  "application/invited-members": [],
  "application/description": "",
  "application/generated-external-id": "2023/14",
  "application/last-activity": "2023-10-31T07:17:52.540Z",
  "application/events": [
    {
      "application/external-id": "2023/14",
      "event/actor-attributes": {
        "userid": "ABC123XYZ456",
        "name": "Alice Applicant",
        "email": "alice@example.com",
        "researcher-status-by": "so"
      },
      "application/id": 14,
      "event/time": "2023-10-31T07:17:52.386Z",
      "workflow/type": "workflow/default",
      "application/resources": [
        {
          "catalogue-item/id": 14,
          "resource/ext-id": "urn:nbn:fi:lb-201403262"
        },
        {
          "catalogue-item/id": 15,
          "resource/ext-id": "Extra Data"
        }
      ],
      "application/forms": [
        {
          "form/id": 3
        },
        {
          "form/id": 4
        }
      ],
      "event/visibility": "visibility/public",
      "workflow/id": 1,
      "event/actor": "ABC123XYZ456",
      "event/type": "application.event/created",
      "event/id": 69,
      "application/licenses": [
        {
          "license/id": 8
        },
        {
          "license/id": 9
        },
        {
          "license/id": 1
        }
      ]
    },
    {
      "event/id": 70,
      "event/type": "application.event/draft-saved",
      "event/time": "2023-10-31T07:17:52.425Z",
      "event/actor": "ABC123XYZ456",
      "application/id": 14,
      "event/actor-attributes": {
        "userid": "ABC123XYZ456",
        "name": "Alice Applicant",
        "email": "alice@example.com",
        "researcher-status-by": "so"
      },
      "application/field-values": [
        {
          "form": 3,
          "field": "fld1",
          "value": "two-form draft application"
        },
        {
          "form": 3,
          "field": "fld2",
          "value": "two-form draft application"
        },
        {
          "form": 4,
          "field": "fld1",
          "value": "two-form draft application"
        }
      ],
      "event/visibility": "visibility/public"
    },
    {
      "event/id": 71,
      "event/type": "application.event/licenses-accepted",
      "event/time": "2023-10-31T07:17:52.441Z",
      "event/actor": "ABC123XYZ456",
      "application/id": 14,
      "event/actor-attributes": {
        "userid": "ABC123XYZ456",
        "name": "Alice Applicant",
        "email": "alice@example.com",
        "researcher-status-by": "so"
      },
      "application/accepted-licenses": [
        1,
        9,
        8
      ],
      "event/visibility": "visibility/public"
    },
    {
      "event/id": 72,
      "event/type": "application.event/member-invited",
      "event/time": "2023-10-31T07:17:52.459Z",
      "event/actor": "ABC123XYZ456",
      "application/id": 14,
      "event/actor-attributes": {
        "userid": "ABC123XYZ456",
        "name": "Alice Applicant",
        "email": "alice@example.com",
        "researcher-status-by": "so"
      },
      "application/member": {
        "name": "Malice Applicant",
        "email": "malice@example.com"
      },
      "event/visibility": "visibility/public"
    },
    {
      "event/id": 73,
      "event/type": "application.event/member-joined",
      "event/time": "2023-10-31T07:17:52.483Z",
      "event/actor": "MNO123XYZ456",
      "application/id": 14,
      "event/actor-attributes": {
        "userid": "MNO123XYZ456",
        "name": "Malice Applicant",
        "email": "malice@example.com"
      },
      "event/visibility": "visibility/public"
    },
    {
      "event/id": 74,
      "event/type": "application.event/submitted",
      "event/time": "2023-10-31T07:17:52.505Z",
      "event/actor": "ABC123XYZ456",
      "application/id": 14,
      "event/actor-attributes": {
        "userid": "ABC123XYZ456",
        "name": "Alice Applicant",
        "email": "alice@example.com",
        "researcher-status-by": "so"
      },
      "event/visibility": "visibility/public"
    },
    {
      "event/actor-attributes": {
        "userid": "HIJ123XYZ789",
        "name": "Hannah Handler",
        "email": "handler@example.com"
      },
      "application/id": 14,
      "event/time": "2023-10-31T07:17:52.540Z",
      "application/comment": "please have a look",
      "application/reviewers": [
        {
          "userid": "CDE123XYZ456",
          "name": "Carl Reviewer",
          "email": "carl@example.com"
        }
      ],
      "event/visibility": "visibility/handling-users",
      "application/request-id": "e3305727-5123-4c2b-bf8a-4d6ab23cff95",
      "event/actor": "HIJ123XYZ789",
      "event/type": "application.event/review-requested",
      "event/id": 75
    }
  ],
  "application/attachments": [],
  "application/licenses": [
    {
      "license/type": "link",
      "license/link": {
        "en": "https://www.apache.org/licenses/LICENSE-2.0",
        "fi": "https://www.apache.org/licenses/LICENSE-2.0"
      },
      "license/title": {
        "en": "Demo license",
        "fi": "Demolisenssi"
      },
      "license/id": 1,
      "license/enabled": true,
      "license/archived": false
    },
    {
      "license/type": "link",
      "license/link": {
        "en": "https://creativecommons.org/licenses/by/4.0/legalcode",
        "fi": "https://creativecommons.org/licenses/by/4.0/legalcode.fi"
      },
      "license/title": {
        "en": "CC Attribution 4.0",
        "fi": "CC Nimeä 4.0"
      },
      "license/id": 8,
      "license/enabled": true,
      "license/archived": false
    },
    {
      "license/text": {
        "en": "License text in English. License text in English. License text in English. License text in English. License text in English. License text in English. License text in English. License text in English. License text in English. License text in English. ",
        "fi": "Suomenkielinen lisenssiteksti. Suomenkielinen lisenssiteksti. Suomenkielinen lisenssiteksti. Suomenkielinen lisenssiteksti. Suomenkielinen lisenssiteksti. Suomenkielinen lisenssiteksti. Suomenkielinen lisenssiteksti. Suomenkielinen lisenssiteksti. Suomenkielinen lisenssiteksti. Suomenkielinen lisenssiteksti. "
      },
      "license/type": "text",
      "license/title": {
        "en": "General Terms of Use",
        "fi": "Yleiset käyttöehdot"
      },
      "license/id": 9,
      "license/enabled": true,
      "license/archived": false
    }
  ],
  "application/created": "2023-10-31T07:17:52.386Z",
  "application/role-permissions": {
    "past-reviewer": [
      "see-everything",
      "application.command/redact-attachments",
      "application.command/remark"
    ],
    "decider": [
      "see-everything",
      "application.command/redact-attachments",
      "application.command/decide",
      "application.command/remark"
    ],
    "everyone-else": [
      "application.command/accept-invitation"
    ],
    "expirer": [
      "application.command/send-expiration-notifications",
      "application.command/delete"
    ],
    "member": [
      "application.command/copy-as-new",
      "application.command/accept-licenses"
    ],
    "reporter": [
      "see-everything"
    ],
    "past-decider": [
      "see-everything",
      "application.command/redact-attachments",
      "application.command/remark"
    ],
    "applicant": [
      "application.command/copy-as-new",
      "application.command/remove-member",
      "application.command/accept-licenses",
      "application.command/uninvite-member"
    ],
    "reviewer": [
      "see-everything",
      "application.command/redact-attachments",
      "application.command/review",
      "application.command/remark"
    ],
    "handler": [
      "application.command/invite-member",
      "application.command/invite-decider",
      "application.command/request-review",
      "see-everything",
      "application.command/redact-attachments",
      "application.command/invite-reviewer",
      "application.command/change-applicant",
      "application.command/reject",
      "application.command/add-licenses",
      "application.command/remove-member",
      "application.command/request-decision",
      "application.command/uninvite-member",
      "application.command/remark",
      "application.command/add-member",
      "application.command/approve",
      "application.command/return",
      "application.command/assign-external-id",
      "application.command/close",
      "application.command/change-resources"
    ]
  },
  "application/state": "application.state/submitted",
  "application/modified": "2023-10-31T07:17:52.425Z"
}
```

## Examples

See [Bona fide pusher for an example use case](../resources/addons/bona-fide-pusher/README.md)
