import { createConnection } from './connection.js'

/**
 * Singleton DB connection for production use.
 * Tests must NOT import this — use createConnection(':memory:') directly
 * or createTestDb() from src/test-helpers/createTestDb.ts.
 */
export const db = createConnection(process.env.DATABASE_PATH ?? ':memory:')
