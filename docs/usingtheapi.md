# Using the API

## Documentation

The REMS API is documented using
[OpenAPI aka Swagger](https://swagger.io/docs/specification/about/).
You can check out the API docs using
[the swagger-ui of the public demo instance](https://rems-demo.rahtiapp.fi/swagger-ui),
or your local development instance at <http://localhost:3000/swagger-ui>.

## Authentication

You can use either browser session authentication or an API key.

### Session authentication

Once a user has logged in to REMS normally, they can use the API from
their browser. In practice this means that you can log in and then use
the "Try it out!" feature in Swagger UI.

Session authentication is also used by implementation of  REMS browser UI

### API key authentication

You can also authenticate by setting these two headers:
- `x-rems-api-key` -- an API key
- `x-rems-user-id` -- the user id of a user to impersonate

API keys can be defined and modified using the `api-key` command line option to REMS. Here are some examples:

```sh
# Get all API keys:
java -Drems.config=path/to/config -jar rems.jar api-key get
# Add an API key with an optional comment:
java -Drems.config=path/to/config -jar rems.jar api-key add abcd1234 this is my secret api key
```

API keys can optionally have _user_ and _path whitelists_. These limit
the users the API key can impersonate, and the paths the API key can
access. Here are some examples:

```sh
# Set whitelists:
java -Drems.config=path/to/config -jar rems.jar api-key set-users abcd1234 alice bob
java -Drems.config=path/to/config -jar rems.jar api-key allow abcd1234 get '/api/users/.*'
java -Drems.config=path/to/config -jar rems.jar api-key allow abcd1234 any '/api/applications/.*'
# Clear whitelists:
java -Drems.config=path/to/config -jar rems.jar api-key set-users abcd1234
java -Drems.config=path/to/config -jar rems.jar api-key allow-all abcd1234
```

The user whitelist contains userids. The path whitelist contains
regexps that must match the whole path. If the user (or path)
whitelist is empty, any user (or respectively path) is allowed for the
API key.

For more information about the api-key command line commands, run:

```sh
java -Drems.config=path/to/config -jar rems.jar help
```

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

You may also inspect what API request the REMS UI does using your web
browser's developer tools. The REMS UI does its requests using the
`application/transit+json` content-type, but all the APIs work also
using `application/json` (which is the default).
