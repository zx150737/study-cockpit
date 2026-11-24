FROM node:22-bookworm-slim AS frontend-build
WORKDIR /build/frontend
RUN npm install --global pnpm@11.7.0
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm typecheck && pnpm lint --max-warnings 0 && pnpm build

FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /build/backend
COPY backend/pom.xml ./
COPY backend/src ./src
RUN mvn -B --no-transfer-progress verify

FROM eclipse-temurin:17-jre-jammy AS backend
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 cockpit && useradd --uid 10001 --gid cockpit --no-create-home cockpit \
    && mkdir -p /app /data/study-cockpit/uploads \
    && chown -R cockpit:cockpit /app /data/study-cockpit
WORKDIR /app
COPY --from=backend-build --chown=cockpit:cockpit /build/backend/target/study-cockpit-1.1.0.jar app.jar
USER cockpit
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-Dfile.encoding=UTF-8", "-Xms128m", "-Xmx512m", "-jar", "/app/app.jar"]

FROM caddy:2-alpine AS web
COPY --from=frontend-build /build/frontend/dist /srv
COPY deploy/docker/Caddyfile /etc/caddy/Caddyfile
