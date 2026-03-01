/**
 * P2-20 / P2-21: Settings routes
 *
 * GET  /api/settings — fetch user preferences (defaults if none saved)
 * PUT  /api/settings — replace user preferences with HLC generation
 */
import { randomUUID } from 'node:crypto'
import type { FastifyInstance } from 'fastify'
import type Database from 'better-sqlite3'
import { requireAuth } from '../middleware/auth.js'
import { hlcGenerate } from '../hlc/hlc.js'

interface SettingsRow {
  id: string
  user_id: string
  theme: string
  theme_hlc: string
  density: string
  density_hlc: string
  show_guide_lines: number
  show_guide_lines_hlc: string
  show_backlink_badge: number
  show_backlink_badge_hlc: string
  device_id: string
  created_at: number
  updated_at: number
}

function toSettingsResponse(row: SettingsRow) {
  return {
    theme: row.theme,
    density: row.density,
    show_guide_lines: row.show_guide_lines === 1,
    show_backlink_badge: row.show_backlink_badge === 1,
    updated_at: row.updated_at,
  }
}

const VALID_THEMES = ['dark', 'light'] as const
const VALID_DENSITIES = ['cozy', 'comfortable', 'compact'] as const

export function createSettingsRoutes(sqlite: InstanceType<typeof Database>) {
  return async function settingsRoutes(fastify: FastifyInstance) {
    // -----------------------------------------------------------------------
    // GET /settings — fetch user preferences
    // -----------------------------------------------------------------------
    fastify.get('/settings', { preHandler: requireAuth }, async (req) => {
      const row = sqlite
        .prepare('SELECT * FROM settings WHERE user_id = ?')
        .get(req.user!.id) as SettingsRow | undefined

      if (!row) {
        return {
          theme: 'dark',
          density: 'cozy',
          show_guide_lines: true,
          show_backlink_badge: true,
          updated_at: 0,
        }
      }

      return toSettingsResponse(row)
    })

    // -----------------------------------------------------------------------
    // PUT /settings — replace user preferences
    // -----------------------------------------------------------------------
    fastify.put('/settings', { preHandler: requireAuth }, async (req, reply) => {
      const body = req.body as {
        theme?: unknown
        density?: unknown
        show_guide_lines?: unknown
        show_backlink_badge?: unknown
      }

      if (
        !body ||
        typeof body.theme !== 'string' ||
        !VALID_THEMES.includes(body.theme as typeof VALID_THEMES[number]) ||
        typeof body.density !== 'string' ||
        !VALID_DENSITIES.includes(body.density as typeof VALID_DENSITIES[number]) ||
        typeof body.show_guide_lines !== 'boolean' ||
        typeof body.show_backlink_badge !== 'boolean'
      ) {
        return reply.status(400).send({ error: 'Missing or invalid fields' })
      }

      const hlc = hlcGenerate('server')
      const now = Date.now()
      const userId = req.user!.id

      // Check if a settings row exists for this user
      const existing = sqlite
        .prepare('SELECT id FROM settings WHERE user_id = ?')
        .get(userId) as { id: string } | undefined

      const id = existing?.id ?? randomUUID()

      sqlite
        .prepare(
          `INSERT INTO settings (id, user_id, theme, theme_hlc, density, density_hlc, show_guide_lines, show_guide_lines_hlc, show_backlink_badge, show_backlink_badge_hlc, device_id, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT(id) DO UPDATE SET
             theme = excluded.theme,
             theme_hlc = excluded.theme_hlc,
             density = excluded.density,
             density_hlc = excluded.density_hlc,
             show_guide_lines = excluded.show_guide_lines,
             show_guide_lines_hlc = excluded.show_guide_lines_hlc,
             show_backlink_badge = excluded.show_backlink_badge,
             show_backlink_badge_hlc = excluded.show_backlink_badge_hlc,
             device_id = excluded.device_id,
             updated_at = excluded.updated_at`,
        )
        .run(
          id,
          userId,
          body.theme,
          hlc,
          body.density,
          hlc,
          body.show_guide_lines ? 1 : 0,
          hlc,
          body.show_backlink_badge ? 1 : 0,
          hlc,
          'server',
          now,
          now,
        )

      const row = sqlite
        .prepare('SELECT * FROM settings WHERE id = ?')
        .get(id) as SettingsRow

      return toSettingsResponse(row)
    })
  }
}
