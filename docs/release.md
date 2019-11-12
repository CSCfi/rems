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

## Environments

The integration test environment is updated by the developers.
CSC updates all supported environments such as https://rems2demo.csc.fi which is for public testing and customer specific instances.

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

   - Leave changes under `## Unreleased` empty

3. Create a pull request for those changes

4. After the branch is merged to master, fetch and checkout the new master

5. Create a new branch with a name indicating the version number of the
   new release, e.g.,

   `git checkout -b release-v2.6`

6. Tag the branch, e.g.,

   `git tag -a v2.6 -m "Release v2.6, \"Kalevalantie\""`

7. Push the tag,

   `git push origin v2.6`

8. Create jar and war packages for the release,

   `lein clean; lein uberjar; lein uberwar`

9. Go to the github page for the release, e.g.,

   https://github.com/CSCfi/rems/releases/tag/v2.6

10. From "Edit tag", change the name of the tag to the release name,
    e.g., "Kalevalantie".

11. Copy changes from CHANGELOG.md to "Description" field:

    - Change lines starting with `###` to `##` for better formatting

12. Upload rems.jar and rems.war from the directory target/uberjar/

13. Save changes
