# Elixir Bona fide pusher

A simple HTTP server listening REMS' `application.event/approved` event notifications.
On notification, it tries to push user id to Elixir for a bona fide status.

## Pre-requirements for REMS
You need the "bona fide status" catalogue item. For this you first need to create the following objects. These can be 
created in REMS UI except where stated otherwise.
* (Set up `owner` role for yourself)
* (Create an API key)
* Create an organisation
* Create the `bona-fide-bot` user (create using API/swagger)
* Create a resource
* Create a form (only a single email field required)
* Create a workflow (with `bona-fide-bot` as the handler)
* Create a catalogue item

For more info see [Bona Fide bot documentation](../../../docs/bots.md#bona-fide-bot)

## Installation

See [config.ini](config.ini) for an example of a configuration file that must be supplied. 

Add this in your REMS `config.edn`:
```
:event-notification-targets [{:url "http://127.0.0.1:3008"
                              :event-types [:application.event/approved]}]
```

## Running locally
You can test your installation locally. Pick some `<BUILD_NAME>` and `<CONTAINER_NAME>` and run:
```
cd rems/resources/addons/bona-fide-pusher
docker build -t <BUILD_NAME> .
docker run --rm --network="host" --name <CONTAINER_NAME> <BUILD_NAME>
```
Invoke `application.event/approved` event in REMS and check that Bona fide pusher log looks ok. The actual request to 
Elixir might fail from your local environment depending your configuration. 
