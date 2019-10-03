---
---

# 003: Namespaced keywords

Authors: @opqdonut @Macroz @luontola @cscwooller

# Background

There are various ways of using namespaced keywords in clojure:
- fully qualified: `:name.space/key`
  - note that `name.space` can either be an actual namespace that contains code,
  - ... or a namespace that's used _just_ in keyword names
- private: `::key` means `:name.space/key` when in ns `name.space`
- with an alias: `(require '[name.space :as space]) ... ::space/key`

There are various places we use keywords:
- data keys (in events, api responses, etc.)
- "keyword argument" names
- translation keys
- re-frame event and subscription names

# Current status

Our data keys are short and often confusing normal keywords, like
`:id`. This is what we want to fix.

We use fully qualified keywords for translation keys. This is nice.

We use private keywords for re-frame events and subscriptions. This is nice.

# The decision

Let's try out _fully qualified_ namespaced keywords in _data keys_.
This means keys of (application) events and API responses in
particular. Let's use short namespaces like `:application/id` or
`:event/time`. Note that namespaces `application` and `event` don't
need to contain any code.

Let's avoid structures that hinder greppability, like namespaced maps
and namespaced destructuring:
```clojure
#:event{:time 1 :id 2}

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
