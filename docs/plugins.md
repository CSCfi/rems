# Plugins

REMS can be extended with plugins in certain extension points.

The plugins are loaded dynamically from external code found in the specified file.

There should be a function defined in the plugin, that is called at the right time. The name of the function
depends on the type of the extension point.

All the functions receive `config` where the plugin's configuration is (from the config file). Also context
specific `data` will be passed.

Certain types of libraries have been exposed to plugins. Please post an issue if you want more added.

Examples of plugins can be found in the `resources/plugins` directory.

## Types of plugins

### Transform

Take `data` and do any kind of transformations to it and return the new `data`.

Return the original data if nothing should be done.

```clj
(defn transform [config data]
  ...)
```

### Process

Processes the passed `data` for side-effects, such as integration to another server using HTTP requests.

Returns the errors so an empty sequence (`nil` or `[]` for example) should be returned if everything was good.
Any errors will prevent the possible next process from running.

In the case of a failure in processing of the same `data`, the process will be retried again, so the implementation should be idempotent. A retry can also happen for a successful process, if a processing plugin configured after this plugin fails.

```clj
(defn process [config data]
  ...)
```

### Validate

Validates the passed `data`.

Returns the errors so an empty sequence (`nil` or `[]` for example) should be returned if everything was good.
Any errors will prevent the possible next validation from running, and generally the action from happening (e.g. logging in).

```clj
(defn validate [config data]
  ...)
```

## Extension points

Next are all the current extension points and the function they expect to find in the plugin.

### `:extension-point/transform-user-data`

After logging in, after opening the OIDC token and potentially fetching the user info, allow transforming that data further. For example, a complex field can be parsed and the result stored in new fields.

Expects `transform` function.

See [AARC-G069-group-split.md](../resources/plugins/AARC-G069-group-split.md)

### `:extension-point/validate-user-data`

After logging in, after the user data is finalized, allow validating it to prevent invalid users from logging in.

Expects `validate` function.

See [validate-attributes.md](../resources/plugins/validate-attributes.md)

### `:extension-point/process-entitlements`

After entitlements have been updated, the new entitlements can be processed, and for example updated to
another system.

Expects `process` function.

See [LS-AAI-GA4GH-push.md](../resources/plugins/LS-AAI-GA4GH-push.md)
