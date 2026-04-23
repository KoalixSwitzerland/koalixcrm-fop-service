# Production image for the koalixcrm FOP (PDF export) service.
# Multi-stage: Gradle build -> minimal JRE runtime.
FROM gradle:8.10-jdk21-alpine AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts ./
COPY app_api_java app_api_java
COPY pdf-export-service pdf-export-service
RUN --mount=type=cache,target=/root/.gradle \
    gradle :pdf-export-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine AS runtime

ARG APP_VERSION=vX.Y.Z-develop
ARG VCS_REF=unknown

LABEL org.opencontainers.image.title="koalixcrm-fop-service" \
      org.opencontainers.image.version=${APP_VERSION} \
      org.opencontainers.image.revision=${VCS_REF} \
      org.opencontainers.image.source="https://github.com/KoalixSwitzerland/koalixcrm-fop-service"

RUN addgroup -S pdf && adduser -S pdf -G pdf
WORKDIR /app
COPY --from=build /src/pdf-export-service/build/libs/*.jar /app/pdf-export-service.jar
RUN chown -R pdf:pdf /app
USER pdf
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75" \
    KOALIXCRM_VERSION=${APP_VERSION}
EXPOSE 5005
CMD ["java", "-jar", "/app/pdf-export-service.jar"]
