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

3. **Per-`ResourceSet` registration** — `ModelSerializationService` obtains each
   `ResourceSet` from a `ModelResourceSetFactory`. The default factory
   (`DefaultModelResourceSetFactory`) registers all `EPackage` beans into the
   `ResourceSet`'s local package registry (not the global
   `EPackage.Registry.INSTANCE`). A fresh `ResourceSet` per call keeps the service
   thread-safe and avoids JVM-wide mutable state, so tests stay isolated.

   The factory is the single seam for model loading: supply an alternative
   `ModelResourceSetFactory` bean (e.g. one backed by an `UpgradingResourceSet`
   that loads and auto-upgrades older model versions) and it overrides the default
   (`@ConditionalOnMissingBean`) with no change to any service.

Once registered, `deserialize(...)` returns the generated types (e.g.
`TimingRegatta`) and EMF Compare produces feature-accurate diffs.

## Ingest validation

`TdiValidationService` validates submitted models before they are stored. It is
controlled by two properties:

**Fail-fast policy.** A model that the generated TDI classes cannot load is
unusable elsewhere in the timing system, so it is **rejected at ingest** rather
than stored. Every accepted document is therefore guaranteed to load into the
generated classes.

| Property | Default | Behavior |
|---|---|---|
| `rrr.tdi.validation.enabled` | `true` | When `false`, validation is skipped entirely (fully opaque; no load guarantee). |
| `rrr.tdi.validation.strict` | `false` | When `true`, additionally reject models that load but have EMF `ERROR`-level problems (HTTP 400). |

With validation enabled (default), a payload is rejected with HTTP 400 unless it:

1. loads into the generated classes (a failed typed load — bad XML, or a value
   the generated datatype converters reject — is rejected), **and**
2. is in the TDI namespace (`http://www.rowtown.org/TDI/1.0.0`).

Once loaded, EMF `Diagnostician` runs. Structural problems (e.g. a start list
omitting result-only required features such as `TimingStation.position`) are
logged but not rejected in the default mode; `strict=true` rejects them too.

Rejections are raised as `IllegalArgumentException`, which
`GlobalExceptionHandler` maps to HTTP 400.

> **Loadability depends on the generated model.** The generated datatype
> converters must be able to parse every value your producers emit. If a
> converter is unimplemented or stricter than the data (observed: the generated
> `LocalDate`/`LocalTime` converters fall through to `EFactoryImpl` and reject
> ISO values such as `2024-05-17`), EMF aborts the whole load and the document is
> rejected. Fix such converters in the `tdi-model` project (regenerate with
> `@generated NOT` conversion methods), otherwise valid real-world documents
> cannot be ingested.

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

## Loading older versions & upgrade-and-persist

Loading of older stored versions is delegated to the `ModelResourceSetFactory`
seam (see above): supplying an upgrading `ResourceSet` lets `ModelSerializationService`
load-and-upgrade any stored version to the current one, transparently to
validation, comparison, and read APIs.

Because a multi-stage upgrade copies the model and would be re-paid on every
load, `ModelUpgradePersistenceService` writes the upgraded form back so the cost
is paid once:

- **Detection** — a document needs upgrade when its stored `model_ns_uri` is not
  the current namespace for its type (`CurrentModelNamespaces` is the single
  source of truth, shared with validation).
- **Lazy trigger** — read paths (`getDocument`) call `requestUpgradeAsync`, which
  upgrades off the hot path in a background executor, deduplicated per document.
  The read itself is unaffected.
- **Batch trigger** — `POST /api/v1/admin/models/upgrade?type=…` (admin only)
  drains all older-version documents of a type, e.g. after publishing a new model.
- **Persistence** — the upgraded bytes are appended as a **new, system-authored
  version** ("Auto-upgrade `from` → `to`"); the original version is retained for
  audit, `model_ns_uri` is advanced to current, and provenance
  (`upgradedFrom`/`upgradedTo`/`upgradedAt`) is recorded in metadata.
- **Safety** — each upgrade runs in its own transaction, re-checks the version
  under the transaction (idempotent), and never persists a failed/partial upgrade.

## Client considerations

- The **Java client** (`race-timer-client`) may also depend on `tdi-model` to
  build/validate models before upload.
- The **web client** (`race-timer-client-web`) cannot use Java classes; it
  extracts `raceId` via regex. Generated classes are therefore a
  server-and-Java-client concern — never make them the only way to interact with
  the API.
