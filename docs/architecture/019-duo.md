# 019: DUO - Data Use Ontology

Authors: @Macroz @aatkin

## Situation

Data use restrictions have been standardized in [DUO](https://github.com/EBISPOT/DUO).

REMS not only has a catalogue of data, but the whole application process. DUO standard is intended for automatic processing of data restrictions, so it is natural that REMS could support it. 

There has been worldwide interest on adopting it, along with other standards such as [GA4GH](https://github.com/CSCfi/rems/blob/duo/adr-019/docs/ga4gh-visas.md). For example, [ELIXIR](https://elixir-europe.org/) project, [EGA](https://ega-archive.org/), and other biobanks, some of which use REMS already.

Our implementation so far is experimental in the sense that there is no current production user. We are open for implementing any additional features the very first intentional user requires, or even changing our approach. What we have implemented so far is our first idea, of how DUO fits into REMS, but certainly has a lot of potential for extension.

## Solution

To enable semantical tagging of datasets with DUO codes, we have implemented the following:

- Internal tools to fetch new DUO and Mondo codes as part of new REMS releases,
- New API to allow querying DUO and Mondo codes,
- Updated application and resource API to support DUO codes,
- Added user interface for tagging datasets with DUO and Mondo codes,
- Updated application user interface for applicant to allow entering their DUO codes, and
- Updated application user interface to display both applicant DUO codes and dataset DUO codes to the handler.

DUO codes are updated on an infrequent basis with releases being created in [DUO repository](https://github.com/EBISPOT/DUO/releases). Mondo codes are updated more frequently, which can also be found in [Mondo repository](https://github.com/monarch-initiative/mondo/releases). Because the codes can change quite a lot, a decision was made to make the update process manual to give control over when new codes are rolled into production, and if any new features need to be implemented. Currently both DUO and Mondo codes are committed to REMS main branch, and subsequently built into a release.

To support dataset tagging with DUO codes, we implement a new field in resource entity, `:resource/duo` which contains the field `:duo/codes`. DUO code includes `:id` and optional `:restrictions`, which model the possible extra questions required by the restricting DUO code. These can include questions such as:
- DUO:0000007 "This data use permission indicates that use is allowed provided it is related to the specified disease," and
- DUO:0000024 "This data use modifier indicates that requestor agrees not to publish results of studies until a specific date."

We will implement a new field in application entity `:application/duo` which will contain three new fields:
- `:duo/codes` contains DUO codes entered by the applicant,
- `:duo/matches` contains results of automated DUO code check against resource DUO codes, and
- `:duo/valid?` contains a single boolean/keyword to indicate the overall validness of applicant DUO codes.

Applicant DUO codes are automatically checked against resource DUO codes by REMS. DUO code can have one of three validity values: `true`, `false` and `:duo/needs-validation`, which means that DUO code should be manually checked by application handler. Validity value is associated in `:duo/valid?` field in resource and application entities.

The algorithm for checking DUO codes is roughly the following:
1. Look through all resource DUO codes in application, for each find a match by `:id` from applicant DUO codes
2. If `:id` match is not found, associate `:duo/valid?` with false on the resource DUO code
3. Else:
   1. (A) If resource DUO code contains restriction that has special check, associate `:duo/valid?` on the DUO code based on the special check
   2. (B) If resource DUO code contains non-special restriction, associate `:duo/valid?` with the keyword `:duo/needs-validation`
   3. Else, associate `:duo/valid?` with true.

Mondo codes are used to determine validity of DUO:0000007 (DS) code. One or more Mondo codes can be added as restriction. Mondo codes have a hierarchical relation, which is modeled with a directed acyclic graph (DAG). When DUO code validity is checked on DUO:0000007 (DS) code, the following algorithm is used:
1. Look through all Mondo codes in the resource's DUO:0000007 (DS) code restriction, for each find a match by `:id` in applicant DUO:0000007 (DS) restriction
2. If exact match is found, return `true`
3. Else, look at all hierarchical parents (ancestors) of the Mondo code in applicant's DUO code restriction. If the Mondo code in resource's DUO code restriction is one of these ancestors, return `true`
4. Else, return `false`.

## Unfinished details

- Display of DUO in the catalogue, something like in EGA. More-info can be used already. It is thought that few instances will use the REMS catalogue, and prefer something of their own instead.
- Updating DUO / Mondo codes and what it means to existing resources and applications
