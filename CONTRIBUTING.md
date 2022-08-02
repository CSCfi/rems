# How to contribute

REMS is an open source project and we are open to issues and pull requests from everyone. Before starting on working something big/non-trivial, please submit an issue first, we can help with the design and provide assistance!

Instructions for getting started are in [docs/development.md](docs/development.md). You will also find architecture documentation and more guides in [docs directory](docs/).

You can try things against our [demo instance](https://rems-demo.rahtiapp.fi/).

See [Good first issue](https://github.com/CSCfi/rems/labels/Good%20First%20Issue) and [Help wanted](https://github.com/CSCfi/rems/labels/Help%20Wanted).

Don't forget [Hacktoberfest](https://hacktoberfest.digitalocean.com/), we tried to make sure [issues with the Hacktoberfest label](https://github.com/CSCfi/rems/labels/Hacktoberfest) have been groomed, but please ask by e.g. commenting the issues!

## Submitting a pull request

To work on an issue do the following:

- Fork the repository in Github
- Clone your repository:
```
git clone git@github.com:your-username/rems.git
```
- Make sure that tests pass by running `lein alltests`
- Submit a pull request against the main repository
- Fill in the info you think is relevant for the PR (screenshots, tests, ...)
- Wait for the CI build to finish and fix the findings
- One of the core team members will review it
- React to questions and comments in the PR
- Let's get it merged and celebrate!

Please, follow [CSC Code of Conduct](https://www.csc.fi/en/values) when participating. If you notice any violations or have some issue to raise, please [contact the team](mailto:rems@csc.fi).
