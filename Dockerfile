# syntax=docker/dockerfile:1
# The whole app in one image: the Spring Boot jar (with the page built in) on Java 21. Built from
# the sources (no local build needed); see compose.yaml for running it with its database.
#
# No browser lives here any more: both platforms are driven over plain HTTP, and the one sign-in
# that needs a browser is done by the band in its own. So nothing in this image runs Chromium
# unsandboxed beside the bands' sealed secrets, and the image is a JRE and a jar.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY features features
COPY platforms platforms
COPY web web
COPY app app
# the Maven repository and npm's cache survive between builds
RUN --mount=type=cache,target=/root/.m2 --mount=type=cache,target=/root/.npm \
    ./mvnw -B -q -DskipTests -pl app -am package \
 && cp app/target/gigsynchub-app-*.jar /src/app.jar \
 && java -Djarmode=tools -jar /src/app.jar extract --layers --destination /out

FROM eclipse-temurin:21-jre
ENV LANG=C.UTF-8 \
    TZ=Europe/Bratislava
# curl for the health check below; the rest is certificates and zones the app needs itself
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl tzdata \
 && rm -rf /var/lib/apt/lists/*

# not root: the app and its data belong to this user. The ids match the host user who owns ./data
# and ./secrets (bind mounts keep the host's owners).
ARG UID=1000
ARG GID=1000
# The ids are the host's and not negotiable, but the base image may already have someone at them —
# Ubuntu 26.04, which Temurin now builds on, ships a user "ubuntu" at 1000:1000 — so whoever holds
# them gives way first. Named by id, not by name: the next base image may call its own user anything.
RUN taken="$(getent passwd "$UID" | cut -d: -f1)"; [ -z "$taken" ] || userdel -r "$taken" 2>/dev/null || userdel "$taken" \
 ; taken="$(getent group "$GID" | cut -d: -f1)"; [ -z "$taken" ] || groupdel "$taken" 2>/dev/null || true \
 ; groupadd -g "$GID" gigsynchub \
 && useradd -u "$UID" -g "$GID" -m -d /home/gigsynchub -s /usr/sbin/nologin gigsynchub \
 && mkdir -p /app/data \
 && chown -R gigsynchub:gigsynchub /app /home/gigsynchub

WORKDIR /app
# layers from the least to the most often changed, so a code change ships only the last one
COPY --from=build --chown=gigsynchub:gigsynchub /out/dependencies/ ./
COPY --from=build --chown=gigsynchub:gigsynchub /out/spring-boot-loader/ ./
COPY --from=build --chown=gigsynchub:gigsynchub /out/snapshot-dependencies/ ./
COPY --from=build --chown=gigsynchub:gigsynchub /out/application/ ./

USER gigsynchub
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8081/actuator/health > /dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
