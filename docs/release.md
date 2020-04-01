# Releases and releasing

## Naming
Releases are named by streets starting from CSC headquarters in Keilaniemi.

I.e. Keilaranta (v2.0), Otaniementie (v2.1), Vuorimiehentie (v2.2.), Tekniikantie (v2.3) to Tietotie (v2.4).
The destination to aim for is up to you.

See [Release page](https://github.com/CSCfi/rems/releases).

## Release notes

Release notes can be found in the [changelog](../CHANGELOG.md). The
changelog is updated by developers via the normal pull request process.

Releases can be found on the github [release page](https://github.com/CSCfi/rems/releases).

## Step-by-step instructions for creating a release

1. Checkout the most recent master, e.g.,

   `git fetch; git checkout origin/master`

2. Edit CHANGELOG.md:

   - See the name of the previous release, find the street with that name,
     select a street that is next to that street but is not yet used as
     a release name.

   - Add a line for the new release, e.g.,
     `## v2.6 "Kalevalantie" 2018-11-12`

   - Move all changes from under `## Unreleased` to under the new release

   - Leave `Changes since v[x.y]` text under `## Unreleased`

3. Create a pull request for those changes

4. After the branch is merged to master, fetch and checkout the new master

5. Tag the branch, e.g.,

   `git tag -a v2.6 -m "Release v2.6, \"Kalevalantie\""`

6. Push the tag,

   `git push origin v2.6`

7. Get the `rems.jar` and `rems.war` packages for the release from CircleCI.
   You can do this manually or using `rems-deploy/scripts/fetch_from_circle.sh`

8. Go to the github page for the release, e.g.,

   https://github.com/CSCfi/rems/releases/tag/v2.6

9. From "Edit tag", change the name of the tag to the release title,
   e.g., "Kalevalantie".

10. Copy changes under the new release from CHANGELOG.md to "Description"
    field:

    - Change lines starting with `###` to `##` for better formatting

11. Press "Attach binaries" to upload the `rems.jar` and `rems.war` you got in step 8.

12. Press "Publish release".
