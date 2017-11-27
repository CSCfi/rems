# Entitlement callbacks

REMS can submit a HTTP POST request for every entitlement granted.

To use this feature, add `:entitlements-target "http://url/to/post/to"` to
`config.edn`.

The payload of the POST request is JSON, and looks like this:

```json
{"application": 137,
 "user": "username",
 "resource:" "http//urn.fi/resource_name"}
```