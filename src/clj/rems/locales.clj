(ns rems.locales)

(def tconfig
  {:dict
   {:en-GB
    {:missing "Missing translation"
     :navigation {:login "Login"
                  :logout "Sign Out"
                  :about "About"
                  :home "Home"
                  :catalogue "Catalogue"}}
    :fi
    {:missing "Käännös puuttuu"
     :navigation {:login "Kirjaudu sisään"
                  :logout "Kirjaudu ulos"
                  :about "Info"
                  :home "Etusivu"
                  :catalogue "Aineistoluettelo"}}
    :en :en-GB}})
