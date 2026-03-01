/**
 * Server entry point. Delegates to startServer() in index.ts which runs
 * migrations, builds the app, and starts listening.
 * This file is NOT imported by tests — use buildApp() from index.ts instead.
 */
import { startServer } from './index.js'

const PORT = parseInt(process.env.PORT ?? '3000', 10)
const DB_PATH = process.env.DATABASE_PATH ?? '/data/outlinergod.db'

await startServer(PORT, DB_PATH)
