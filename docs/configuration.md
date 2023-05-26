# Configuration

REMS contains a number of configuration options that can be used to alter authentication options, theming or to add integration points, just to name a few.

Configuration uses the [cprop](https://github.com/tolitius/cprop) library. You can specify the location of the configuration file by setting the `rems.config` system property: `java -Drems.config="../somepath/config.edn" -jar rems.jar run` You can also configure the application using environment variables as described in the cprop documentation.

The full list of available configuration options can be seen in [config-defaults.edn](https://github.com/CSCfi/rems/blob/master/resources/config-defaults.edn).

REMS tries to validate your config file when starting. Errors or warnings will be logged for problems found in the configuration file.

## Authentication options

Currently the only real authentication method is OpenId Connect (e.g. Auth0). The authentication method to be used can be defined with the key `:authentication` and the following values are recognized:

* `:oidc` for OpenId Connect
* `:fake` for development login

### OpenId Connect (`:oidc`)

The `:oidc` authentication method has the following configuration options:

* `:oidc-metadata-url` - URL of the OAuth Server Metadata JSON document. See [RFC 8414](https://tools.ietf.org/html/rfc8414). Typically of the form `https://my.oidc.service/.well-known/openid-configuration`.
  The metadata can contain:
  - `authorization_endpoint` - where to do direct the user for the login
  - `token_endpoint` - where to fetch the user's login token with the code
  - `revocation_endpoint` - where to POST a request to revoke the login token
  - `issuer` - issuer to check for validating a login token
  - `jwks_uri` - where to find public key for validating a login token
  - `userinfo_endpoint` - where to fetch user details (claims)

* `:oidc-domain` – DEPRECATED, prefer `:oidc-metadata-url`. The openid connect configration is fetched from `https://{oidc-domain}/.well-known/openid-configuration`
* `:oidc-client-id`
* `:oidc-client-secret`
* `:oidc-scopes` - which scopes to request, defaults to `"openid profile email"`
* `:oidc-userid-attributes` – which id-token attributes to use as the
  REMS userid. A sequence of multiple attributes, which are searched in order and the first non-empty one
  used. The format is such that each attribute is a map where the `:attribute` specifies which token attribute
  is used, and (optionally) `:rename` specifies to which attribute it is stored in the REMS user details
  (returned in API, stored in DB).

  E.g. `:oidc-userid-attributes [{:attribute "sub" :rename "elixirId"} {:attribute "sub2"}]`

  Default is to use the attribute called `"sub"`.
* `:oidc-name-attributes` – which id-token attributes can be used as the name of the user, first will be used
  Like in the default:
  `:oidc-name-attributes ["name" "unique_name" "family_name"]`
* `:oidc-email-attributes` – which id-token attributes can be used as the email address of the user, first will be used
  Like in the default:
  `:oidc-email-attributes ["email"]`
* `:oidc-extra-attributes` - extra attributes to read and store for users
* `:oidc-require-name` - whether a non-empty name attribute is required (defaults to true)
* `:oidc-require-email` - whether a non-empty email attribute is required (defaults to false)
* `:public-url` - the redirect uri sent to the openid endpoint is `{public-url}/oidc-callback`
* `:oidc-additional-authorization-parameters` - additional query parameters to add to the OIDC authorization_endpoint url when logging in
* `:oidc-logout-redirect-url` - to which URL a user is redirected to after a successful logout? (defaults to "/")
* `:oidc-perform-revoke-on-logout` - should REMS POST to the `revocation_endpoint` received from OIDC metadata? (defaults to true)

See details and formats from [config-defaults.edn](https://github.com/CSCfi/rems/blob/master/resources/config-defaults.edn).


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

### Development login (`:fake`)

This option should not be used in production. Keep also in mind that anyone with access to the dev/test server using development authentication can login with your fake credentials.

## Entitlements

REMS can push entitlements to external systems with a few different options. There are currently two distinct versions. The original entitlement post (v1) and a newer refined approach called entitlement push (v2).

### Entitlement push (v2)

The entitlement push can be configured using the `:entitlement-push` key like so in your `config.edn`. Any number of external system targets can be defined.

```edn
  :entitlement-push [{:id "ega"
                      :type :ega
                      :connect-server-url "https://ega.server.url:1234/ega-openid-connect-server"
                      :permission-server-url "https://ega.server.url:1234/ega-permissions"
                      :client-id "..."
                      :client-secret "..."}
```

So far the only supported type is `:ega`, i.e. entitlements are meant to be pushed to [European Genome-Phoenome Archive](https://ega-archive.org/). Enable the EGA support also by adding `:enable-ega true`.

### Entitlement post (v1)

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

Note! REMS sets a Cache-Control max-age of 23 hours for static resources (`:theme-static-resources`, `:extra-static-resources`). Consider using a different filename when updating static resource to avoid caching issues.

## Extra pages

Extra pages can be added to the navigation bar using `:extra-pages` configuration parameter. Each extra page can be either a link to an external url or it can show the content of a markdown file to the user. See `:extra-pages` in [config-defaults.edn](https://github.com/CSCfi/rems/blob/master/resources/config-defaults.edn) for examples of how to define the extra pages.

## PDF customization

Should you require to change the PDF output, there are styling options under the `:pdf-metadata` configuration key.

By default a system font will be chosen (randomly), which many not be what you like, and which may not support all the characters you intend to use (diacritics etc.). You can customize the font like so:

```clojure
:pdf-metadata {:font {:encoding :unicode
                      :ttf-name "/usr/share/fonts/truetype/ubuntu/Ubuntu-M.ttf"}}
```

A full list of options is described in [clj-pdf documentation](https://github.com/clj-pdf/clj-pdf#metadata).

## Logging

REMS uses [Logback](https://logback.qos.ch/) for logging. By default everything is printed to standard output. If you wish to customize logging, create your own Logback configuration file and specify its location using the `logback.configurationFile` system property:

    java -Dlogback.configurationFile=logback-prod.xml -jar rems.jar run

## Application expiration scheduler

REMS can be configured to delete applications after a set period of time has passed since last activity. Expiration can be defined for application states with ISO-8601 duration formatting, and optionally email notification can be configured to be sent to applicant and members before deletion. Application expiration scheduler is disabled by default. See `:application-expiration` in [config-defaults.edn](https://github.com/CSCfi/rems/blob/master/resources/config-defaults.edn) for details.

```clojure
{:application-expiration
 {:application.state/draft {:delete-after "P90D" :reminder-before "P7D"} ; delete draft applications that are over 90 days old, and send reminder emails 7 days before deletion
  :application.state/closed {:delete-after "P7D"}}} ; delete closed applications that are over 7 days old
```

## Shopping cart

REMS has a shopping cart feature which allows bundling multiple resources into single application. Shopping cart is enabled by default, and it can be enabled or disabled using the `:enable-cart` key in your `config.edn`. See [config-defaults.edn](https://github.com/CSCfi/rems/blob/master/resources/config-defaults.edn) for details.
