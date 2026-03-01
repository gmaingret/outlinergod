import { sqliteTable, text, integer, AnySQLiteColumn } from 'drizzle-orm/sqlite-core'
import { users } from './users.js'
import { documents } from './documents.js'

export const nodes = sqliteTable('nodes', {
  id: text('id').primaryKey(),
  document_id: text('document_id')
    .notNull()
    .references(() => documents.id, { onDelete: 'cascade' }),
  user_id: text('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  content: text('content').notNull().default(''),
  content_hlc: text('content_hlc').notNull(),
  note: text('note').notNull().default(''),
  note_hlc: text('note_hlc').notNull(),
  parent_id: text('parent_id').references((): AnySQLiteColumn => nodes.id),
  parent_id_hlc: text('parent_id_hlc').notNull(),
  sort_order: text('sort_order').notNull(),
  sort_order_hlc: text('sort_order_hlc').notNull(),
  completed: integer('completed').notNull().default(0),
  completed_hlc: text('completed_hlc').notNull(),
  color: integer('color').notNull().default(0),
  color_hlc: text('color_hlc').notNull(),
  collapsed: integer('collapsed').notNull().default(0),
  collapsed_hlc: text('collapsed_hlc').notNull(),
  deleted_at: integer('deleted_at'),
  deleted_hlc: text('deleted_hlc').notNull(),
  device_id: text('device_id').notNull(),
  created_at: integer('created_at').notNull(),
  updated_at: integer('updated_at').notNull(),
})

export type Node = typeof nodes.$inferSelect
export type NewNode = typeof nodes.$inferInsert
