# Configuration

REMS contains a number of configuration options that can be used to alter authentication options, theming or to add integration points, just to name a few.

Configuration uses the [cprop](https://github.com/tolitius/cprop) library. You can specify the location of the configuration file by setting the `rems.config` system property: `java -Drems.config="../somepath/config.edn" -jar rems.jar` You can also configure the application using environment variables as described in the cprop documentation.

The full list of available configuration options can be seen in [config-defaults.edn](https://github.com/CSCfi/rems/blob/master/resources/config-defaults.edn).

REMS tries to validate your config file when starting. Errors or warnings will be logged for problems found in the configuration file.

## Authentication options

Currently the only real authentication method is OpenId Connect (e.g. Auth0). The authentication method to be used can be defined with the key `:authentication` and the following values are recognized:

* `:oidc` for OpenId Connect
* `:fake` for development login

### OpenId Connect (`:oidc`)

The `:oidc` authentication method has the following configuration options:

* `:oidc-metadata-url` - URL of the OAuth Server Metadata JSON document. See [RFC 8414](https://tools.ietf.org/html/rfc8414). Typically of the form `https://my.oidc.service/.well-known/openid-configuration`.
* `:oidc-domain` – DEPRECATED, prefer `:oidc-metadata-url`. The openid connect configration is fetched from `https://{oidc-domain}/.well-known/openid-configuration`
* `:oidc-client-id`
* `:oidc-client-secret`
* `:oidc-scopes` - which scopes to request, defaults to `"openid profile email"`
* `:oidc-userid-attribute` – which id-token attribute to use as the
  REMS userid. Can be a single attribute, or a sequence of multiple
  attributes, which are searched in order and the first non-empty one
  used. Defaults to `"sub"`.
* `:oidc-additional-authorization-parameters` - additional query parameters to add to the OIDC authorization_endpoint url when logging in
* `:oidc-extra-attributes` - extra attributes to read. Check [config-defaults.edn](https://github.com/CSCfi/rems/blob/master/resources/config-defaults.edn) for the syntax.
* `:public-url` - the redirect uri sent to the openid endpoint is `{public-url}/oidc-callback`

#### User attributes

By default, REMS reads the following attributes from the OIDC id token:

* `sub` - the username
* `name`, `unique_name` or `family_name` – looked up in this order to decide the display name for the user
* `email` - user email

You can configure additional properties to read via the
`:oidc-extra-attributes` configuration key. The additional attributes
are only shown to users handling the application, and super-users like
the owner and reporter. In praticular, application members cannot see
the additional user properties of each other.

#### OIDC Hostname issues

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

This can be resolved with the process outlined below, which forces keycloak to generate the URLs served at `.well-known/openid-configuration` with a predetermined hostname, one that the browser is able to find (`localhost:3002` in this example).
1) Copy `/opt/jboss/keycloak/standalone/configuration/standalone-ha.xml` from your keycloak container to your host
2) In your local copy of `standalone-ha.xml`, replace this line:
```
<property name="frontendUrl" value="${keycloak.frontendUrl:}"/>
```
with this line:
```
<property name="frontendUrl" value="http://localhost:3002/auth/"/>
```
This is assuming you want Keycloak to authenticate at `localhost:3002`. Amend the hostname as needed, but make sure the value for `frontendUrl` terminates with `/auth/`.
3) Mount your local copy of `standalone-ha.xml` to the keycloak container, ex. via your keycloak's `Dockerfile`:
```
ADD ./standalone-ha.xml /opt/jboss/keycloak/standalone/configuration/standalone-ha.xml
```

REMS will still be able to connect to keycloak at `keycloak:8080` at runtime, but the `.well-known/openid-configuration` Keycloak endpoint will serve URLs with the `frontendUrl` base URL you set.

### Development login (`:fake`)

This option should not be used in production. Keep also in mind that anyone with access to the dev/test server using development authentication can login with your fake credentials.

## Entitlements

REMS can submit a HTTP POST request for every entitlement granted or
revoked.

To use this feature, add `:entitlements-target {:add "http://url/to/post/to" :remove "http://other/url"}` to `config.edn`.

The payload of the POST request is JSON, and looks like this:

```json
{"application": 137,
 "user": "username",
 "resource": "resource_name_maybe_urn_or_something",
 "email": "bob@example.com"}
```

## Localization

To add localization in a new language, make a copy of the [English localization file](https://github.com/CSCfi/rems/blob/master/resources/translations/en.edn) and change the texts. Place it in a directory and configure `:translations-directory` to point to that directory. The localization file must be named after the language code. Add the language code to `:languages` to make it available in the application. You can change the default language by configuring `:default-language`.

For example, to add German localization, create a file `my-translations/de.edn` and use the following configuration:

```clojure
{:translations-directory "my-translations"
 :languages [:de :en]
 :default-language :de}
```

### Overriding default localizations

To override certain localization key, create a new folder called <b>extra-translations</b> under theme folder.

Create new localization files to the new directory. You don't need to copy whole localization files, it is enough to add only the localizations
you want to override. For example to override English localizations for keys: <i>administration.catalogue-item, administration.catalogue-items, applicant-info.applicant, applicant-info.applicants</i>
create the following <b>en.edn</b> file to the new translations folder.

```clojure
{:t
 {:administration {:catalogue-item "Research item"
                   :catalogue-items "Research items"}
  :applicant-info {:applicant "Student"
                   :applicants "Students"}}}
```

See [resources/translations/en.edn](../resources/translations/en.edn)
for a list of all translation keys and their format parameters. Format
parameters are pieces of text like `%3` that get replaced with certain
information.

## Themes

Custom themes can be used by creating a file, for example `my-custom-theme.edn`, and specifying its location in the `:theme-path` configuration parameter. The theme file can override some or all of the theme attributes (see `:theme` in [config-defaults.edn](https://github.com/CSCfi/rems/blob/master/resources/config-defaults.edn)). Static resources can be placed in a `public` directory next to the theme configuration file. See [example-theme/theme.edn](https://github.com/CSCfi/rems/blob/master/example-theme/theme.edn) for an example.

To quickly validate that all UI components look right navigate to `/guide`. See it in action at <https://rems-demo.rahtiapp.fi/guide>.

## Extra pages

Extra pages can be added to the navigation bar using `:extra-pages` configuration parameter. Each extra page can be either a link to an external url or it can show the content of a markdown file to the user. See `:extra-pages` in [config-defaults.edn](https://github.com/CSCfi/rems/blob/master/resources/config-defaults.edn) for examples of how to define the extra pages.

## Logging

REMS uses [Logback](https://logback.qos.ch/) for logging. By default everything is printed to standard output. If you wish to customize logging, create your own Logback configuration file and specify its location using the `logback.configurationFile` system property:

    java -Dlogback.configurationFile=logback-prod.xml -jar rems.jar
