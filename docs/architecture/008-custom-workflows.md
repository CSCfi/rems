# 008: Custom workflows

Authors: @luontola


## Background

The original idea with workflows was that the handler has the power to do anything, and this would be enough to fit the needs of various organizations which have their own ways of working. But it was found out that for bureaucratic reasons some organizations want to restrict what the handler can do.

Example: When an application comes in, there is one person who does the back-and-forth with the applicant until the application is ready, after which the application will be forwarded to an official who will either approve or reject the application. In this case the handler cannot approve/reject the application. Also applications are never closed, but there is always an official decision.

Thus arose the need to have custom workflows.


## Prototypes

The `calculate-permissions` function (since renamed to `application-permissions-view`) is in control of mapping users and roles to allowed commands, and by modifying those allowed commands we can produce different kinds of workflows.

### POC 1: Multiple distinct workflows

The first idea was to have multiple `calculate-permissions` functions, one for each workflow. This is the most flexible solution, but it results in quite much duplication, and the duplication scales linearly over the number of commands.

The prototype is at #1838 (7392154f06c5304b208b57322558ef97c3699437).

### POC 2: Master workflow + command whitelist/blacklist

The second idea was to have one master workflow which contains all the commands, but then the commands are whitelisted/blacklisted to derive restricted workflows for each organization's needs. This approach may require creating variations of some commands, so that the organizations can choose the variation which fits them.

The prototype is at #1842 (ae8533ba16f4dd74778fc0a1f07180434fde796a).

> In this POC there is a command variation, `request-final-decision`, but it can be avoided by granting `approve`, `reject` and `decide` permissions to `request-decision`'s `decider` role and then filtering out the unwanted permissions.


## Decision

Let's proceed with the *master workflow + command whitelist* approach. It seems to produce less duplication than the other approach and should be flexible enough - we can create command variations for the hard cases. We choose to use a whitelist instead of a blacklist, because even though the blacklist would be shorter to write, it runs the risk of exposing unwanted commands as new commands are added.
