import { describe, expect, it } from 'vitest'
import { CHANGES_BEFORE_BACKUP, MILLIS_BEFORE_BACKUP, backupDue, inspectVault, vaultFileName } from './backup'

const NOW = 1_700_000_000_000

describe('архив', () => {
  it('не предлагается, пока ничего не менялось', () => {
    expect(backupDue(0, null, NOW)).toBe(false)
    expect(backupDue(0, NOW - MILLIS_BEFORE_BACKUP * 10, NOW)).toBe(false)
  })

  it('первый архив ждёт накопления правок', () => {
    expect(backupDue(CHANGES_BEFORE_BACKUP - 1, null, NOW)).toBe(false)
    expect(backupDue(CHANGES_BEFORE_BACKUP, null, NOW)).toBe(true)
  })

  it('сутки без архива важнее числа правок', () => {
    const yesterday = NOW - MILLIS_BEFORE_BACKUP
    expect(backupDue(1, yesterday, NOW)).toBe(true)
    expect(backupDue(1, NOW - 1000, NOW)).toBe(false)
  })

  it('битый файл не проходит проверку', () => {
    expect(inspectVault(null).ok).toBe(false)
    expect(inspectVault({ app: 'other', format: 1, data: {} }).ok).toBe(false)
    expect(inspectVault({ app: 'punchline', format: 99, data: {} }).ok).toBe(false)
    expect(inspectVault({ app: 'punchline', format: 1, data: { bits: 'нет' } }).ok).toBe(false)
  })

  it('целый архив проходит и считает записи', () => {
    const r = inspectVault({ app: 'punchline', format: 1, data: { bits: [{ id: 'a' }, { id: 'b' }] } })
    expect(r.ok).toBe(true)
    expect(r.counts?.bits).toBe(2)
    expect(r.counts?.topics).toBe(0)
  })

  it('имя файла читается человеком и сортируется по дате', () => {
    expect(vaultFileName(NOW)).toMatch(/^punchline-\d{4}-\d{2}-\d{2}-\d{4}\.json$/)
  })
})
