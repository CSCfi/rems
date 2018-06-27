# Configuration

REMS contains a number of configuration options that can be used to alter authentication options, theming or to add integration points, just to name a few.

Configuration can be found under the `env` folder and [cprop](https://github.com/tolitius/cprop) library is used to handle the configuration options. For example, to change configuration for your production environment edit the `config.edn` file under the `env/prod/resources` folder.

## Authentication options

Currently supported authentication methods are SAML2 and LDAP. Login method to be used can be defined with the key `:authentication` and the following values are recognized:

* `:fake-shibboleth`
* `:shibboleth`
* `:ldap`

### SAML2

When using this option, login requests are directed to `/Shibboleth.sso/Login`.

### LDAP

Using LDAP as the authentication method requires that additional configuration with the following structure is provided:
```
:ldap {:connection {:host "your-host-name"
                    :ssl? true}
       :search-root "dc=some,dc=thing"}
```

### Development login

This option should not be used in production. Keep also in mind that anyone with access to the dev/test server using development authentication can login with your fake credentials.

## Entitlements

REMS can submit a HTTP POST request for every entitlement granted or
revoked.

To use this feature, add `:entitlements-target {:add
"http://url/to/post/to" :remove "http://other/url"}` to `config.edn`.

The payload of the POST request is JSON, and looks like this:

```json
{"application": 137,
 "user": "username",
 "resource": "resource_name_maybe_urn_or_something"
 "email": "bob@example.com"}
```

## Localization

Localization files are located under `resources/translations`. To change a text simply provide a new value for the key you want to change.

## Themes

Custom themes can be added by creating a file, for example
my-custom-theme.edn, to the resources/themes folder. The theming
allows custom themes to only partially override the default
attributes. To take the new theme into use add a key/value pair,
`:theme "my-custom-theme"` in the case of your example, to the
appropriate config.edn under the env folder.

To quickly validate that all UI components look right navigate to `/#/guide`. See it in action at <https://rems2demo.csc.fi/#/guide>.