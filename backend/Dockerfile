# Stage 1: builder — compile TypeScript to dist/
FROM node:20-alpine AS builder

RUN corepack enable && corepack prepare pnpm@latest --activate

WORKDIR /app

# Install build tools needed for better-sqlite3 native compilation
RUN apk add --no-cache python3 make g++

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY tsconfig.json ./
COPY src/ ./src/

RUN pnpm run build

# Stage 2: runner — lean production image
FROM node:20-alpine AS runner

WORKDIR /app

# Copy compiled output and dependencies from builder
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./
# Migration SQL files must be present at runtime (migrate.ts resolves ../../drizzle from dist/db/)
COPY drizzle ./drizzle

# Create /data dir with uploads subdir, owned by node so the named volume is writable
RUN mkdir -p /data/uploads && chown -R node:node /data

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "require('http').get('http://localhost:3000/health',(r)=>process.exit(r.statusCode===200?0:1))"

USER node

CMD ["node", "dist/server.js"]
