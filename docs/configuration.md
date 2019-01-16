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

To add localization in a new language, make a copy of the [English localization file](https://github.com/CSCfi/rems/blob/master/resources/translations/en.edn) and change the texts. Place it in a directory and configure `:translations-directory` to point to that directory. The localization file must be named after the language code. Add the language code to `:languages` to make it available in the application. You can change the default language by configuring `:default-language`.

For example, to add German localization, create a file `my-translations/de.edn` and use the following configuration:

```clojure
{:translations-directory "my-translations"
 :languages [:de :en]
 :default-language :de}
```

## Themes

Custom themes can be used by creating a file, for example `my-custom-theme.edn`, and specifying its location in the `:theme-path` configuration parameter. The theme file can override some or all of the theme attributes (see `:theme` in [config-defaults.edn](https://github.com/CSCfi/rems/blob/master/resources/config-defaults.edn)). Static resources can be placed in a `public` directory next to the theme configuration file. See [theme-lbr](https://github.com/CSCfi/rems/tree/master/theme-lbr) for an example theme.

To quickly validate that all UI components look right navigate to `/#/guide`. See it in action at <https://rems2demo.csc.fi/#/guide>.

## Logging

REMS uses [Logback](https://logback.qos.ch/) for logging. By default everything is printed to standard output. If you wish to customize logging, create your own Logback configuration file and specify its location using the `logback.configurationFile` system property:

    java -Dlogback.configurationFile=logback-prod.xml -jar rems.jar
