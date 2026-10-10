# syntax=docker/dockerfile:1
# The whole app in one image: the Spring Boot jar (with the page built in) on Java 21, plus
# Chromium + chromedriver for the platforms' Selenium. Built from the sources (no local build
# needed); see compose.yaml for running it with its database.

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
 && cp app/target/bzscraper-app-*.jar /src/app.jar \
 && java -Djarmode=tools -jar /src/app.jar extract --layers --destination /out

FROM eclipse-temurin:21-jre AS jre

# Debian: Chromium and its matching chromedriver as packages (Ubuntu's are snaps)
FROM debian:trixie-slim
ENV JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:$PATH \
    LANG=C.UTF-8 \
    TZ=Europe/Bratislava
COPY --from=jre /opt/java/openjdk /opt/java/openjdk
RUN apt-get update \
 && apt-get install -y --no-install-recommends chromium chromium-driver fonts-dejavu-core fonts-liberation \
        ca-certificates curl tzdata \
 && rm -rf /var/lib/apt/lists/*

# not root: the app, its data and the browsers' saved logins belong to this user. The ids match
# the host user who owns ./data and ./secrets (bind mounts keep the host's owners).
ARG UID=1000
ARG GID=1000
RUN groupadd -g "$GID" bzscraper \
 && useradd -u "$UID" -g "$GID" -m -d /home/bzscraper -s /usr/sbin/nologin bzscraper \
 && mkdir -p /app/data /home/bzscraper/.bzscraper \
 && chown -R bzscraper:bzscraper /app /home/bzscraper

WORKDIR /app
# layers from the least to the most often changed, so a code change ships only the last one
COPY --from=build --chown=bzscraper:bzscraper /out/dependencies/ ./
COPY --from=build --chown=bzscraper:bzscraper /out/spring-boot-loader/ ./
COPY --from=build --chown=bzscraper:bzscraper /out/snapshot-dependencies/ ./
COPY --from=build --chown=bzscraper:bzscraper /out/application/ ./

USER bzscraper
ENV BZSCRAPER_BROWSER_CHROMIUMBINARY=/usr/bin/chromium \
    BZSCRAPER_BROWSER_CHROMEDRIVER=/usr/bin/chromedriver
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8081/actuator/health > /dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
