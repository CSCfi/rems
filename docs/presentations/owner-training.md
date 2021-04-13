% REMS, the Resource Entitlement Management System
% CSC – IT Center for Science, Ltd.
% April 9, 2021

# REMS, the Resource Entitlment Management System

Resource Entitlement Management System (REMS) is an open source tool for managing access rights to different kinds of resources. REMS enables users to apply for access rights easily and offers a secure way for the data owners to manage access to their data.

The resources in REMS do not necessarily have to be in electronic data sets. It is possible to manage access to basically anything as long as it is identified by some form of an identifier. This includes for example research datasets and biological samples.

REMS is a service developed by CSC – IT Center for Science Ltd. and Nitor.

# Application process in brief

![application process](https://github.com/CSCfi/rems/blob/master/manual/img/application_process.png?raw=true)

# General terms

- A **Resource** is the technical identifier for the material or the data that needs have controlled access.

- A **License** defines the terms and conditions for the use of the _resource_.

- **Catalogue item**s are the _resources_ the applicant can apply for access to.

  - A catalogue item consists of a _resource_, a _license_, a _workflow_ and the application _form_.

- The **Catalogue** lists all the _catalogue items_ that users can apply for access rights.

# General terms (cont'ed)

- **Entitlement** means the access right. Entitlement is granted when the applicant has accepted the license terms and their _application_ is approved

- A **workflow** determines how the _application_’s approval process proceeds and who is in charge of the application.

- A **form** is the questionnaire the applicant has to fill in in order to request access to _resource(s)_.

- An **application** is a collection of _catalog item(s)_ with their associated _catalog item(s)_ an _applicant_ wants to request access to.

# Roles

- An **applicant** is a user who applies for an access right to a data resource by filling in the application form.

- A **member** is a user who has been added to an application by an applicant or a handler. Members are often part of the applicant’s research group.

- A **handler** is a user who processes the access rights applications. Generally, handlers decide whether the application should be approved or rejected. They can also request reviews and decisions from other users.

# Roles (cont'ed)

- **Reviewer** is a user who can review and comment on applications but cannot approve or reject them. Handlers invite reviewers to evaluate applications.

- **Decider** is a user who can approve or reject applications suggested by handlers. Decider role is used when a handler should not be able to approve or reject applications, for example in governmental office use.

- **Reporter** is a user who can only view all applications. Can be used for e.g. a supervisor who should only monitor the application process.

# Roles (cont'ed)

- An **owner** is a user who lists and manages their data resources in REMS. Owners can create organisations and assign organisation owners. Owners also assign handlers and reviewers to process the applications.

- **Organisation owner** is a user who can manage their own organisation’s resources.

# Role assignment

REMS assigns one or more roles to users dynamically. Only the _owner_ and _reporter_ roles are static and can only be set by a REMS system administrator.

- Roles are calculated per application, e.g. if Rebecca has been invited to review an application, once logged in to REMS she will have the _reviewer_ role for that single application but not for the other applications being processed in REMS.

Each user will always have an _applicant_ role, meaning that they can create and submit an applications.

# How to add your resources to REMS

![adding resources](https://github.com/CSCfi/rems/blob/master/manual/img/rems_owner.png?raw=true)

# Adding a resource

When you add resources to REMS, you have to create:

- an application **form**: applicants use this to apply for access rights;

- a **workflow**: define how applications will be processed;

- **licences**: set terms of use for your resources;

- a **resource**: a technical identifier for the dataset

and combine these items together by creating a **catalogue item**, which will then be listed in the catalog.

# Why is it so complicated

Having separate items increases flexibility and reduces manual work because after you have created the items once, you can easily reuse them and form new catalogue items by combining different items, e.g.

- you can list the same _resource_ with different _licenses_ in the catalog;

- you can have applications for the same _resource_ processed by different _handlers_;

- you can use different _forms_ for the same _resource_

or **any** combination of the above examples.

# Organizations

You have to connect each _catalog item_ you create to an **organization**. You can use the default organization for all the items, or you can create new ones according to your needs.

Each _organization_ can optionally have their own admins with an _organization owner_ role. Users with this role are able to manage items associated with their organization.

- This reduces the burden of the _owner_ by delegating the creation and maintenance of resources, licenses, forms, and workflows to the corresponding _organization owners_.
