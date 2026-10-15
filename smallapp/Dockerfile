# Customer app: build the React app with Vite, then serve the static files with nginx.

# ---- Stage 1: build ----
FROM node:22-slim AS build
WORKDIR /app

# Copy only the dependency files first so `npm ci` is cached until they change
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Vite bakes this into the JavaScript at build time. It is the address the
# BROWSER uses to reach the gateway, so it must work from the laptop (localhost),
# not a Docker service name.
ARG VITE_API_URL=http://localhost:8090
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# ---- Stage 2: serve ----
# nginx-unprivileged runs as a non-root user and listens on 8080
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1
