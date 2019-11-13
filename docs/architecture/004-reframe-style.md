# 004: re-frame handlers

Authors: @hukka

## Handling UI events and side-effects

The re-frame architecture allows for the separation of side-effects from the event handler code by the way of fx and co-fx.

The input consists of the event vector with the event name and parameters, and a map of coeffects. The output is called effects, of which the db effect is the most common. In other words, all event handlers are pure functions from the coeffect map and event vector to the effect map.

**This is how re-frame suggests you should to do things:**

```clojure
;; AVOID

(rf/reg-event-fx
 ::send-request-decision
 (fn [{{::keys [deciders application-id comment]} :db} _]
   {:request-decision-effect [deciders application-id comment]}))             ; second indirection

(rf/reg-fx
 :request-decision-effect
 (fn [[deciders application-id comment]]
   (fetch "/api/applications/request-decision"                                ; the side-effect
          {:params {:deciders deciders
                    :application-id application-id
                    :comment comment}
           :handler #(rf/dispatch [::decision-results %]})))

[action-button {:id action-form-id
                :text (text :t.actions/request-decision)
                :on-click #(rf/dispatch [::send-request-decision])}]          ; first indirection
```

[The listed reasons are](https://github.com/Day8/re-frame/blob/master/docs/EffectfulHandlers.md#bad-why)

> 1. Cognitive load for the function's later readers goes up because they can no longer reason locally.
> 2. Testing becomes more difficult and involves "mocking". How do we test that the http GET above is using the right URL? "mocking" should be mocked. It is a bad omen.
> 3. And event replay-ability is lost.

As REMS is a relatively simple SPA, we have found that the added indirection actually causes more cognitive load. When you find e.g. a click handler, you have to find the effect handler, and then all the coeffects and effects, all of which are referred to with keywords so jumping to definition is slower. Also they are not necessarily co-located.

Also, we are not unit testing the event handlers anyway, because they are for the most part straightforward. All important logic can be split to helper functions that are easy to test with regular ClojureScript test functionality.

Furthermore we haven't used a time travelling debugger or anything else that benefits from the replay-ability.

**Therefore we have decided that it's ok to cause simple side-effects, such as fetching data from the backend, directly within event handlers.**

**We will therefore write our handlers like this:**

```clojure
(rf/reg-event-fx
 ::send-request-decision
 (fn [{{::keys [deciders application-id comment]} :db} _]
   (fetch "/api/applications/request-decision"           ; side-effect without the second indirection
          {:params {:deciders deciders
                    :application-id application-id
                    :comment comment}
           :handler #(rf/dispatch [::decision-results %])})
   {}))
```

In particular we do not want to mix side-effects and effect handlers in the same flow! **Please avoid doing this mixed model** because it's even more confusing than either solution!

```clojure
;; AVOID
(rf/reg-event-fx
 ::send-request-decision
 (fn [{{::keys [deciders application-id comment]} :db} _]
   (do-another-kind-of-side-effect!)                                 ; side-effect without indirection
   {:request-decision-effect [deciders application-id comment]}))    ; side-effect with indirection
```

## Handling user interaction in components

We could also launch the fetch directly from components (e.g. buttons with on-click handlers)
but we will always dispatch a re-frame event for consistency,
since quite often we need to do something else than just fire API calls.

One common case is to set some kind of loading toggle in the db,
that in turn will show a loading spinner while the data hasn't yet arrived.
Some other usecases might be setting purely internal, UI state,
such as sorting options and moving between pages.

So instead of

```clojure
;; AVOID
[action-button {:id action-form-id
                :text (text :t.actions/request-decision)
                :on-click #(do-a-fetch!)}]                           ; side-effect
```

we will write

```clojure
[action-button {:id action-form-id
                :text (text :t.actions/request-decision)
                :on-click #(rf/dispatch [::send-request-decision])}] ; indirection
```
