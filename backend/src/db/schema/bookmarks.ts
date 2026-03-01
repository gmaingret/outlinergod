import { sqliteTable, text, integer } from 'drizzle-orm/sqlite-core'
import { users } from './users.js'
import { documents } from './documents.js'
import { nodes } from './nodes.js'

export const bookmarks = sqliteTable('bookmarks', {
  id: text('id').primaryKey(),
  user_id: text('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  node_id: text('node_id')
    .notNull()
    .references(() => nodes.id, { onDelete: 'cascade' }),
  document_id: text('document_id')
    .notNull()
    .references(() => documents.id, { onDelete: 'cascade' }),
  sort_order: text('sort_order').notNull(),
  sort_order_hlc: text('sort_order_hlc').notNull(),
  deleted_at: integer('deleted_at'),
  deleted_hlc: text('deleted_hlc').notNull(),
  device_id: text('device_id').notNull(),
  created_at: integer('created_at').notNull(),
  updated_at: integer('updated_at').notNull(),
})

export type Bookmark = typeof bookmarks.$inferSelect
export type NewBookmark = typeof bookmarks.$inferInsert
