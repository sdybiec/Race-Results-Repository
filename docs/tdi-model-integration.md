# TDI Model Integration

The Race Results Repository stores EMF Timing Data Interchange (TDI) models. This
document describes how the **generated** TDI model classes (the `tdi-model`
artifact) are wired in, why the storage layer stays byte-oriented, and the
version-skew / migration policy you must follow when the model evolves.

## Design principle: opaque storage, typed semantics

The persistence and versioning path treats `modelData` as **opaque bytes**. This
keeps the store schema-agnostic and forward-compatible: a new model revision does
not require a server change to be *stored*.

Typed access via the generated classes is applied **only where semantics are
needed**:

| Concern | Mechanism | Uses generated classes? |
|---|---|---|
| Store / version documents | `byte[]` snapshots | No |
| Derive `raceId` on ingest | `TdiModelInspector` (StAX, streaming) | **No — deliberate** |
| Deserialize / serialize | `ModelSerializationService` | Yes (typed when package registered) |
| Semantic diff between versions | `ModelComparisonService` (EMF Compare) | Yes |
| Validate on ingest | `TdiValidationService` | Yes |

`TdiModelInspector` intentionally stays on StAX: pulling a single `raceId`
attribute needs no package registration and no schema-version match, so it is
strictly more robust than typed access for that one job.

## How the generated model is wired

1. **Dependency** — `pom.xml` declares the generated artifact via the
   `tdi.model.groupId` / `tdi.model.artifactId` / `tdi.model.version` properties.
   Install it locally with `mvn install` from the Timing-Data-Interchange project,
   or publish it to your registry.

2. **Single reference point** — `config/TdiModelConfig` is the *only* class that
   imports the generated model. It exposes the generated `EPackage` as a Spring
   bean by referencing `…Package.eINSTANCE` (which triggers registration of the
   generated factory and implementation classes). If the artifact coordinates or
   generated package/class names differ, change **only** `TdiModelConfig` and the
   `pom.xml` properties.

3. **Per-`ResourceSet` registration** — `ModelSerializationService` registers all
   `EPackage` beans into each `ResourceSet`'s local package registry (not the
   global `EPackage.Registry.INSTANCE`). A fresh `ResourceSet` per call keeps the
   service thread-safe and avoids JVM-wide mutable state, so tests stay isolated.

Once registered, `deserialize(...)` returns the generated types (e.g.
`TimingRegatta`) and EMF Compare produces feature-accurate diffs.

## Ingest validation

`TdiValidationService` validates submitted models before they are stored. It is
controlled by two properties:

| Property | Default | Behavior |
|---|---|---|
| `rrr.tdi.validation.enabled` | `true` | When `false`, validation is skipped entirely (fully opaque). |
| `rrr.tdi.validation.strict` | `false` | When `true`, require a clean typed load and reject models with EMF `ERROR`-level problems (HTTP 400). |

**Default is lenient.** A payload is accepted if it *either*:

1. loads cleanly into the generated classes (then EMF `Diagnostician` runs;
   structural problems are logged, not rejected), **or**
2. is a well-formed XML document whose root is in the TDI namespace
   (`http://www.rowtown.org/TDI/1.0.0`), checked with StAX via
   `TdiModelInspector` — no dependency on the generated datatype converters.

Only payloads that are neither (not well-formed XML, or a foreign namespace) are
rejected with HTTP 400.

This two-tier check is deliberate. Real documents can be valid TDI yet fail a
*typed* load for two independent reasons:

- **Omitted fields** — a start list legitimately omits result-only required
  features (e.g. `TimingStation.position`, `TimingStation.progressDistance`).
- **Datatype/version skew** — the generated model's datatype converters can
  reject otherwise-valid values (observed: the generated `LocalDate` converter
  rejecting an ISO date such as `2024-05-17`). EMF aborts the whole load in that
  case.

Because storage is byte-opaque, such documents must still be accepted. Strict
mode is available for pipelines whose producers emit fully-populated,
cleanly-loadable models.

Rejections are raised as `IllegalArgumentException`, which
`GlobalExceptionHandler` maps to HTTP 400.

> Generated *custom* invariants (from a generated `…Validator`) do not run in a
> standalone (non-OSGi) runtime unless the validator is registered in
> `EValidator.Registry.INSTANCE`. Only the base structural checks (multiplicity,
> bounds, enum/data-type conformance) run by default. Register the generated
> validator in `TdiModelConfig` if you need the custom constraints.

## Version-skew and migration policy

Documents are stored as bytes and versioned **forever**. Typed access depends on
the document's `nsURI` matching a registered generated package.

- **Namespace stability** — Keep `nsURI` (`…/TDI/1.0.0`) stable across
  *compatible* model changes so historical documents keep loading as typed
  objects. The version segment should change only on a breaking revision.
- **On a breaking revision** — Choose one of:
  1. Register **multiple** generated package versions (bean per `nsURI`) so old
     and new documents both load; or
  2. Add a migration step that re-serializes old documents to the new `nsURI`; or
  3. Rely on the opaque-byte guarantee for storage and only require the current
     package for *new* typed operations (diff/validation) — historical documents
     remain retrievable as bytes regardless.
- **The store never breaks** — Because persistence is byte-oriented, a package
  mismatch only affects typed operations (deserialize/diff/validate), never the
  ability to store or return a document's bytes.

## Client considerations

- The **Java client** (`race-timer-client`) may also depend on `tdi-model` to
  build/validate models before upload.
- The **web client** (`race-timer-client-web`) cannot use Java classes; it
  extracts `raceId` via regex. Generated classes are therefore a
  server-and-Java-client concern — never make them the only way to interact with
  the API.
