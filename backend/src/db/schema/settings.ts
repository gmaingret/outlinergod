import { sqliteTable, text, integer } from 'drizzle-orm/sqlite-core'
import { users } from './users.js'

export const settings = sqliteTable('settings', {
  id: text('id').primaryKey(),
  user_id: text('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),
  theme: text('theme').notNull().default('dark'),
  theme_hlc: text('theme_hlc').notNull(),
  density: text('density').notNull().default('cozy'),
  density_hlc: text('density_hlc').notNull(),
  show_guide_lines: integer('show_guide_lines').notNull().default(1),
  show_guide_lines_hlc: text('show_guide_lines_hlc').notNull(),
  show_backlink_badge: integer('show_backlink_badge').notNull().default(1),
  show_backlink_badge_hlc: text('show_backlink_badge_hlc').notNull(),
  device_id: text('device_id').notNull(),
  created_at: integer('created_at').notNull(),
  updated_at: integer('updated_at').notNull(),
})

export type Setting = typeof settings.$inferSelect
export type NewSetting = typeof settings.$inferInsert
