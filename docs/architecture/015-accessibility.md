# 015: Accessibility

Authors: @opqdonut

This ADR aims to document the current status of Accessibility features
in REMS, and the guidelines with which we hope to keep REMS
accessible.

## Background

REMS is aiming for
[Web Content Accessibility Guidelines 2.1 Level AA](https://www.w3.org/TR/WCAG21/)
accessibility. There has as of the writing of this document
(2020-12-14) been no formal evaluation of whether REMS fulfils the AA
requirements.

## Automated testing

We use [axe](https://www.deque.com/axe/) for automated accessibility
tests. This should verify at least

- contrast requirements (but only for the default theme)
- correct use of ARIA attributes

## Manual testing

How accessible a site truly is can only be tested manually. Even if we
tick all the technical boxes the site might not make any sense. We've
had regular check ups of REMS using screen reader software (NVDA), but
haven't done those in a while (as of 2020-12-14).

## Use cases

We aim for high accessibility in the applicants' work flow. Handler &
administrator interfaces can be more complex and require more
knowledge to use.

## Focus

There are many guidelines in WCAG2.0 that refer to focus & keyboard navigation, for example:

- [2.1.1 Keyboard](https://www.w3.org/WAI/WCAG21/quickref/#keyboard)
- [2.4.3 Focus Order](https://www.w3.org/WAI/WCAG21/quickref/#qr-navigation-mechanisms-focus-order)
- [2.4.7 Focus Visible](https://www.w3.org/WAI/WCAG21/quickref/#focus-visible)
- [3.2.1 On Focus](https://www.w3.org/WAI/WCAG21/quickref/#on-focus)

Here are the REMS principles for handling keyboard navigation & focus:

- Focus H1 element on page change (but see [#2492](https://github.com/CSCfi/rems/issues/2492))
- Make sure TAB on keyboard focuses first input element
- We have custom focus indicators where browser defaults are not enough (but see [#2493](https://github.com/CSCfi/rems/issues/2493))

### Mac OS specific settings

It has been discovered that in order to properly test/use accessibility features, such as  `:focus` outline, on Mac OS Catalina, you should have Accessibility settings explicitly enabled in your OS settings. Please, refer to these resources for instructions:
  - [No, tabbing is not broken. Yes, I was confused too.](https://www.scottohara.me/blog/2014/10/03/link-tabbing-firefox-osx.html)
  - [Browser Keyboard Navigation in macOS](https://www.a11yproject.com/posts/2017-12-29-macos-browser-keyboard-navigation/)

## Screen readers

The most important things for a nice screen reader experience are:

- we offer a skip to main content link
- we try to order the DOM in an order that makes sense
  (e.g. the Actions menu comes after the other application information)
