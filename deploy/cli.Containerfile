# Hermetic notify4j-cli image: a JRE + the notify4j-cli fat jar, so `notify4j send …` runs on
# ANY CI (or any host with Docker) without Java installed. Also the image the Docker-based
# GitHub Action wraps.
#
# Build context is the CLI module's target dir (the jar must be built first):
#   ./mvnw -pl notify4j-cli -am package -DskipTests
#   docker build -t ghcr.io/alexmond/notify4j-cli:<tag> -f deploy/cli.Containerfile notify4j-cli/target
#
# Run (pass channel URLs via env — never bake a secret into a layer):
#   docker run --rm -e NOTIFY4J_URLS='slack://hooks.slack.com/services/T/B/X' \
#     ghcr.io/alexmond/notify4j-cli send -t "CI" -b "build passed"
FROM eclipse-temurin:17-jre

COPY notify4j-cli.jar /app/notify4j-cli.jar

RUN useradd --uid 1001 --system --create-home --shell /usr/sbin/nologin app
USER 1001

ENTRYPOINT ["java", "-jar", "/app/notify4j-cli.jar"]
