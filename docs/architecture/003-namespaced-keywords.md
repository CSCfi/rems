# 003: Namespaced keywords

Authors: @opqdonut @Macroz @luontola @cscwooller

# Background

There are various ways of using namespaced keywords in clojure:
- fully qualified: `:name.space/key`
- private: `::key` means `:name.space/key` when in ns `name.space`
- with an alias: `(require '[name.space :as space]) ... ::space/key`

There are various places we use keywords:
- data keys (in events, api responses, etc.)
- "keyword argument" names
- re-frame event and subscription names

# The decision

Let's try out _fully qualified_ namespaced keywords in _data keys_.
This means keys of (application) events and API responses in
particular.

Let's use short, non-hierarchic namespaces like `:application/id` or
`:event/time`.

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
