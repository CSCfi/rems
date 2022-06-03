[![CircleCI](https://circleci.com/gh/CSCfi/rems.svg?style=svg)](https://circleci.com/gh/CSCfi/rems)

# Resource Entitlement Management System

Resource Entitlement Management System (REMS) is a tool for managing access rights to resources, such as research datasets.

Applicants can use their federated user IDs to log in to REMS, fill in the data access application and agree to the dataset's terms of use. The REMS system then circulates the application to the resource owner or designated representative for approval. REMS also produces the necessary reports on the applications and the granted data access rights.

REMS is a Clojure+ClojureScript Single Page App.

REMS is developed by a [team](mailto:rems@csc.fi) at CSC – IT Center for Science.

You can try out REMS using the publicly available demo instance at <https://rems-demo.rahtiapp.fi>.

## Getting started

- You can [run the application with Docker](docs/development.md#Running-the-application-with-Docker)
- or read the full [development documentation](docs/development.md)
- or proceed to the [installation instructions](docs/installing-upgrading.md).

## Releases

Currently, the REMS project aims to make small, frequent releases.
Releases are compatible with old data, using migrations where needed. Since REMS is undergoing active
development, we can't guarantee backwards compatibility for the API.
However, all breaking changes are highlighted in the release notes.

See the [release page](https://github.com/CSCfi/rems/releases) for the releases.

## Contributing

- REMS is an open source project. We use the [MIT License](./LICENSE).
- We welcome [issues through GitHub](https://github.com/CSCfi/rems/issues).
- You can [contact the team on the discussion board](https://github.com/CSCfi/rems/discussions) or [via email](mailto:rems@csc.fi).
- In case you would like to contribute to the development, perhaps you participate in Hacktoberfest, then please refer to the [contributing document](CONTRIBUTING.md). We'll guide you through to get your pull request reviewed and merged.
- You can follow the progress of the project on the [GitHub project board](https://github.com/CSCfi/rems/projects/1).

## Documentation

The REMS API is documented using
[OpenAPI aka Swagger](https://swagger.io/docs/specification/about/).
You can check out the API docs using
[the swagger-ui of the public demo instance](https://rems-demo.rahtiapp.fi/swagger-ui).

Documentation can be found under the [docs](./docs) folder.
