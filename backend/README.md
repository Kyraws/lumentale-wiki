# Backend

Java 17 / Spring Boot 3.5 with plain JDBC, PostgreSQL and Flyway. Domain packages follow Controller -> Service -> Repository; shared helpers handle localization, asset URLs, reference indexes and validation. See the [current engineering source tour](../docs/ENGINEERING.md) for implementation links and tradeoffs.

## Run

Start the [root Compose database](../docker-compose.yml), then from `backend/`:

```bash
./gradlew bootRun
```

Use `gradlew.bat bootRun` on Windows. The API defaults to port **8084**, with PostgreSQL database/user/password `anidex4` on localhost port **5433** for local development. Gradle sets the JVM working directory to the repository root so `wiki-db/migrations` and local data paths resolve correctly.

Flyway applies the checked-in migrations. Seeding defaults to **off**; a fresh clone contains no game dataset. See the [data requirements](../README.md#data-separately-supplied-never-redistributed-here) before enabling `LUMENTALE_SEED_ON_EMPTY=true`. Optional additional inputs use `LUMENTALE_PHASE4_DIR`.

The running API exposes its index at `http://localhost:8084/` and counts at `/api/meta`. Environment overrides, including `SPRING_DATASOURCE_*`, are defined in [application.properties](src/main/resources/application.properties).

## Verify

```bash
./gradlew test
./gradlew bootJar
```

Unit tests cover selected domain logic, including graph assembly, quest flow and type calculations. These commands are provided for local verification; this documentation change does not establish a new passing run, seeded import result or production deployment.

[ARCHITECTURE.md](ARCHITECTURE.md) and [graph.md](graph.md) retain historical design notes. Earlier database names, ports, counts and test reports there are not current setup instructions.
