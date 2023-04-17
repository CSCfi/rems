# Perun group parse

A plugin to parse Perun group attributes. (AARC G069)

The OIDC attribute name is configured in the `:attribute-name` config:

    urn:geant:lifescience-ri.eu:group:example-vo.lifescience-ri.eu#aai.lifescience-ri.eu
    urn:geant:elixir-europe.org:group:elixir:ELIXIR%20AAI:staff#perun.elixir-czech.cz

The format comes from AARC G069 as so:

    <NAMESPACE>:group:<GROUP>[:<SUBGROUP>*][:role=<ROLE>][#<AUTHORITY>]

Where the <NAMESPACE> is:

    urn:geant:lifescience-ri.eu
    urn:geant:elixir-europe.org

The <AUTHORITY> comes after `#`:

    aai.lifescience-ri.eu
    perun.elixir-czech.cz

For a group to be valid the authority must declared in config `:trusted-authorities`.

The part `:group:` is just a splitter.

The middle part are the <GROUP> (and <SUBGROUP>s) of the person:

    example-vo.lifescience-ri.eu (only group)
    elixir:ELIXIR%20AAI:staff (group "elixir", subgroup "ELIXIR AAI", subgroup "staff")

The <ROLE> is currently handled as just another subgroup.

```clj
(require '[rems.config :refer [env]])
(require '[clojure.string :as str])

(when (:log-authentication-details env)
  (log/info "Data" data))

(let [attribute-name (get config :attribute-name "eduperson_entitlement")]
  (if-some [attribute-value (get data (keyword attribute-name))]
    (let [_ (prn attribute-value)
          ;;trusted-authorities (set (get config :trusted-authorities))
          group-index (str/index-of attribute-value ":group:")
          authority-index (str/index-of attribute-value "#")]

      (when (:log-authentication-details env)
        (log/info "Indices" group-index authority-index))

      (if (and group-index
               authority-index
               (< -1 group-index authority-index))
        (let [namespace (subs attribute-value 0 group-index)
              groups-and-role (subs attribute-value
                                    (+ group-index (count ":group:"))
                                    authority-index)
              authority (subs attribute-value (+ authority-index (count "#")))

              groups (str/split groups-and-role #":")]

          (when (:log-authentication-details env)
            (log/info "Parts" namespace groups authority))

          ;; TODO: check trusted authority

          (assoc data :groups groups))
        data))
    data))

```
