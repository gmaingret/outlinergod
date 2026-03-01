import { sqliteTable, text, integer } from 'drizzle-orm/sqlite-core'
import { users } from './users.js'

export const settings = sqliteTable('settings', {
  id: text('id').primaryKey(),
  user_id: text('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  theme: text('theme').notNull().default('system'),
  theme_hlc: text('theme_hlc').notNull(),
  font_size: integer('font_size').notNull().default(14),
  font_size_hlc: text('font_size_hlc').notNull(),
  device_id: text('device_id').notNull(),
  created_at: integer('created_at').notNull(),
  updated_at: integer('updated_at').notNull(),
})

export type Setting = typeof settings.$inferSelect
export type NewSetting = typeof settings.$inferInsert
