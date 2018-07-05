# Using the API

## Introduction

Here we assume that the instance you want to talk to is running locally at `localhost:3000`, you have an API-key that you must provide in the header `X-REMS-API-Key`.

Checking what catalogue items are available

```sh
curl -H "X-REMS-API-Key: 42" http://localhost:3000/api/catalogue
```

Returns the JSON response with the catalogue items

```json
[
    {
        "formid": 1,
        "id": 1,
        "localizations": {
            "en": {
                "id": 1,
                "langcode": "en",
                "title": "ELFA Corpus, direct approval"
            },
            "fi": {
                "id": 1,
                "langcode": "fi",
                "title": "ELFA-korpus, suora hyv\u00e4ksynt\u00e4"
            }
        },
        "resid": "urn:nbn:fi:lb-201403262",
        "state": "enabled",
        "title": "non-localized title",
        "wfid": 1
    },

    ...
]
```

Some API endpoints also require `X-REMS-User-Id` header that is the REMS user id for the user that is represented. I.e. which user applies for a resource and which approves an application.

See other methods in the [Swagger API documentation](https://rems2demo.csc.fi/swagger-ui).

## Example: Applying & Approving

The following example contains instructions on how to send an application and to approve the requested access using the API. These instructions assume you are using curl, have the backend running locally and have populated your local database with test data. Please refer to the README.md on [github](https://github.com/CSCfi/rems) for the setup instructions.

We will be applying for access on behalf of Alice. First let's find out which resources are available. To do this we will send the following request:

```sh
curl -X GET \
     -H 'Accept: application/json' \
     -H 'x-rems-api-key: 42' \
     -H 'x-rems-user-id: alice' \
     'http://localhost:3000/api/catalogue'
```

Let's say we are interested in applying for catalogue item 2 that includes a workflow with one approval round. We can query the application api endpoint to find out how to fill in the form. Essentially, it gives us a draft to work on.

```
curl -X GET -H 'Accept: application/json' -H 'x-rems-api-key: 42' -H 'x-rems-user-id: alice' 'http://localhost:3000/api/applications/draft?catalogue-items=2'
```

Judging from the output of the previous command, in order to apply for access we need to:

* accept licenses 1 and 2
* fill in form items "Project name" and "Purpose of the project" with ids 1 & 2

Form item "Duration of the project" seems to be optional so we will leave that empty. Let's send in the application.

```sh
curl -X PUT \
     -H 'Content-Type: application/json' \
     -H 'Accept: application/json' \
     -H 'x-rems-api-key: 42' \
     -H 'x-rems-user-id: alice' \
     -d '{"command": "submit", \
    "catalogue-items": [2], \
    "items": {"1":"Test Project","2":"To test sending applications using the api"}, \
    "licenses": {"1":"approved","2":"approved"} }' \
    'http://localhost:3000/api/applications/save'
```

You should get the following output as a response:

```
{"success":true,"valid":true,"id":12,"state":"applied"}
```

This tells us that the request succeeded, the system assigned id 12 to our application, the form was properly filled and that the application has progressed to an applied state. Now we can proceed to approving the request. Both users Developer and Bob have been assigned as approvers for the current workflow but only one of them needs to grant the permission. Let's provide an answer as Developer:

```sh
curl -X PUT \
     -H 'Content-Type: application/json' \
     -H 'Accept: application/json' \
     -H 'x-rems-api-key: 42' \
     -H 'x-rems-user-id: developer' \
   -d '{"command": "approve", \
   "application-id": 12, \
   "round": 0, \
   "comment": "Looks good to me." \
 }' 'http://localhost:3000/api/applications/judge'
```

Now the application sent by Alice has been approved and an entitlement should have been created. To verify this we can query the entitlements api like this:

```sh
curl -X GET -H 'Accept: application/json' -H 'x-rems-api-key: 42' -H 'x-rems-user-id: developer' 'http://localhost:3000/api/entitlements?user=alice'
```
