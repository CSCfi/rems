# Hooks

## Extra scripts

You can configure REMS to include extra script files by defining configuration

```clj
 :extra-scripts {:root "/tmp" :files ["/scripts/foo.js"]}
```

There you can for example initialize your analytics scripts.

## Event hooks

If you want to hook code into additional events you can include a script file like the following. REMS will attempt to call hooks for `get`, `put` and `navigate` events, if they exist.

```js
function logIt(e) {
  // e.g. call your analytics solution here
  console.log(e);
}

window.rems = {};
window.rems.hooks = {};
window.rems.hooks.get = logIt;
window.rems.hooks.put = logIt;
window.rems.hooks.navigate = logIt;
```
