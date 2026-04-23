# koalixcrm-fop-service

Java-based PDF rendering service for [koalixcrm](https://github.com/KoalixSwitzerland/koalixcrm).

Spring Boot 3 + Apache FOP. Consumes PDF export commands from SQS, fetches XSL-FO templates and entity data via the koalixcrm REST API, renders PDFs, and uploads the result to S3.

## Modules

- `pdf-export-service/` — the Spring Boot application (SQS listener, XML aggregation, FOP renderer, S3 uploader).
- `app_api_java/` — generated/maintained Java client for the koalixcrm REST API. Used by `pdf-export-service`.

## Build

```sh
./gradlew build
./gradlew :pdf-export-service:bootJar
```

## Container image

Built and published by `.github/workflows/build.yml` to GHCR:

```
ghcr.io/koalixswitzerland/koalixcrm_pdf_service:<tag>
```

(Image name kept as `koalixcrm_pdf_service` from the pre-extraction naming so existing deployments don't break. Only the repo name is `koalixcrm-fop-service`.)

Tags:
- `vX.Y.Z` and `vX.Y` on git tag `vX.Y.Z`
- branch name on branch push (`main`, `develop`)
- `sha-<short-sha>` on every push

Consumers (`koalixcrm`, `koalixcrm-system`) should pin a specific semver or sha tag, never `latest`.

## History

Extracted from `KoalixSwitzerland/koalixcrm` to separate the Java toolchain and QA pipeline from the Python/Django side. See koalixcrm issue #404 for context.
