import { sqliteTable, text, integer } from 'drizzle-orm/sqlite-core'
import { users } from './users.js'

export const bookmarks = sqliteTable('bookmarks', {
  id: text('id').primaryKey(),
  user_id: text('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  title: text('title').notNull(),
  title_hlc: text('title_hlc').notNull(),
  target_type: text('target_type').notNull(), // 'document' | 'node' | 'search'
  target_type_hlc: text('target_type_hlc').notNull(),
  target_document_id: text('target_document_id'),
  target_document_id_hlc: text('target_document_id_hlc').notNull(),
  target_node_id: text('target_node_id'),
  target_node_id_hlc: text('target_node_id_hlc').notNull(),
  query: text('query'),
  query_hlc: text('query_hlc').notNull(),
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
