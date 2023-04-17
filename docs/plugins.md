# Plugins

REMS can be extended with plugins in certain extension points.

The plugins are loaded dynamically from external code found in the specified file.

## Extension points

Next are all the current extension points.

### `:extension-point/process-user-data`

After logging in, after opening the OIDC token and potentially fetching the user info, allow processing that data further.

### `:extension-point/validate-user-data`

After logging in, after the user data is finalized, allow validating it to prevent invalid users from logging in.

