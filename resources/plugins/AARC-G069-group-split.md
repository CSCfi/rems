# AARC-G069 group parse

A plugin to parse group attributes according to AARC-G069.

The format comes from AARC-G069 as so:

    <NAMESPACE>:group:<GROUP>[:<SUBGROUP>*][:role=<ROLE>][#<AUTHORITY>]

The OIDC attribute name that is parsed is configured in the `:attribute-name` config. With these example attibutes:

    urn:geant:lifescience-ri.eu:group:example-vo.lifescience-ri.eu#aai.lifescience-ri.eu
    urn:geant:elixir-europe.org:group:elixir:ELIXIR%20AAI:staff#perun.elixir-czech.cz

The <NAMESPACE> is:

    urn:geant:lifescience-ri.eu
    urn:geant:elixir-europe.org

The <AUTHORITY> comes after `#`:

    aai.lifescience-ri.eu
    perun.elixir-czech.cz

The part `:group:` is just a splitter like the `#` is.

The middle part are the <GROUP> (and <SUBGROUP>s) of the person:

    example-vo.lifescience-ri.eu (only group)
    elixir:ELIXIR%20AAI:staff (group "elixir", subgroup "ELIXIR AAI", subgroup "staff")

The <ROLE> is not present in the example but it is currently handled as just another subgroup. So
a `role=R` would literally be just an additional `role=R` group.

For a group to be valid the authority must declared in config `:trusted-authorities`.

```clj
(require '[clojure.string :as str])
(require '[clojure.tools.logging :as log])
(require '[rems.config :refer [env]])
(require '[rems.common.util :refer [getx]])

(defn transform [config data]
  (when (:log-authentication-details env)
    (log/info "Data" data))

  (let [attribute-name (getx config :attribute-name)]
    (if-some [attribute-value (get data (keyword attribute-name))]
      (let [group-index (str/index-of attribute-value ":group:")
            authority-index (str/index-of attribute-value "#")]

        (when (:log-authentication-details env)
          (log/info "Indices" group-index authority-index))

        (if (and group-index
                 authority-index
                 (< -1 group-index authority-index))
          (let [namespace (subs attribute-value 0 group-index)
                groups-and-role (subs attribute-value (+ group-index (count ":group:")) authority-index)
                authority (subs attribute-value (+ authority-index (count "#")))

                groups (str/split groups-and-role #":")]

            (when (:log-authentication-details env)
              (log/info "Parts" namespace groups authority))

            ;; TODO: check trusted authority

            (assoc data :groups groups))
          data))
      data)))

```
