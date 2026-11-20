FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 10001 app
WORKDIR /app
COPY target/jobhunt-1.0.0.jar app.jar
COPY companies.yaml companies.yaml
USER app
# small heap: the host has 1 GB of RAM shared with other apps
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:+UseSerialGC" PORT=8000
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD curl -fs http://localhost:8000/api/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
