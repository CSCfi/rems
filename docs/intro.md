# Introduction

# API use the 1 minute version

Checking what catalogue items are available

```sh
curl https://rems2demo.csc.fi/api/catalogue
```

Returns the JSON response with the catalogue items

```json
[{"id":1,"title":"non-localized title","wfid":1,"formid":1,"resid":"http://urn.fi/urn:nbn:fi:lb-201403262","state":"enabled","localizations":{"en":{"id":1,"langcode":"en","title":"ELFA Corpus, direct approval"},"fi":{"id":1,"langcode":"fi","title":"ELFA-korpus, suora hyväksyntä"}}}, ...]
```

See other methods in the [Swagger API documentation](https://rems2demo.scs.fi/swagger-ui).

# UI deep linking

## Linking into catalogue

```
https://rems2demo.csc.fi/#/catalogue
```

## Linking into a new application

This application has items with catalogue item ids 2 and 3.

```
https://rems2demo.csc.fi/#/application?items=2,3
```

## Linking into an existing application

```
https://rems2demo.csc.fi/#/application/2
```
