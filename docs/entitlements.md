# Entitlement callbacks

REMS can submit a HTTP POST request for every entitlement granted or
revoked.

To use this feature, add `:entitlements-target {:add
"http://url/to/post/to" :remove "http://other/url"}` to `config.edn`.

The payload of the POST request is JSON, and looks like this:

```json
{"application": 137,
 "user": "username",
 "resource": "http//urn.fi/resource_name"
 "email": "bob@example.com"}
```