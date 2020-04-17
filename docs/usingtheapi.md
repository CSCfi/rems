# Using the API

These examples assume that the REMS instance you want to talk to is running locally at `localhost:3000`.

## Authentication

To call the API programmatically, you will first need to add an API key to the `api_key` database table. The API key must be provided in the `x-rems-api-key` header when calling the API.

Some API endpoints also require `x-rems-user-id` header to contain the REMS user ID for the user that is being represented, i.e. the user which applies for a resource or approves an application.

## Example

Checking what catalogue items are available:

```sh
curl -H "x-rems-api-key: 42" -H "x-rems-user-id: alice" http://localhost:3000/api/catalogue
```

Returns the list of catalogue items as a JSON response:

```json
[
    {
        "id": 1,
        "resource-id": 1,
        "resid": "urn:nbn:fi:lb-201403262",
        "formid": 1,
        "wfid": 1,
        "title": "",
        "localizations": {
            "en": {
                "id": 1,
                "langcode": "en",
                "title": "ELFA Corpus"
            },
            "fi": {
                "id": 1,
                "langcode": "fi",
                "title": "ELFA-korpus"
            }
        },
        "start": "2019-07-31T09:43:51.117Z",
        "end": null,
        "enabled": true,
        "expired": false,
        "archived": false
    },

    ...
]
```

## Learn More

See the [REMS API documentation](https://rems-demo.rahtiapp.fi/swagger-ui) for a list of all available operations.

You may also inspect what API request the REMS UI does using your web browser's developer tools. The REMS UI does its requests using the `application/transit+json` content-type, but all the APIs work also using `application/json` (which is the default).
