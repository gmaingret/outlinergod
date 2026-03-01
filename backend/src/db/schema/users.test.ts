import { describe, it, expect } from 'vitest'
import { getTableConfig } from 'drizzle-orm/sqlite-core'
import { drizzle } from 'drizzle-orm/better-sqlite3'
import Database from 'better-sqlite3'
import { users, type User } from './users.js'

describe('users schema', () => {
  it('users_tableName_isUsers', () => {
    const config = getTableConfig(users)
    expect(config.name).toBe('users')
  })

  it('users_idIsPrimaryKey', () => {
    const config = getTableConfig(users)
    const idCol = config.columns.find((c) => c.name === 'id')
    expect(idCol?.primary).toBe(true)
  })

  it('users_googleSub_hasUniqueConstraint', () => {
    const config = getTableConfig(users)
    // google_sub is unique — check via uniqueConstraints or column isUnique
    const hasUniqueConstraint = config.uniqueConstraints.some((uc) =>
      uc.columns.some((c) => c.name === 'google_sub'),
    )
    const colIsUnique = config.columns.find((c) => c.name === 'google_sub')?.isUnique === true
    expect(hasUniqueConstraint || colIsUnique).toBe(true)
  })

  it('users_insert_andSelect_roundtrip', () => {
    const sqlite = new Database(':memory:')
    sqlite.pragma('foreign_keys = ON')
    sqlite.exec(`
      CREATE TABLE users (
        id TEXT PRIMARY KEY NOT NULL,
        google_sub TEXT NOT NULL UNIQUE,
        email TEXT NOT NULL,
        name TEXT NOT NULL,
        picture TEXT NOT NULL DEFAULT '',
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )
    `)
    const db = drizzle(sqlite)

    const now = Date.now()
    const newUser: User = {
      id: 'user-uuid-1',
      google_sub: 'google-sub-123',
      email: 'test@example.com',
      name: 'Test User',
      picture: 'https://example.com/pic.jpg',
      created_at: now,
      updated_at: now,
    }

    db.insert(users).values(newUser).run()
    const result = db.select().from(users).all()

    expect(result).toHaveLength(1)
    expect(result[0]).toEqual(newUser)
  })
})
