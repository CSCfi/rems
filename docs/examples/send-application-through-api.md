# Example of REMS application through the API without user interaction

This is an example of how REMS can be used to process applications where the applicant user does not directly use REMS, for example if a custom UI is created for the application process. The UI can still be used by the handling users.

This simple flow is enough to create a user and send an application for processing. The process gets slightly more complicated, if the application can be returned to the applicant for changes, or for example if a copy of an old application is used.

## Create the applicant user

First, create a user who is the applicant. This call is made with the technical user who has the `owner` or `user-owner` role.

When the regular REMS UI is used, the login callback checks whether the user already exists and creates one if not. There can be any number of extra attributes in the payload, and any can be configured to be shown in the UI through `:oidc-extra-attributes` config.

```sh
curl -X 'POST' \
  'http://localhost:3000/api/users/create' \
  -H 'accept: application/json' \
  -H 'x-rems-api-key: 42' \
  -H 'x-rems-user-id: owner' \
  -H 'Content-Type: application/json' \
  -d '{
  "userid": "marie",
  "name": "Marie Curie",
  "email": "marie.curie@example.org",
  "nobel": "true"
}'
```

This should return a simple success response.

```json
{
  "success": true
}
```

## Create application

Create a new application using the newly created applicant user. 

Catalogue item ids can be determined by the catalogue API beforehand. NB, currently there is no way to specify external resource ids (resid) here as there is with direct apply links (`&resource=...`, see [linking.md](../linking.md)).

```sh
curl -X 'POST' \
  'http://localhost:3000/api/applications/create' \
  -H 'accept: application/json' \
  -H 'x-rems-api-key: 42' \
  -H 'x-rems-user-id: marie' \
  -H 'Content-Type: application/json' \
  -d '{
  "catalogue-item-ids": [
    5
  ]
}'
```

This should return a simple success response with the new application id.

```json
{
  "success": true,
  "application-id": 31
}
```

## Accept terms of use

Generally the user must accept the licenses or terms of use, if any.

License ids are internal ids that can be determined from the license API or administration UI.

```sh
curl -X 'POST' \
  'http://localhost:3000/api/applications/accept-licenses' \
  -H 'accept: application/json' \
  -H 'x-rems-api-key: 42' \
  -H 'x-rems-user-id: marie' \
  -H 'Content-Type: application/json' \
  -d '{
  "application-id": 31,
  "accepted-licenses": [
    1, 7, 8
  ]
}'
```

This should return a simple success response.

```json
{
  "success": true
}
```

## Save answers

To send the answers, we use the `save-draft` command. In this case there is only one form with one text field.

The form ids are available from the form API or administration UI. The administrator (owner) can set the technical field ids for each field. Otherwise they are generated, and of the form `fld1`, `fld2` etc.

```sh
curl -X 'POST' \
  'http://localhost:3000/api/applications/save-draft' \
  -H 'accept: application/json' \
  -H 'x-rems-api-key: 42' \
  -H 'x-rems-user-id: marie' \
  -H 'Content-Type: application/json' \
  -d '{
  "application-id": 31,
  "field-values": [
    {
      "form": 5,
      "field": "fld1",
      "value": "Marie Curie received the second Nobel Prize in 1911."
    }
  ]
}'
```

If there are no validation errors, the simple success response is returned.

```json
{
  "success": true
}
```

## Submit application

Finally, the application must be sent or submitted for processing.

```sh
curl -X 'POST' \
  'http://localhost:3000/api/applications/submit' \
  -H 'accept: application/json' \
  -H 'x-rems-api-key: 42' \
  -H 'x-rems-user-id: marie' \
  -H 'Content-Type: application/json' \
  -d '{
  "application-id": 31
}'
```

This should return a final simple success response.

```json
{
  "success": true
}
```

Now the application is available for processing by the handling users.
