# Configuration

REMS contains a number of configuration options that can be used to alter authentication options, theming or to add integration points, just to name a few.

Configuration uses the [cprop](https://github.com/tolitius/cprop) library. You can specify the location of the configuration file by setting the `conf` system property: `java -Dconf="../somepath/config.edn" -jar rems.jar` You can also configure the application using environment variables as described in the cprop documentation.

The full list of available configuration options can be seen in [config-defaults.edn](https://github.com/CSCfi/rems/blob/master/resources/config-defaults.edn).  

## Authentication options

Currently supported authentication methods are SAML2 and LDAP. Login method to be used can be defined with the key `:authentication` and the following values are recognized:

* `:shibboleth` for SAML2
* `:ldap`
* `:fake-shibboleth` for development login

### SAML2 (`:shibboleth`)

When using this option, login requests are directed to `/Shibboleth.sso/Login`.

### LDAP (`:ldap`)

Using LDAP as the authentication method requires that additional configuration with the following structure is provided:
```
:ldap {:connection {:host "your-host-name"
                    :ssl? true}
       :search-root "dc=some,dc=thing"}
```

### Development login (`:fake-shibboleth`)

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

Default language used in the application is English. To change the behaviour an optional key `:default-language` to your production environment's `config.edn` file.

Localization files are located under `resources/translations`. To change a text simply provide a new value for the key you want to change.

## Themes

Custom themes can be added by creating a file, for example `my-custom-theme.edn`, and specifying its location in the `:theme-path` configuration parameter. The theme file can override some or all of the theme attributes (see [theme-defaults.edn](https://github.com/CSCfi/rems/blob/master/resources/theme-defaults.edn)). If there is a `public` directory next to the theme configuration file, it will be exposed to the web as a static resources directory. See [lbr-theme](https://github.com/CSCfi/rems/tree/master/lbr-theme) for an example theme.

To quickly validate that all UI components look right navigate to `/#/guide`. See it in action at <https://rems2demo.csc.fi/#/guide>.
