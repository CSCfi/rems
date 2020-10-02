# GA4GH Visas

REMS can produce cryptographically signed GA4GH Visas that assert a
user's access rights to resources.

In the language of the GA4GH specifications, REMS acts as a
[Passport Visa Assertion Repository](https://github.com/ga4gh-duri/ga4gh-duri.github.io/blob/master/researcher_ids/ga4gh_passport_v1.md#passport-visa-assertion-repository),
[Passport Visa Issuer](https://github.com/ga4gh-duri/ga4gh-duri.github.io/blob/master/researcher_ids/ga4gh_passport_v1.md#passport-visa-issuer)
and
[Embedded Token Issuer](https://github.com/ga4gh/data-security/blob/master/AAI/AAIConnectProfile.md#conformance-for-embedded-token-issuers)

More info about GA4GH visas:
- [The GA4GH Passport specification](https://github.com/ga4gh-duri/ga4gh-duri.github.io/blob/master/researcher_ids/ga4gh_passport_v1.md)
- [The GA4GH Authentication and Authorization Infrastructure specification](https://github.com/ga4gh/data-security/blob/master/AAI/AAIConnectProfile.md)
- [The GA4GH Genomic Data Toolkit](https://www.ga4gh.org/genomic-data-toolkit/)

## Current status

Visa support is experimental and has to be enabled with the `:enable-permissions-api` configuration parameter.

After this, the `/api/permissions` API can be used to query visas for a given user.
[See the API docs in the development environment.](https://rems-dev.rahtiapp.fi/swagger-ui/index.html#/permissions).

The API returns one visa in the
[GA4GH Embedded Token format](https://github.com/ga4gh/data-security/blob/master/AAI/AAIConnectProfile.md#embedded-token-issued-by-embedded-token-issuer)
per each resource the user is entitled to. The Visas are signed with
the RSA private key specified in the `:ga4gh-visa-private-key`
configuration parameter. The corresponding public key should be
configured via the `:ga4gh-visa-public-key` parameter. As the
specification requires, the Visa headers have a `"jku"` parameter,
that points to the `/api/jwk` url, where the public key can be fetched
for verifying the Visa.
