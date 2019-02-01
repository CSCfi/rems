# 003: Namespaced keywords

Authors: @opqdonut @Macroz @luontola @cscwooller

Let's try out namespaced keywords in events & the API. In particular,
let's use short, non-hierarchic namespaces like `:application/id` or
`:event/time`, and avoid using ns aliases.

Other structures can also hinder greppability, like namespaced maps:
```clojure
#:event{:time 1 :id 2}
```
or namespaced destructuring:
```clojure
(let [{:event/keys [time id]} event]
  ...)
```

Pros of namespaced keys:

- no need to track context: `:application/id` instead of "Which `:id` is this?"
- merging data from various sources without (key) conflicts
- greppability (as long as we don't use ns aliases)
- good experiences with translation keys (e.g. `:t.form.validation/required`)

Cons:

- verbose (mitigated by using short namespaces)
