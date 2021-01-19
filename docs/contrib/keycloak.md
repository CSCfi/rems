# Keycloak Identity Provider for REMS

Using docker, docker-compose, and Keycloak, you can roll your own local IdP for use with REMS.

## Quick-ish Start

1. **Setting up the Docker files**
    1. Create a `Dockerfile` for your keycloak. To start, it can be as simple as the following:
    ```
    FROM quay.io/keycloak/keycloak:11.0.2
    ```
    2. Create a `docker-compose.yaml` file for REMS and your Keycloak IdP. Mount your custom REMS config to `/rems/config/config.edn`. Example:
    ```
    version: "3.7"
    services:
    rems:
        mem_reservation: 200m
        mem_limit: 500m
        ports:
        - "127.0.0.1:3001:3000"
        volumes:
        - ./services/rems/simple-config.edn:/rems/config/config.edn
        depends_on:
        - db
        - keycloak
    db:
        image: postgres:9.6
        environment:
        POSTGRES_USER: rems
        POSTGRES_PASSWORD: remspassword
        mem_reservation: 30m
        mem_limit: 150m
        ports:
        - "127.0.0.1:5432:5432"
    keycloak:
        environment:
        KEYCLOAK_USER: admin
        KEYCLOAK_PASSWORD: admin
        ports:
        - "3002:8080"
    ```

2. **Preparing a Keycloak Realm**
    1. Build and run keycloak with `docker-compose up keycloak`.
    2. Login to keycloak at `http://keycloak:8080/auth/admin` with the admin username and password, set to `admin` and `admin` in this (very insecure) example. These can be set as environment variables, as demonstrated in the `docker-compose.yaml` example above.
    3. Add an identity provision `realm` to Keycloak by mousing over `Master` near the top-left of the page, and clicking `Add realm`. A realm can be thought of as a single vault containing a set of users, credentials, groups, clients, security settings, etc. When authenticating with a realm, the client is only able to authenticate within that realm. Realms can be partially exported and imported by clicking `Import`/`Export` near the bottom-left of the page, although exporting users is more complicated. For this example, the realm will be named `rems-idp`.
    4. Add a client for REMS, by navigating to `Clients` on the left side of the page and clicking `Create`. Set the `Client Protocol` to `openid-connect`. Clients are allowed to authenticate users within the Keycloak realm that they are bound to. For this example, the Client ID will be `rems-client`.
    5. Upon client creation, you will be redirected to `Clients` > `rems-client`. Put `*` in the `Valid Redirect URIs` field.
    6. There is a variety of ways to authenticate the client itself with Keycloak. REMS uses the `Client ID` and a Keycloak-generated `Secret`. In Keycloak, navigate to `Clients` > `rems-client` if you are not already there. Set the `Access Type` to `confidential`. Then, navigate to the `Credentials` tab. Set `Client Authenticator` to `Client Id and Secret`. Click `Regenerate Secret` and copy the generated secret to your clipboard. For this example, the generated secret is `0ed4b9e0-5ff0-446a-ab7b-82dac465b9d5`.

3. **Configuring REMS to Authenticate with Keycloak**
    1. Modify your custom REMS config file (called `./services/rems/simple-config.edn` in this example). Tell REMS to use OIDC by modifying the `:authentication` parameter, and setting the values for some OIDC parameters specific to your keycloak instance. Remember that docker-compose will mount this file into your REMS container as `/rems/config/config.edn`.
    ```
    :authentication :oidc

    ; The location of Keycloak's OIDC metadata for the realm to which REMS is client
    :oidc-metadata-url "http://keycloak:8080/auth/realms/rems-idp/.well-known/openid-configuration"

    ; REMS' client ID, as defined in Keycloak
    :oidc-client-id "rems-client"

    ; REMS' client secret, as generated in keycloak in step #8 above
    :oidc-client-secret "0ed4b9e0-5ff0-446a-ab7b-82dac465b9d5"
    ```

4. **Spinning up REMS**
    1. Add any other configuration that your REMS instance needs to your `simple-config.edn` file. For example, you will want to populate the `database-url` and `port` parameters.
    ```
    {:port 3000
    :database-url "postgresql://db:5432/rems?user=rems&password=remspassword"
    :search-index-path "/tmp/rems-search-index"
    :authentication :oidc
    :public-url "http://localhost:3001/"

    :oidc-metadata-url "http://rp-keycloak:8080/auth/realms/rems-idp/.well-known/openid-configuration"

    :oidc-client-id "rems-client"
    :oidc-client-secret "0ed4b9e0-5ff0-446a-ab7b-82dac465b9d5"
    }
    ```
    2. Build rems, which (at the time of writing) requires:
        1. [Installing Leiningen](https://leiningen.org/)
        2. Building the Leiningen jar: `lein uberjar`
        3. Building the Docker container: `docker-compose build rems`
        4. Migrating the test data: `docker-compose run --rm -e CMD="migrate;test-data" rems`
    3. Run `docker-compose up rems`
    4. Navigate to REMS, at http://localhost:3001/ in this example.

5. **Testing Authentication**
    1. To test authentication with Keycloak, add a test User in Keycloak by navigating to `Users` and clicking `Add user`. Give them a `username` and clicking `Save`.
    2. Navigate to the new User's `Credentials` tab and set a `password` for them. Set the `Temporary` toggle to `OFF`.
    3. In REMS, at http://localhost:3001/ in this example, click the `Login` button to be redirected to Keycloak. 
    4. Access the account using `varchar`/`varchar`. You should be authenticated and redirected back to REMS.

6. **Exporting the Realm**
    1. You can export your realm and save it source control, to reduce development and testing time. Simply navigate to `Export` on the bottom-left of the page, set `Export clients` to `ON`, and click `Export`.
    2. Next time you want to add this realm to keycloak, navigate to `Add Realm` and select your `realm-export.json` file for import. The `Name` will auto-populate.

## OIDC Hostname issues

If you are using docker-compose to spin up your IdP alongside REMS, you may run into hostname issues pertaining to `:oidc-metadata-url` when using a browser. Keycloak generates the redirect URLs served at the `./well-known/openid-configuration` endpoint based on the hostname of the incoming request. But if this hostname is relative to a set of docker containers, it might not make sense to the browser from which you are accessing REMS and the Keycloak IdP.

For example, suppose your `docker-compose.yaml` has a Keycloak IdP configured as follows:
```
services:
  keycloak:
    ports:
      - "3002:8080"
```
Then, in your REMS config file, you must set something like:
```
:oidc-metadata-url "http://keycloak:8080/auth/realms/my-realm/.well-known/openid-configuration"`.
```
If you instead set it to something like:
```
:oidc-metadata-url "http://localhost:3002/auth/realms/dycons-researcher-idp/.well-known/openid-configuration"
```
then REMS will crash with a `Connection Refused` exception early in runtime. This is fairly typical Docker networking.

Using the `keycloak:8080` hostname, the problem arises when a user attempts to Login to REMS. REMS requests authentication redirection URLs from Keycloak at the `keycloak:8080` address. Keycloak then generates those URLs using the `keycloak:8080` hostname used in the request. However, `keycloak:8080` is meaningless to the user's browser, which does not have access to the docker-compose network namespace. The browser is unable to find Keycloak at that address.

This can be resolved with either of the processes outlined below, which cause keycloak to generate the URLs served at `.well-known/openid-configuration` with a specified [frontendUrl](https://www.keycloak.org/docs/latest/server_installation/#default-provider). The `frontendUrl` resolves to a hostname that the browser is able to find (`localhost:3002` in this example). REMS will still be able to connect to keycloak at `keycloak:8080` at runtime, but the `.well-known/openid-configuration` Keycloak endpoint will serve URLs with the `frontendUrl` base URL you set.

### Option 1: Set frontendUrl using the Keycloak GUI

1. Run your keycloak and REMS with `docker-compose up`.
2. Create or import a `realm` if you have not done so already.
3. In the realm settings on the admin console, populate the `Frontend URL` field with `http://localhost:3002/auth/`. This is assuming you want Keycloak to authenticate at `localhost:3002`. Amend the hostname as needed, *but make sure the value for Frontend URL terminates with `/auth/`.*

### Option 2: Set frontendUrl by mounting custom configuration to the Keycloak container

This process hardcodes the frontendUrl for your keycloak instance, but has the benefit of being easy to automate into your docker build.

1. Copy `/opt/jboss/keycloak/standalone/configuration/standalone-ha.xml` from your keycloak container to your host
2. In your local copy of `standalone-ha.xml`, replace this line:
```
<property name="frontendUrl" value="${keycloak.frontendUrl:}"/>
```
with this line:
```
<property name="frontendUrl" value="http://localhost:3002/auth/"/>
```
This is assuming you want Keycloak to authenticate at `localhost:3002`. Amend the hostname as needed, *but make sure the value for `frontendUrl` terminates with `/auth/`.*
3. Mount your local copy of `standalone-ha.xml` to the keycloak container, ex. via your keycloak's `Dockerfile`:
```
ADD ./standalone-ha.xml /opt/jboss/keycloak/standalone/configuration/standalone-ha.xml
```
4. Run your keycloak and REMS with `docker-compose up`.
