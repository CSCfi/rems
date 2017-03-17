(ns rems.locales)

;; Note: the intermediate :t key in the dictionaries makes grepping
;; easier: all localizations are of the form :t/foo or :t.something/foo
(def tconfig
  {:dict
   {:en-GB
    {:t
     {:missing "Missing translation"
      :footer "Powered by CSC - IT Center for Science"
      :navigation {:login "Login"
                   :logout "Sign Out"
                   :about "About"
                   :home "Home"
                   :catalogue "Catalogue"
                   :applications "Applications"}
      :login {:title "Login"
              :text "Login by using your Haka credentials"}
      :about {:text "this is the story of rems... work in progress"}
      :cart {:header "Resource in cart"
             :add "Add to cart"
             :remove "Remove"
             :apply "Apply"
             :checkout "Check out"}
      :catalogue {:header "Resource"}
      :form {:apply "Apply"
             :save "Save as draft"}
      :applications {:application "Application"
                     :resource "Resource"
                     :user "User"}}}

    :fi
    {:t
     {:missing "Käännös puuttuu"
      :footer "CSC - IT Center for Science"
      :navigation {:login "Kirjaudu sisään"
                   :logout "Kirjaudu ulos"
                   :about "Info"
                   :home "Etusivu"
                   :catalogue "Kielivarat"
                   :applications "Hakemukset"}
      :login {:title "Kirjaudu sisään"
              :text "Kirjaudu sisään Haka-tunnuksillasi"}
      :about {:text "hauki on kala"}
      :cart {:header "Kielivarat korissa"
             :add "Lisää koriin"
             :remove "Poista"
             :apply "Hae"
             :checkout "Lähetä"}
      :catalogue {:header "Kielivarat"}
      :form {:apply "Hae"
             :save "Tallenna luonnos"}
      :applications {:application "Hakemus"
                     :resource "Kielivara"
                     :user "Käyttäjä"}}}
    :en :en-GB}})
