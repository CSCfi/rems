# Keycloak Identity Provider for REMS

Using docker, docker-compose, and Keycloak, you can roll your own local IdP for use with REMS.

## Quick-ish Start

1. **Setting up the docker-compose file**
Create a `docker-compose.yaml` file for REMS and your Keycloak IdP. Mount your custom REMS config to `/rems/config/config.edn`. The following example is provided as a `docker-compose.yaml` file in this directory:
```
version: "3.7"
services:
    rems:
        image: cscfi/rems
        mem_reservation: 200m
        mem_limit: 500m
        ports:
            - "127.0.0.1:3000:3000"
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
        image: quay.io/keycloak/keycloak:latest
        environment:
            KEYCLOAK_USER: admin
            KEYCLOAK_PASSWORD: admin
        ports:
            - "8080:8080"
volumes:
    db:
```

Next, we will set up Keycloak with the following settings:
| Keycloak property                | Setting              | Value                                |
|----------------------------------|----------------------|--------------------------------------|
| Keycloak admin's username        | Username or email    | admin                                |
| Keycloak admin's password        | Password             | admin                                |
| realm                            | Name                 | rems-idp                             |
| client                           | Client ID            | rems-client                          |
| client auth protocol             | Client Protocol      | openid-connect                       |
| acceptable client redirect URIs  | Valid Redirect URIs  | *                                    |
| client access type               | Access Type          | confidential                         |
| client authenticator             | Client Authenticator | Client Id and Secret                 |
| Keycloak-generated client secret | Secret               | 0ed4b9e0-5ff0-446a-ab7b-82dac465b9d5 |
| REMS user's username             | Username             | username                              |
| REMS user's password             | Password             | password                              |

2. **Running and preparing Keycloak**
    1. **Run the keycloak container** with `docker-compose up keycloak`.
    2. **Login to keycloak as `admin`/`admin`.**
    Login at `http://keycloak:8080/auth/admin`. The administration username and password can be set as environment variables, as shown in the `docker-compose.yaml` example above.
    3. *(Optional)* **Import the generic REMS Keycloak realm** provided in this repository.
    Download the `realm-export.json` file provided in this directory.
    Mouse over `Master` near the top-left of the page, and click `Add realm`. Next to `Import`, click `Select file` and choose the `realm-export.json` file. Click `Create` to finish.
    Skip to step #4 below.

If you opt to not do #2.3, follow the instructions outlined in step #3 (below) to set up the realm manually.

3. *(Optional)* **Manually preparing a Keycloak Realm**
    1. **Add an identity provision `realm` to Keycloak named `rems-idp`.**
    Add the realm by mousing over `Master` near the top-left of the page, and clicking `Add realm`.
    A realm can be thought of as a single vault containing a set of users, credentials, groups, clients, security settings, etc. When authenticating with a realm, the client is only able to authenticate within that realm.
    2. **Add an OIDC client named `rems-client` to the realm.**
    Navigate to `Clients` on the left side of the page and click `Create`. Set the `Client Protocol` to `openid-connect`. Clients are allowed to authenticate users within the Keycloak realm that they are bound to. For this example, the Client ID will be `rems-client`.
    3. **Define the set of acceptable redirect URIs.**
    Upon client creation, you will be redirected to `Clients` > `rems-client`. To accept all redirect URIs, put `*` in the `Valid Redirect URIs` field.
    4. **Authenticate the client with Keycloak using the `Client Id and Secret`.**
    There various ways to authenticate the client itself with Keycloak. REMS uses the `Client ID` and a Keycloak-generated `Secret`.
    In Keycloak, navigate to `Clients` > `rems-client`. Set the `Access Type` to `confidential`.
    
4. **Configuring REMS to Authenticate with Keycloak**
    1. **Generate the REMS client secret.**
    In Keycloak, navigate to `Clients` > `rems-client` > `Credentials`. Click `Regenerate Secret` and copy the generated secret to your clipboard. In this example, the generated secret is `0ed4b9e0-5ff0-446a-ab7b-82dac465b9d5`, but yours will be something different.
    2. **Configure REMS to use the secret.**
    Modify your custom REMS config file (called `./services/rems/simple-config.edn` in this example). Tell REMS to use OIDC by modifying the `:authentication` parameter, and setting the values for some OIDC parameters specific to your keycloak instance. Remember that docker-compose will mount this file into your REMS container as `/rems/config/config.edn`.
    ```
    :authentication :oidc

    ; The location of Keycloak's OIDC metadata for the realm to which REMS is client
    :oidc-metadata-url "http://keycloak:8080/auth/realms/rems-idp/.well-known/openid-configuration"

    ; REMS' client ID, as defined in Keycloak
    :oidc-client-id "rems-client"

    ; REMS' client secret, as generated in keycloak in step #4.1
    :oidc-client-secret "0ed4b9e0-5ff0-446a-ab7b-82dac465b9d5"
    ```

5. **Spinning up REMS**
    1. **Finalize the REMS configuration.**
    The full configuration used in this example is provided in the `simple-config.edn` file in this directory.
    Add any other configuration that your REMS instance needs to your `simple-config.edn` file. For example, you will want to populate the `database-url` and `port` parameters.
    ```
    :port 3000
    :database-url "postgresql://db:5432/rems?user=rems&password=remspassword"
    :search-index-path "/tmp/rems-search-index"
    :authentication :oidc
    :public-url "http://localhost:3000/"
    ```
    2. **Migrate the REMS database.**
        1. Migrate REMS with: `docker-compose run --rm -e CMD="migrate" rems`
        2. *(Optional)* Populate REMS with test data with: `docker-compose run --rm -e CMD="test-data" rems`
    3. Run with: `docker-compose up rems`
    4. Navigate to REMS, at http://localhost:3000/ in this example.

5. **Testing Authentication**
    1. **Create REMS user username/password in Keycloak.**
        1. To test authentication with Keycloak, add a test User in Keycloak by navigating to `Users` and clicking `Add user`. Give them a `username` and click `Save`.
        2. Navigate to the new User's `Credentials` tab and set a `password` for them. Set the `Temporary` toggle to `OFF`.
    2. In REMS, at http://localhost:3000/ in this example, click the `Login` button to be redirected to Keycloak. 
    3. Access the account by logging in with the user's `username`/`password` credentials. You should be authenticated and redirected back to REMS.

6. **Exporting the Realm**
    1. You can export your realm and save it source control, to reduce development and testing time. Simply navigate to `Export` on the bottom-left of the page, set `Export clients` to `ON`, and click `Export`.
    2. Next time you want to add this realm to keycloak, navigate to `Add Realm` and select your `realm-export.json` file for import.

## OIDC Hostname issues

If you are using docker-compose to spin up your IdP alongside REMS, you may run into hostname issues pertaining to `:oidc-metadata-url` when using a browser. Keycloak generates the redirect URLs served at the `./well-known/openid-configuration` endpoint based on the hostname of the incoming request. But if this hostname is relative to a set of docker containers, it might not make sense to the browser from which you are accessing REMS and the Keycloak IdP.

For example, suppose your `docker-compose.yaml` has a Keycloak IdP configured as follows:
```
services:
  keycloak:
    ports:
      - "8080:8080"
```
Then, in your REMS config file, you must set something like:
```
:oidc-metadata-url "http://keycloak:8080/auth/realms/my-realm/.well-known/openid-configuration"`.
```
If you instead set it to something like:
```
:oidc-metadata-url "http://localhost:8080/auth/realms/dycons-researcher-idp/.well-known/openid-configuration"
```
then REMS will crash with a `Connection Refused` exception early in runtime. 

Using the `keycloak:8080` hostname, the problem arises when a user attempts to Login to REMS. REMS requests authentication redirection URLs from Keycloak at the `keycloak:8080` address. Keycloak then generates those URLs using the `keycloak:8080` hostname used in the request. However, `keycloak:8080` is meaningless to the user's browser, which does not have access to the docker-compose network namespace. The browser is unable to find Keycloak at that address.

This can be resolved with either of the processes outlined below, which cause keycloak to generate the URLs served at `.well-known/openid-configuration` with a specified [frontendUrl](https://www.keycloak.org/docs/latest/server_installation/#default-provider). The `frontendUrl` resolves to a hostname that the browser is able to find (`localhost:8080` in this example). REMS will still be able to connect to keycloak at `keycloak:8080` at runtime, but the `.well-known/openid-configuration` Keycloak endpoint will serve URLs with the `frontendUrl` base URL you set.

### Option 1: Set frontendUrl using the Keycloak GUI, then export

This solution is host-independent, can be saved to source control, and is easy to automate into your overall Keycloak setup process.

1. Run your keycloak and REMS with `docker-compose up`.
2. Create or import a `realm` if you have not done so already.
3. In the realm settings on the admin console, populate the `Frontend URL` field with `http://localhost:8080/auth/`. This is assuming you want Keycloak to authenticate at `localhost:8080`. Amend the hostname as needed, *but make sure the value for Frontend URL terminates with `/auth/`.*
4. You can export your realm with this custom frontendUrl and add the resulting `realm-export.json` to source control. Next time you set up keycloak by importing the realm, the frontendUrl will be populated automatically.

### Option 2: Add the keycloak hostname to the host machine

This solution is a common work-around for keycloak redirection issues, but has the following disadvantages:
- Requires administrative access to the host machine
- Is a manual process that must be run on each new machine that keycloak is being installed on
- Can cause confusion when multiple seperate instances of Keycloak are run on one host
- May not work if the port number inside the docker network differs from the port exposed on the host, for example if `localhost:8081` is mapped to `keycloak:8080`

1. Open the host's `/etc/hosts` file for editing, for example with: `sudo nano /etc/hosts`
2. Add the end of the file, add the IP address of your keycloak instance followed by the domain name and its aliases: `IPAddress DomainName [DomainAliases]`. For example, append:
```
127.0.0.1   keycloak
```
3. Save the modified `/etc/hosts` file.