---
---

# Configuration

REMS contains a number of configuration options that can be used to alter authentication options, theming or to add integration points, just to name a few.

Configuration uses the [cprop](https://github.com/tolitius/cprop) library. You can specify the location of the configuration file by setting the `rems.config` system property: `java -Drems.config="../somepath/config.edn" -jar rems.jar` You can also configure the application using environment variables as described in the cprop documentation.

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

## Themes

Custom themes can be used by creating a file, for example `my-custom-theme.edn`, and specifying its location in the `:theme-path` configuration parameter. The theme file can override some or all of the theme attributes (see `:theme` in [config-defaults.edn](https://github.com/CSCfi/rems/blob/master/resources/config-defaults.edn)). Static resources can be placed in a `public` directory next to the theme configuration file. See [example-theme/theme.edn](https://github.com/CSCfi/rems/blob/master/example-theme/theme.edn) for an example.

To quickly validate that all UI components look right navigate to `/guide`. See it in action at <https://rems2demo.csc.fi/guide>.

## Extra pages

Extra pages can be added to the navigation bar using `:extra-pages` configuration parameter. Each extra page can be either a link to an external url or it can show the content of a markdown file to the user. See `:extra-pages` in [config-defaults.edn](https://github.com/CSCfi/rems/blob/master/resources/config-defaults.edn) for examples of how to define the extra pages.

## Logging

REMS uses [Logback](https://logback.qos.ch/) for logging. By default everything is printed to standard output. If you wish to customize logging, create your own Logback configuration file and specify its location using the `logback.configurationFile` system property:

    java -Dlogback.configurationFile=logback-prod.xml -jar rems.jar
