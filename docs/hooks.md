# Hooks

## Extra scripts

You can configure REMS to include extra script files by defining configuration such as:

```clj
 :extra-scripts {:root "/tmp" :files ["/scripts/foo.js"]}
```

This means the script will be served from `/tmp/scripts/foo.js` on your local filesystem. The browser sees only the `/scripts/foo.js` part.

There you can for example initialize your analytics scripts.

## Event hooks

If you want to hook code into additional events you can include a script file like the following. REMS will attempt to call hooks for `get`, `put` and `navigate` events, if they exist.

```js
function logIt(e) {
  // e.g. call your analytics solution here
  console.log(e);
}

window.rems.hooks.get = logIt;
window.rems.hooks.put = logIt;
window.rems.hooks.navigate = logIt;
```

`window.rems.hooks.get` is called when more data is fetched from the API.
`window.rems.hooks.put` is called when a command is sent to the API.
`window.rems.hooks.navigate` is called whenever the Single-Page App changes page i.e. route.

All callbacks get the request path as first parameter. Both `get` and `put` callbacks get an additional parameter that is a map containing other request parameters. You can find query parameters and such from there.
