import { sqliteTable, text, integer, AnySQLiteColumn } from 'drizzle-orm/sqlite-core'
import { users } from './users.js'

export const documents = sqliteTable('documents', {
  id: text('id').primaryKey(),
  user_id: text('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  title: text('title').notNull(),
  title_hlc: text('title_hlc').notNull(),
  type: text('type').notNull(),
  parent_id: text('parent_id').references((): AnySQLiteColumn => documents.id),
  parent_id_hlc: text('parent_id_hlc').notNull(),
  sort_order: text('sort_order').notNull(),
  sort_order_hlc: text('sort_order_hlc').notNull(),
  collapsed: integer('collapsed').notNull().default(0),
  collapsed_hlc: text('collapsed_hlc').notNull(),
  deleted_at: integer('deleted_at'),
  deleted_hlc: text('deleted_hlc').notNull(),
  device_id: text('device_id').notNull(),
  created_at: integer('created_at').notNull(),
  updated_at: integer('updated_at').notNull(),
})

export type Document = typeof documents.$inferSelect
export type NewDocument = typeof documents.$inferInsert
