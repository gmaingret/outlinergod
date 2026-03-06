# Stage 1: Build React SPA
FROM node:20-alpine AS web-builder
RUN corepack enable && corepack prepare pnpm@latest --activate
WORKDIR /web
COPY web/package.json web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY web/ ./
RUN pnpm run build

# Stage 2: Compile backend TypeScript
FROM node:20-alpine AS ts-builder
RUN corepack enable && corepack prepare pnpm@latest --activate
WORKDIR /app
RUN apk add --no-cache python3 make g++
COPY backend/package.json backend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY backend/tsconfig.json ./
COPY backend/src/ ./src/
RUN pnpm run build

# Stage 3: Lean production runner
FROM node:20-alpine AS runner
WORKDIR /app
COPY --from=ts-builder /app/dist ./dist
COPY --from=ts-builder /app/node_modules ./node_modules
COPY --from=ts-builder /app/package.json ./
# Migration SQL files must be present at runtime (migrate.ts resolves ../../drizzle from dist/db/)
COPY backend/drizzle ./drizzle
COPY --from=web-builder /web/dist ./web/dist
RUN mkdir -p /data/uploads && chown -R node:node /data
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "require('http').get('http://localhost:3000/health',(r)=>process.exit(r.statusCode===200?0:1))"
USER node
CMD ["node", "dist/server.js"]
