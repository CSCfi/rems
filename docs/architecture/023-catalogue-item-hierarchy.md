# 023 Catalogue item hierarchy

Authors: @pikariop

## Background

See issue https://github.com/CSCfi/rems/issues/3412 

## Considerations

- For the least amount of surprise to the applicant and organization owners, the hierarchy should
  - consist of two levels, a top-level-item and it's complementary items
  - not allow a circular dependency
  - not allow a complementary item to be referenced by many top-level items

- The catalogue items that can be assigned as complementary items to another should 
  - already exist
  - not be archived
  - not already have their own complementary items or another top-level item
  - be writable by the organization owner
  - have the same workflow, to follow the same logic as bundling items in the shopping cart
  
This would be straightforward to implement as a new table in the database with a foreign key to the top-level catalogue item and another for the complementary item, and using constraints to maintain the aforementioned restrictions. However, a similar concept already exists as the catalogue tree and a considerable amount of prior work can be reused for this feature as well.

Reference table
* + simple
* + database is designed to deal with referential integrity and the means to define relationships and constraints come out of the box
* - requires a migration whether the REMS instance uses the feature or not
* - requires migrations if the constraints change

JSON blob, parent record has array of children
* + can reuse existing data structure (`catalogueitemdata`)
* + can reuse existing code to deal with referential integrity and relationships (dependency graph)
* + no migration required if constraints or data fields change
* - can become cumbersome to pass back and forth in the API with a large amount of data 

## Decision

Implement catalogue item hierarchy by recording complementary items under the `catalogueitemdata` JSON blob in similar way to categories that constitute the catalogue tree.

