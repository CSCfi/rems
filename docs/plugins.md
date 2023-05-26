# Plugins

REMS can be extended with plugins in certain extension points.

The plugins are loaded dynamically from external code found in the specified file.

There should be a function defined in the plugin, that is called at the right time. The name of the function
depends on the type of the extension point.

## Types of plugins

### Transform

Take `data` and do any kind of transformations to it and return the new `data`. 

Return the original data if nothing should be done.

```clj
(defn transform [config data]
  ...)
```

### Validate

Validates the passed `data`. 

Returns the errors so an empty sequence (`nil` or `[]` for example) should be returned if everything was good.
Any errors will prevent the possible next validation from running.

```clj
(defn validate [config data]
  ...)
```

## Extension points

Next are all the current extension points and the function they expect to find in the plugin.

### `:extension-point/process-user-data`

After logging in, after opening the OIDC token and potentially fetching the user info, allow processing that data further.

### `:extension-point/validate-user-data`

After logging in, after the user data is finalized, allow validating it to prevent invalid users from logging in.

