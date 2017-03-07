(ns rems.locales)

;; Note: the intermediate :t key in the dictionaries makes grepping
;; easier: all localizations are of the form :t/foo
(def tconfig
  {:dict
   {:en-GB
    {:t
     {:missing "Missing translation"
      :navigation {:login "Login"
                   :logout "Sign Out"
                   :about "About"
                   :home "Home"
                   :catalogue "Catalogue"}
      :login {:title "Login"
              :text "Login by using your Haka credentials"}
      :about {:text "this is the story of rems... work in progress"}
      :cart {:header "Resource in cart"
             :add "Add to cart"}
      :catalogue {:header "Resource"}}}
    :fi
    {:t
     {:missing "Käännös puuttuu"
      :navigation {:login "Kirjaudu sisään"
                   :logout "Kirjaudu ulos"
                   :about "Info"
                   :home "Etusivu"
                   :catalogue "Aineistoluettelo"}}
     :en :en-GB}}})
