# 016: Database and Time

Authors: @opqdonut

## Problem

Integration tests were failing on macOS due to different clocks being
used inside the JVM and inside the test database running in a docker
container.

This clock difference causes problems like this:
- A catalogue item gets created, and the database current time is used as the `start`
- The catalogue item is fetched, and the `start` is compared with the JVM time
- The `start` is in the future for the JVM, so the catalogue item is disabled
- A test, expecting to use a catalogue item it just created, fails

In production there were no problems like this (that we know of)
probably due to network & human latencies being greater than the clock
drift.

This was already noticed back in
[PR #1430](https://github.com/CSCfi/rems/pull/1430)
where a workaround was added.

## Solution

Always use the JVM clock. This was accomplished by removing the
`now()` defaults from timestamp columns. See
- [Issue #2540](https://github.com/CSCfi/rems/issues/2540)
- [PR #2543](https://github.com/CSCfi/rems/pull/2543)
- [PR #2544](https://github.com/CSCfi/rems/pull/2544)
