import { sqliteTable, text, integer } from 'drizzle-orm/sqlite-core'
import { users } from './users.js'

export const files = sqliteTable('files', {
  filename: text('filename').primaryKey(),
  user_id: text('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  node_id: text('node_id'),
  mime_type: text('mime_type').notNull().default(''),
  size: integer('size').notNull().default(0),
  created_at: integer('created_at').notNull(),
})

export type FileRecord = typeof files.$inferSelect
export type NewFileRecord = typeof files.$inferInsert
