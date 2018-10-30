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

## Example 1: Applying & Approving

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

- accept licenses 1 and 2
- fill in form items "Project name" and "Purpose of the project" with ids 1 & 2

Form item "Duration of the project" seems to be optional so we will leave that empty. Let's send in the application.

```sh
curl -X POST \
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
curl -X POST \
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

## Example 2: Creating a New Catalogue Item

The following example will walk you through the steps needed to create a catalogue item without any prior data. To call the API endpoints you will need a user with an owner role. In this example we will be using a user with `owner` as the userid.

A catalogue item consists of a workflow, a resource and an application form. In order to call the catalogue items API endpoint we will first have to create the data it depends on. Let's first create a workflow:

```sh
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' --header 'x-rems-api-key: 42' --header 'x-rems-user-id: owner' -d '{ \
   "organization": "my_organization", \
   "title": "Test Workflow", \
   "rounds": [ \
     { \
       "type": "approval", \
       "actors": [ \
         { \
           "userid": "bob" \
         } \
       ] \
     } \
   ] \
 }' 'http://localhost:3000/api/workflows/create'
```

Workflow API takes json data as body parameters where `organization` refers to the organization the workflow is related to, `title` provides the workflow a human readable name so that it can be found more easily through the UI and `rounds` takes in an array of application handling rounds. Here we only specify one round of type `approval` where the approving will be handled by a user with userid `bob`. If the call was successful the following json will be returned by the server:

```json
{ "id": 1 }
```

Store this id somewhere as we will needed later when we create the catalogue item.

Next let's create a new resource. The resource API works in a similar way as the workflow API. Keep in mind though that REMS doesn't handle the actual distribution of the resource so the `resid` used here should point to the unique identifier of the resource that you wish to share. The API will return the internal resource id used by REMS as an output just like it did with the successful creation of a workflow. We will not attach any terms of use for our resource so we will not add any license ids to the `licenses` array.

```sh
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' --header 'x-rems-api-key: 42' --header 'x-rems-user-id: owner' -d '{ \
   "resid": "my_resource_id", \
   "organization": "my_organization", \
   "licenses": [ \
   ] \
 }' 'http://localhost:3000/api/resources/create'
```

Then we only need to create an application form to finally be able to create a catalogue item. Let's create a form with only one mandatory text field with English localization. The rest should already be familiar to you from the two previous calls.

```sh
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' --header 'x-rems-api-key: 42' --header 'x-rems-user-id: owner' -d '{ \
   "organization": "my_organization", \
   "title": "API Test Form", \
   "items": [ \
     { \
       "title": {"en":"Project Name"}, \
       "optional": false, \
       "type": "text", \
       "input-prompt": {"en":"My Awesome Project"} \
     } \
   ] \
 }' 'http://localhost:3000/api/forms/create'
```

Finally we can create a catalogue item. Now we simply provide the API endpoint with the ids we got as output from the previous steps:

```sh
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' --header 'x-rems-api-key: 42' --header 'x-rems-user-id: owner' -d '{ \
   "title": "My Awesome Catalogue Item", \
   "form": 1, \
   "resid": 1, \
   "wfid": 1 \
 }' 'http://localhost:3000/api/catalogue-items/create'
```

If everything went smoothly, the newly created catalogue item should now be visible at `http://localhost:3000/#/catalogue`. For more information regarding the catalogue item creation process, please refer to the swagger documentation mentioned earlier.
