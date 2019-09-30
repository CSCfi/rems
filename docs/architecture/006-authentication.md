# 006: Authentication using OIDC

Authors: @hukka

# Terms

<dl>
<dt>JWS</dt>
<dd>
JSON Web Signature, RFC 7515; a standard format to sign JSON data.

Consists of three parts:
- JWS Protected Header that specifies that the message is JWS and the signing algorithm,
- the payload,
- and the HMAC of the first two parts.

The human readable header is for example:
```
{"typ":"JWT",
 "alg":"HS256"}
```
and the payload (valid JWT in this example)
```
{"iss":"joe",
 "exp":1300819380,
 "http://example.com/is_root":true}
```

Base64URL encoded and concatenated with the corresponding signature using JWK
```
{"kty":"oct",
 "k":"AyM1SysPpbyDfgZld3umj1qzKObwVMkoqQ-EstJQLr_T-1qS0gZH75
      aKtMN3Yj0iPS4hcgUuTwjAzZr1Z9CAow"
}
```
(which is not included in the JWS message, but could be linked with `kid` parameter)
the final message is:
```
eyJ0eXAiOiJKV1QiLA0KICJhbGciOiJIUzI1NiJ9
.
eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFt
cGxlLmNvbS9pc19yb290Ijp0cnVlfQ
.
dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```
</dd>

<dt>JWT</dt>
<dd>JSON Web Token, RFC 7519; JSON based, Base64url encoded format for transferring claims.
Data can be transferred as unsecured, or in JWS or JWE payload
(note that OIDC allows only signed JWT, with or without encryption).
</dd>

<dt>OAuth2</dt>
<dd>RFC 6749; authorization protocol based on browser redirects and JSON, supersedes OAuth1.</dd>

<dt>OIDC</dt>
<dd>OpenID Connect; authentication protocol on top of OAuth2 and signed JWT, supersedes OpenId 2.0.</dd>

<dt>OIDC client</dt>
<dd>a system that requires authentication services — in this context REMS is a client, while the actual person is user and service doing the authentication is identity provider.</dd>

<dt>SAML</dt>
<dd>Security Assertion Markup Language; XML based protocol for authentication and authorization.</dd>

<dt>Shibboleth</dt>
<dd>Single Sign-On system based on SAML.</dd>

<dt>Elixir</dt>
<dd>EU level life sciences consortium.</dd>

<dt>Elixir AAI proxy</dt>
<dd>Autehntication and Authorisation Infrastructure; Elixir's authentication system that aggregates different ways for users to log in, and works as a SAML and OIDC identity provider for other systems, like REMS.</dd>
</dl>

# Background

REMS has had a hard requirement to let university users log in. The way to do this has been using Haka, the Shibboleth federation of different universities. This has pretty much required using Apache HTTP server and Tomcat, since they have robust implementations for Shibboleth.

In addition there are new requirements to let people sign in (to some REMS instances) using other credentials, like Google, Facebook or Auth0. They offer authentication using SAML and OIDC.

But Elixir also operates an authentication proxy (AAI proxy), that can act as a gateway to several other authentication systems, including Haka and many social media identities, and can handle a single user having multiple different identities in different identity systems. The different identities are aggregated into one, and Elixir can then also query REMS for any given entitlements using that single, unified identity. The AAI proxy also offers authentication via SAML or OIDC.


# How does OIDC work

OpenID Connect has several different flows for different use cases. They vary in their complexity and security.

Implicit flow is used for clients that cannot hold secrets, such as web apps without backend component, that are distributed fully to end users. It can be made more secure by careful use of CORS.

Authorization code flow has extra communication directly between the service backend and the identity provider, and is the one that REMS will use. In this flow:

1. User presses the login button on REMS.
2. REMS replies with a redirect to the identity provider's URL
   (REMS gives the IdP extra data in query parameters of the redirect, such as what kind of information we want from the user and where should the IdP send the user back after the authentication).
3. User's browser loads the IdP's web site, accepts the authentication request, and possibly does the actual authentication at this point.
4. The IdP replies with a redirect back to REMS, sending an authorization code in the request parameters.
5. REMS gets the code from the user's request, connects directly to IdP and uses the code and a shared secret to get user's profile directly from the IdP as a JWT.
6. REMS verifies the JWT signature using a public key provided by the IdP.
7. REMS sets a session cookie for the user, associates the user profile to that session
   (user only sees a random string in the cookie, the user's profile is stored in memory at the backend)
   and redirects the user to the front page.


# The decision: support OIDC and use cookies with lax same-site protections

Vast majority of identity providers support SAML and OIDC (or some own variant of Oauth). Sometimes SAML is offered in some enterprise options and OIDC is the free options. Also OIDC is newer and seems simpler to implement. Therefore we will transit to supporting OIDC only, using the authorization code flow (see https://tools.ietf.org/html/draft-ietf-oauth-security-topics-10#section-3 for more details about why implicit flow is considered dangerous).

This will also remove our dependency to Tomcat, which will make our deployment simpler in the future (we could, for example, use a built in jetty to deploy and run only JAR-files with only requirement being that the target system has working JRE installed).

## Problem with cookies and redirections

OIDC authentications consists of a pretty long string of redirects without user input. To protect against cross site attacks, browsers (at least Firefox and Chrome) drop all cookies that have `SameSite` property set to `strict` (which is the default with ring-defaults). This manifests as the browser ignoring the cookie set at step 7 above, requesting the front page without any cookies and REMS assigning a new, completely clean session for the user, effectively logging the user out.

One possible solution would be to send the user to the front page, instead of the special OIDC callback URL, and then inject additional logic into the front page handler to see when the user is in the middle of an OIDC authentication. While this makes the logic less modular, it also prevents us from ever redirecting based on what the user was doing (for example to same page, if we need to reauthenticate after expiry).

Second possibility would be to handle the last redirect via JavaScript, but it hasn't been tested and would make the logic again less modular (as front end then needs special cases).

Third possibility is simply not redirecting, and forcing the user to press a "continue" button manually, breaking the redirect chain.

Fourth, we could set a cookie set with `SameSite` to `lax`, that allows cross site use with GET requests that change the visible URL (i.e. not in iframes, images etc.). We then later switch the cookie to a strict one.

Last, and chosen option is to just use `SameSite=lax` all the time, and be careful that no GET operations can mutate data.

## Used libraries

There seem to be no suitable Clojure libraries REMS could use, though multiple claim to do OIDC. Unfortunately most seem to be PoCs or demo code (very few commits, no docs, old) and handle only implicit flow. Even the most advanced (which is not much) handle none of the more advanced cases (UserInfo endpoint, public key retrieval, single sign out) and are based on outdated Java libraries, or are build on frameworks we are not using (Duct, Integrant, friend).

OpenID lists several Java libraries that have official certification, but they all are related to doing the identidy provider's side of the protocol. Most up to date and best maintained Java library for clients seems to be Auth0's.

Even though in security contexts using well known and tested implementations is preferred, the remaining implementation work on top of the offered Java library is not very complex. Therefore REMS shall use our own solution based on the Auth0 Java library.

# Security considerations

HTTP GET operations must not be used for any mutable operations in the future. Before, the strict cookie policy protected against this, but in the future the lax policy will allow cross site GET requests to be done with the session that the user already has on REMS. Our CORS should protect against data leaks with cross site GET requests.

# Future considerations

## Single sign out

The base case for signing out is user wanting to end the REMS session, or to switch identities in REMS. This is trivial to do with a logout button that kill the REMS session cookie (ending the REMS session) and optionally sending a sign out request to the identity provider.

But more complex case is where the user wishes to sign out from all services that have active sessions with the single sign on identity. In this case the identity provider needs to send a request to callbacks of all clients that have active sessions. Not all IdPs support this, for example Auth0 has single sign out only for SAML. It is currently unknown if AAI requires or even supports this.

## Expiration

After logging in, REMS has full control of the session, so we can keep our sessions alive arbitarily long durations. Therefore there is no crucial need to have auto-saving functionality (that has been discussed before).

However, we might want to consider at least renewing tokens from the IdP now and then, in case the account has been disabled or some information has changed. This can often be done automatically on the background, so it doesn't degrade user experience.

## Automatic logging in

If the user has already logged in to the IdP and has given permissions to REMS, we could automatically log in users without requiring the user to initiate the login process. This can be done silently on the background so that any failures will not be visible to the user.

# How AAI OIDC data maps to Haka data

OpenID Connect scopes are way for the client to request certain blocks of information about the user. IdP will then inform the user what data is about to be released, which the user can approve or disapprove.

Scopes for Elixir AAI and the corresponding Haka Shibboleth attributes are:

- openid: sub (eduPersonUniqueId)
- profile: preferred_username (eduPersonPrincipalName), name (displayName)
- email: email
- bona_fide_status: bona fide research status
- groupNames: groupNames (eduPersonEntitlement)
- forwardedScopedAffiliations: forwardedScopedAffiliations
