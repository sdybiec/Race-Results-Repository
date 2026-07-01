# API Documentation

This directory holds the exported static OpenAPI specification for the
Race Results Repository API.

## Generating the spec

The spec is produced by the `openapi` Maven profile, which boots the application
with the `dev` Spring profile (in-memory H2 database + embedded MQTT broker — no
external services required), fetches the live `/api-docs` endpoint, and writes it
to `openapi.json` in this directory:

```bash
mvn verify -Popenapi
```

After the build completes, the generated spec is available at:

```
docs/api/openapi.json
```

## Viewing the spec

- **Swagger UI (live, app running):** http://localhost:8080/swagger-ui.html
- **Raw JSON (live, app running):** http://localhost:8080/api-docs
- **Static file:** [`openapi.json`](./openapi.json)

## Using the static spec

The exported `openapi.json` can be used to:

- Generate client SDKs with [OpenAPI Generator](https://openapi-generator.tech/):
  ```bash
  openapi-generator-cli generate -i docs/api/openapi.json -g java -o ./generated-client
  ```
- Import into API tooling such as Postman, Insomnia, or Stoplight.
- Publish to an API portal or developer documentation site.
- Run contract/lint checks in CI (e.g. with `spectral lint`).
