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
                   :applications "Applications"
                   :approvals "Approvals"}
      :login {:title "Login"
              :text "Login by using your Haka credentials"}
      :about {:text "this is the story of rems... work in progress"}
      :cart {:header "%1 resources in cart"
             :add "Add to cart"
             :remove "Remove"
             :apply "Apply"
             :checkout "Check out"}
      :catalogue {:header "Resource"}
      :form {:save "Save as draft"
             :optional "(optional)"
             :licenses "Licenses"
             :saved "Draft saved."
             :submit "Send application"
             :submitted "Application submitted."
             :back "Back to catalogue"
             :validation {:required "Field \"%1\" is required."}}
      :applications {:application "Application"
                     :resource "Resource"
                     :user "User"
                     :state "State"
                     :view "View"
                     :created "Created"
                     :states {:draft "Draft"
                              :applied "Applied"
                              :approved "Approved"
                              :unknown "Unknown"}}
      :approvals {:application "Application"
                  :resource "Resource"
                  :applicant "Applicant"
                  :view "View"
                  :created "Created"
                  :approve "Approve"
                  :success "Application approved"}
      :roles {:header "Select role:"
              :names {:approver "Approver"
                      :reviewer "Reviewer"
                      :applicant "Applicant"}}}}
    :fi
    {:t
     {:missing "Käännös puuttuu"
      :footer "CSC - IT Center for Science"
      :navigation {:login "Kirjaudu sisään"
                   :logout "Kirjaudu ulos"
                   :about "Info"
                   :home "Etusivu"
                   :catalogue "Kielivarat"
                   :applications "Hakemukset"
                   :approvals "Hyväksynnät"}
      :login {:title "Kirjaudu sisään"
              :text "Kirjaudu sisään Haka-tunnuksillasi"}
      :about {:text "hauki on kala"}
      :cart {:header "%1 kielivaraa korissa"
             :add "Lisää koriin"
             :remove "Poista"
             :apply "Hae"
             :checkout "Lähetä"}
      :catalogue {:header "Kielivarat"}
      :form {:save "Tallenna luonnos"
             :optional "(ei pakollinen)"
             :licenses "Lisenssiehdot"
             :saved "Luonnos tallennettu."
             :submit "Lähetä hakemus"
             :submitted "Hakemus lähetetty."
             :back "Takaisin kielivaroihin"
             :validation {:required "Kenttä \"%1\" on pakollinen."}}
      :applications {:application "Hakemus"
                     :resource "Kielivara"
                     :user "Käyttäjä"
                     :state "Tila"
                     :view "Näytä"
                     :created "Luotu"
                     :states {:draft "Luonnos"
                              :applied "Haettu"
                              :approved "Hyväksytty"
                              :unknown "Tuntematon"}}
      :approvals {:application "Hakemus"
                  :resource "Kielivara"
                  :applicant "Hakija"
                  :view "Näytä"
                  :created "Luotu"
                  :approve "Hyväksy"
                  :success "Hakemus hyväksytty"}
      :roles {:header "Valitse rooli:"
              :names {:approver "Hyväksyjä"
                      :reviewer "Katselmoija"
                      :applicant "Hakija"}}}}
    :en :en-GB}})
