# Backend image for the food delivery API.
# node:22-slim (Debian) is used instead of Alpine because bcrypt ships
# ready-made binaries for it, so no C++ build tools are needed.
FROM node:22-slim

WORKDIR /app

# Copy only the dependency files first. Docker caches this layer, so
# `npm ci` re-runs only when package.json or package-lock.json change,
# not on every code edit.
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

# Then copy the rest of the source code (.dockerignore keeps out
# node_modules, .env, tests and uploaded images).
COPY . .

# Uploaded food images are stored here. docker-compose mounts this folder
# from the laptop, so images survive when the container is recreated.
RUN mkdir -p uploads && chown -R node:node /app

ENV NODE_ENV=production
EXPOSE 4000

# Run as the built-in unprivileged "node" user instead of root.
USER node

# Docker marks the container "unhealthy" if the API stops answering.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD node -e "fetch('http://localhost:4000/').then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"

CMD ["node", "server.js"]
