# Search

## Application Search

The application listings have a search which uses the [Lucene query parser syntax][query-syntax]
and searches also the contents of the applications. The search tems are joined
with the "OR" operation, unless the query specifies otherwise.

By default the search looks into all fields, but the using the `fieldname:`
syntax it's possible to build more specific queries. Wildcards `?` and `*` are
supported, plus many more. See the [query syntax documentation][query-syntax]
for more details.

[query-syntax]: https://lucene.apache.org/core/8_2_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description

### Examples

> Note: Some special characters such as `/` and `@` require the search
> term to be enclosed in quotes even when it's just a single word.

`platypus` - find an exact word

`platy*`, `plat?pus` - find using part of a word

`+egg +bacon -spam`, `egg AND bacon NOT spam` - find items with egg and bacon, but no spam

`id:42`, `id:"2019/12"` - find by application ID

`applicant:alice` - find by applicant's user ID

`applicant:"Alice Liddell"` - find by applicant's full name

`applicant:"alice@example.com"` - find by applicant's email

`member:lewis` - find by member's user ID

`member:"Lewis Carroll"` - find by member's full name

`member:"lewis@example.com"` - find by member's email

`title:platypus` - find by application title

`resource:platypus` - find by resource/catalogue item name

`state:approved` - find by application state

`form:platypus` - find by application form content
  

## Other Searches

The catalogue page and admin pages use a simpler search mechanism which
only does a substring search of the values shown in the table.
The search tems are joined with the "AND" operation.