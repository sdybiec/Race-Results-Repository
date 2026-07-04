# RML (Regatta Definition) Document Type

The repository manages a third document type, **RML** (Regatta Modeling
Language) — the definition of a regatta edition (venue, events, categories,
schedule, dates). It joins the existing `START_LIST` and `RACE_RESULTS` types.

## Cardinality & key

One RML document per **regatta edition**, keyed by `regattaId` (regatta name) +
`regattaStartDate` — the same edition key as the start list.

**The key is derived from the model and is authoritative.** The RML root
`Regatta` element carries `name` and `startDate`; the server reads them
(`EmfDocumentInspector`, StAX) and uses them as `regattaId` / `regattaStartDate`.
If the client also supplies these on the request, they must match the model or
the request is rejected (HTTP 400). This mirrors how `raceId` is derived from the
TDI model for race results.

> `Regatta.startDate` is a custom EMF datatype; the server parses its serialized
> value as ISO-8601 (`yyyy-MM-dd`). If your generated model serializes it
> differently, adjust `DocumentManagerService.parseRegattaStartDate`.

## Model wiring

RML is a generated EMF model (`rml-model` artifact) that **depends on the
ReportDesign model**, so both `EPackage`s must be registered for an RML document
to load into the typed classes. `RmlModelConfig` is the single place that
references the generated classes (register both `RegattaPackage` and
`ReportdesignerPackage`); `ModelSerializationService` registers all model
`EPackage` beans per-`ResourceSet`.

RML instance documents are **self-contained** (a single XMI resource, no
external `href`s), so they load and store like TDI documents. ReportDesign is a
*dependency* of RML, not its own managed document type.

## Validation (per type)

`ModelValidationService` enforces the fail-fast policy *and* that each payload is
in the namespace expected for its declared type:

| Document type | Expected namespace |
|---|---|
| `START_LIST`, `RACE_RESULTS` | `http://www.rowtown.org/TDI/1.0.0` |
| `RML` | `http://www.rowtown.org/RML/1.4.0` |

A model that cannot be loaded by the generated classes, or whose namespace does
not match its declared type (e.g. a TDI model submitted as `RML`), is rejected
with HTTP 400. Structural (`Diagnostician`) errors are logged but not rejected
unless `rrr.tdi.validation.strict=true`.

## Model namespace metadata

Every document now records the `nsURI` of the metamodel its model data conforms
to, in the `model_ns_uri` column (`DocumentResponse.modelNsUri`). It is derived
from the stored model (root element namespace), making each document
self-describing and carrying the model version for future migration decisions.

## Authorization

RML is client-produced and client-consumed (the Java timer client) and also
manageable by regatta admins. The `V2` migration seeds ACL entries granting
`RML` CRUD/rollback/subscribe to `REGATTA_ADMIN` and `TIMER`, and read/subscribe
to `VIEWER`.

## Clients

Only the **Java client** supports RML CRUD + notifications; it derives the
regatta key from the RML model it produces/consumes. The TypeScript client does
not handle RML (its `DocumentType` only needs to tolerate the new value).

## Out of scope (deferred)

- **Recurring Regatta** — a series grouping above regatta editions (for future
  authorization). Deferred; `regattaId` is the implicit series for now.
- Managing ReportDesign as its own document type.
