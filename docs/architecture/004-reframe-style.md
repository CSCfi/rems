# 004: re-frame handlers

Authors: @hukka

## Handling UI events and side-effects

re-frame architecture allows splitting all side-effects from event handlers.
The input (not covered by function variables) are called coeffects
and the output effects.
In short, all event handlers are pure functions from coeffect map and event vector
(containing the event name and parameters)
to an effect map.

[The listed reasons](https://github.com/Day8/re-frame/blob/master/docs/EffectfulHandlers.md#bad-why) are

> 1. Cognitive load for the function's later readers goes up because they can no longer reason locally.
> 2. Testing becomes more difficult and involves "mocking". How do we test that the http GET above is using the right URL? "mocking" should be mocked. It is a bad omen.
> 3. And event replay-ability is lost.

As REMS is a relatively simple SPA, we have found that the added indirection actually causes more cognitive load,
we are not unit testing event handlers anyway,
and we haven't used time travelling debugger or anything else that benefits from replay-ability.

Therefore we have decided that it's ok to cause simple side-effects,
such as fetching data from the backend,
directly within event handlers.

In other words, this is how we used to do things:

```clojure
(rf/reg-event-fx
 ::send-request-decision
 (fn [{{::keys [deciders application-id comment]} :db} _]
   {:request-decision-effect [deciders application-id comment]}))

(rf/reg-fx
 :request-decision-effect
 (fn [[deciders application-id comment]]
   (fetch "/api/applications/request-decision"
          {:params {:deciders deciders
                    :application-id application-id
                    :comment comment}
           :handler #(rf/dispatch [::decision-results %]})))
```

In some cases the effect handler was not even calling fetch directly,
but had one more indirection of calling a specific function `fetch-this-one-thing!` without parameters.
It's slightly better to inline the actual fetch to the effect,
but we will instead write

```clojure
(rf/reg-event-fx
 ::send-request-decision
 (fn [{{::keys [deciders application-id comment]} :db} _]
   (fetch "/api/applications/request-decision"
          {:params {:deciders deciders
                    :application-id application-id
                    :comment comment}
           :handler #(rf/dispatch [::decision-results %]})})
   {}))
```

In particular do not mix side-effects and effect handlers in the same flow!

```clojure
(rf/reg-event-fx
 ::send-request-decision
 (fn [{{::keys [deciders application-id comment]} :db} _]
   (do-another-kind-of-side-effect!)
   {:request-decision-effect [deciders application-id comment]}))
```

## Handling user interaction in components

We could also launch the fetch directly from components (e.g. buttons on-click handlers)
but we will always dispatch a re-frame event for consistency,
since quite often we need to do something else than just fire API calls.
One common case is to set some kind of loading toggle in the db,
that in turn will show a loading spinner while the data hasn't yet arrived.
Some other usecases might be setting purely internal, UI state,
such as sorting options and moving between pages.

So instead of

```clojure
[action-button {:id action-form-id
                :text (text :t.actions/request-decision)
                :on-click #(do-a-fetch!)}])
```

we will write

```clojure
[action-button {:id action-form-id
                :text (text :t.actions/request-decision)
                :on-click #(rf/dispatch [::send-request-decision])}])
```
