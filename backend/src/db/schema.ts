import { sqliteTable, text, integer } from 'drizzle-orm/sqlite-core'

// ---------------------------------------------------------------------------
// nodes — core outliner entity. sort_order is ALWAYS TEXT (fractional index).
// Every syncable field has a companion *_hlc TEXT column for per-field LWW.
// Partial indexes (WHERE clause) are written directly in the migration SQL
// because drizzle-orm 0.45.1 does not expose partial-index support in the
// schema API.
// ---------------------------------------------------------------------------

export const users = sqliteTable('users', {
  id: text('id').primaryKey(),
  google_sub: text('google_sub').notNull().unique(),
  email: text('email').notNull(),
  name: text('name').notNull(),
  picture: text('picture').notNull().default(''),
  created_at: integer('created_at').notNull(),
  updated_at: integer('updated_at').notNull(),
})

export const refreshTokens = sqliteTable('refresh_tokens', {
  token: text('token').primaryKey(),
  user_id: text('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  device_id: text('device_id').notNull(),
  expires_at: integer('expires_at').notNull(),
  created_at: integer('created_at').notNull(),
  revoked: integer('revoked').notNull().default(0),
})

export const nodes = sqliteTable('nodes', {
  id: text('id').primaryKey(),
  // ── syncable fields ─────────────────────────────────────────────────────
  content: text('content').notNull().default(''),
  content_hlc: text('content_hlc').notNull().default(''),
  note: text('note').notNull().default(''),
  note_hlc: text('note_hlc').notNull().default(''),
  // parent_id is nullable (null = document root); self-reference handled app-side
  parent_id: text('parent_id'),
  parent_id_hlc: text('parent_id_hlc').notNull().default(''),
  // sort_order is TEXT fractional index — NEVER a number/REAL
  sort_order: text('sort_order').notNull().default(''),
  sort_order_hlc: text('sort_order_hlc').notNull().default(''),
  completed: integer('completed').notNull().default(0),
  completed_hlc: text('completed_hlc').notNull().default(''),
  color: integer('color').notNull().default(0),
  color_hlc: text('color_hlc').notNull().default(''),
  collapsed: integer('collapsed').notNull().default(0),
  collapsed_hlc: text('collapsed_hlc').notNull().default(''),
  // ── soft delete ──────────────────────────────────────────────────────────
  deleted_at: integer('deleted_at'), // nullable Unix ms; null = active
  deleted_hlc: text('deleted_hlc').notNull().default(''),
  // ── metadata ─────────────────────────────────────────────────────────────
  device_id: text('device_id').notNull().default(''),
  created_at: integer('created_at').notNull(),
  updated_at: integer('updated_at').notNull(),
})

export type User = typeof users.$inferSelect
export type NewUser = typeof users.$inferInsert
export type RefreshToken = typeof refreshTokens.$inferSelect
export type NewRefreshToken = typeof refreshTokens.$inferInsert
export type Node = typeof nodes.$inferSelect
export type NewNode = typeof nodes.$inferInsert
