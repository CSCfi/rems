# REMS-demo

Tämä on demo-ympäristö, joka on tarkoitettu REMS-ohjelmiston kokeilemiseen. Demo-ympäristöön on luotu valmiiksi joitain kuvitteellisia tietoaineistoja, joihin voit hakea käyttöoikeutta. Kullekin tietoaineistolle on luotu hakulomake, jonka hakija täyttää ja lähettää, sekä työnkulku, joka määrittelee, minkälaisen käsittelypolun lähetetty lomake kulkee.

Demo-ympäristöön voi kirjautua eri rooleissa alla olevilla käyttäjätunnuksilla ja salasanoilla. REMS-demo ei kontrolloi minkään todellisen tietoaineiston käyttöoikeuksia. Demo-ympäristöstä ei myöskään lähetetä sähköposti-ilmoituksia käyttöoikeuden hakijoille, kommentoijille tai hyväksyjille.

**Demoympäristö saatetaan nollata ajoittain**, jolloin kaikki tehdyt hakemukset ja käyttöoikeudet pyyhkiytyvät. Huomioi kuitenkin, että demotunnuksilla tekemäsi hakemukset ja muutokset saattavat sitä ennen näkyä muille kokeilijoille.

[Lisätietoa REMS-ohjelmistosta](http://www.csc.fi/rems).

REMS-tuki: rems@csc.fi

## Testitilit ja roolit

Hakan testi-IdP:ltä löytyy seuraavia testitunnuksia:

**1. Käyttöluvanhakija 1**<br/>
Käyttäjätunnus: RDapplicant1<br/>
Salasana: RDapplicant1<br/>
Kuvaus: Kirjaudu tällä tunnuksella hakeaksesi tietoaineiston käyttöoikeutta.

**2. Käyttöluvanhakija 2**<br/>
Käyttäjätunnus: RDapplicant2<br/>
Salasana: RDapplicant2<br/>
Kuvaus: Toinen tunnus tietoaineiston käyttöoikeuden hakemiseen. RDapplicant1 voi vaikkapa kutsua tämän käyttäjän käyttölupahakemuksensa toiseksi jäseneksi, jolloin käyttöoikeuksia anotaan samalla myös hänelle.

**3. Reviewer**<br/>
Käyttäjätunnus: RDreview<br/>
Salasana: RDreview<br/>
Kuvaus: Katselmoija voi kommentoida saapunutta käyttölupahakemusta mutta ei voi hyväksyä tai hylätä sitä.

**4. Approver 1**<br/>
Käyttäjätunnus: RDapprover1<br/>
Salasana: RDapprover1<br/>
Kuvaus: Hyväksyjä hyväksyy tai hylkää saapuneen käyttölupahakemuksen tai palauttaa sen täydennettäväksi.

**5. Approver 2**<br/>
Käyttäjätunnus: RDapprover2<br/>
Salasana: RDapprover2<br/>
Kuvaus: Demon eräillä tietoaineistoilla on useita rinnakkaisia tai vaihtoehtoisia hyväksyjiä.

**6. Dataset Owner**<br/>
Käyttäjätunnus: RDowner<br/>
Salasana: RDowner<br/>
Kuvaus: Aineiston omistaja voi lisätä uusia tietoaineistoja ja muuttaa niiden ominaisuuksia, kuten hakulomaketta, hyväksymisprosessia ja hyväksyjiä.

## Aineistot

**ELFA-korpus, suora hyväksyntä**<br/>
Kuvaus: Tässä aineistossa on minimi työkulku. Käyttäjä sitoutuu lisenssin ehtoihin ja saa käyttöluvan.

**ELFA-korpus, yksi hyväksyntä**<br/>
Kuvaus: Tässä aineistossa on yksinkertainen työkulku. Hakemus lähetetään yhdelle henkilölle (RDapprover1) hyväksyttäväksi.

**ELFA-korpus, katselmoinnilla**<br/>
Kuvaus: Tässä aineistossa on yksinkertainen työkulku. Katselmoija (RDreview) voi vain kommentoida hyväksyjälle (RDapprover1) menevää hakemusta.

**ELFA-korpus, kaksi hyväksyntäkierrosta eri hyväksyjillä**<br/>
Kuvaus: Tässä aineistossa on kaksivaiheinen hyväksyntä. Hakemuksen hyväksyy ensin käyttäjä RDapprover1 ja sitten käyttäjä RDapprover2.

## Käyttöehdot

Sitoudut siihen, että et tee REMS-demossa mitään sellaista joka loukkaa lakeja tai asetuksia tai kolmannen osapuolen oikeuksia (kuten tekijänoikeuksia) tai on säädytöntä, uhkaavaa, väkivaltaista, herjaavaa, vihamielistä, häiritsevää tai muulla tavalla paheksuttavaa.

Sitoudut siihen, että et syötä REMS-demoon mitään tunnistettua tai tunnistettavissa olevaa luonnollista henkilöä koskevaa tietoa.

## Rahoittaja

CSC:n REMS-tuote on saanut rahoitusta Opetus- ja kulttuuriministeriöltä ja Suomen akatemialta (apurahat 271642 ja 263164 Biomedinfraa varten, joka on Suomen ELIXIR, BBMRI ja EATRIS-tutkimusinfrastruktuurisolmujen muodostama konsortio).
