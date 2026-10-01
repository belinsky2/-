import { STORES, getAll, putAll, type StoreName } from './db'
import { wins, type SyncMeta } from '../domain/identity'
import type { MutationSink } from './sink'

/**
 * Архив всего материала одним файлом.
 *
 * Данные лежат в браузере, а браузер вправе их вычистить. Поэтому бэкап не
 * прячется в настройках, а падает в Загрузки сам: пользователь не должен
 * помнить о резервной копии, чтобы её иметь.
 *
 * Формат — обычный JSON, не zip: архив должен открываться на телефоне без
 * распаковки. Прошлая версия отдавала zip с тремя файлами внутри, и это
 * оказалось непонятно.
 */

export const VAULT_FORMAT = 1

export interface Vault {
  readonly format: number
  readonly app: 'punchline'
  readonly exportedAt: number
  readonly deviceId: string
  readonly lamport: number
  readonly data: Readonly<Record<string, readonly unknown[]>>
}

/**
 * Двоичные поля в JSON.
 *
 * Аудиозапись — это Blob, а JSON.stringify превращает его в пустой объект.
 * Без явного кодирования архив молча терял бы записи голоса, продолжая
 * выглядеть целым, — самый опасный вид потери.
 */
const BLOB_TAG = '__blob__'

interface EncodedBlob {
  readonly [BLOB_TAG]: string
  readonly type: string
}

function isEncodedBlob(v: unknown): v is EncodedBlob {
  return typeof v === 'object' && v !== null && BLOB_TAG in v
}

async function blobToBase64(b: Blob): Promise<string> {
  const buf = new Uint8Array(await b.arrayBuffer())
  let bin = ''
  // Кусками: apply на массиве в мегабайты падает на переполнении стека.
  const CHUNK = 0x8000
  for (let i = 0; i < buf.length; i += CHUNK) {
    bin += String.fromCharCode(...buf.subarray(i, i + CHUNK))
  }
  return btoa(bin)
}

function base64ToBlob(b64: string, type: string): Blob {
  const bin = atob(b64)
  const out = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i)
  return new Blob([out], { type })
}

async function encodeRow(row: unknown): Promise<unknown> {
  if (typeof row !== 'object' || row === null) return row
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(row as Record<string, unknown>)) {
    out[k] = v instanceof Blob ? { [BLOB_TAG]: await blobToBase64(v), type: v.type } : v
  }
  return out
}

export function decodeRow(row: unknown): unknown {
  if (typeof row !== 'object' || row === null) return row
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(row as Record<string, unknown>)) {
    out[k] = isEncodedBlob(v) ? base64ToBlob(v[BLOB_TAG], v.type) : v
  }
  return out
}

export async function buildVault(
  db: IDBDatabase,
  deviceId: string,
  lamport: number,
  now: number,
): Promise<Vault> {
  const data: Record<string, readonly unknown[]> = {}
  for (const s of STORES) {
    // Надгробия тоже уезжают в архив: без них удаление не переживёт слияние.
    const rows = await getAll<unknown>(db, s as StoreName)
    data[s] = await Promise.all(rows.map(encodeRow))
  }
  return { format: VAULT_FORMAT, app: 'punchline', exportedAt: now, deviceId, lamport, data }
}

export function vaultFileName(now: number): string {
  const d = new Date(now)
  const p = (n: number) => String(n).padStart(2, '0')
  return `punchline-${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}.json`
}

/** Отдаёт архив как файл. Один файл, который открывается где угодно. */
export function downloadVault(vault: Vault): void {
  const blob = new Blob([JSON.stringify(vault, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = vaultFileName(vault.exportedAt)
  a.click()
  // Освобождать сразу нельзя: Safari не успевает начать скачивание.
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

export interface VaultCheck {
  readonly ok: boolean
  readonly reason?: string
  readonly counts?: Readonly<Record<string, number>>
}

/**
 * Проверка архива до того, как он что-либо перезапишет.
 * Битый файл не должен стирать то, что уже есть на устройстве.
 */
export function inspectVault(raw: unknown): VaultCheck {
  if (typeof raw !== 'object' || raw === null) return { ok: false, reason: 'файл не похож на архив' }
  const v = raw as Partial<Vault>
  if (v.app !== 'punchline') return { ok: false, reason: 'архив не от этого приложения' }
  if (v.format !== VAULT_FORMAT) return { ok: false, reason: `версия архива ${String(v.format)} не поддерживается` }
  if (typeof v.data !== 'object' || v.data === null) return { ok: false, reason: 'в архиве нет данных' }
  const counts: Record<string, number> = {}
  for (const s of STORES) {
    const rows = (v.data as Record<string, unknown>)[s]
    if (rows !== undefined && !Array.isArray(rows)) return { ok: false, reason: `раздел «${s}» повреждён` }
    counts[s] = Array.isArray(rows) ? rows.length : 0
  }
  return { ok: true, counts }
}

/**
 * Когда пора сохранить архив сам.
 *
 * Скачивание файла браузер разрешает в ответ на действие человека, поэтому
 * проверка живёт в момент правки, а не в фоне. Порог двойной: и по числу
 * изменений, и по времени — редкий автор за неделю не наберёт двадцати правок,
 * но потерять неделю всё равно нельзя.
 */
export const CHANGES_BEFORE_BACKUP = 20
export const MILLIS_BEFORE_BACKUP = 24 * 60 * 60 * 1000

export function backupDue(
  changesSince: number,
  lastBackupAt: number | null,
  now: number,
): boolean {
  if (changesSince === 0) return false
  if (lastBackupAt === null) return changesSince >= CHANGES_BEFORE_BACKUP
  return changesSince >= CHANGES_BEFORE_BACKUP || now - lastBackupAt >= MILLIS_BEFORE_BACKUP
}

/** Итог восстановления — что именно произошло с материалом. */
export type ImportReport =
  | { readonly ok: true; readonly added: number; readonly updated: number; readonly kept: number }
  | { readonly ok: false; readonly reason: string }

/** Разбор файла архива. Битый файл — это отказ с причиной, а не исключение. */
export function parseVault(text: string): { ok: true; raw: unknown } | { ok: false; reason: string } {
  try {
    return { ok: true, raw: JSON.parse(text) }
  } catch {
    return { ok: false, reason: 'файл повреждён: это не архив' }
  }
}

interface Row { id: string; meta?: SyncMeta }

/**
 * Восстановление из архива слиянием, а не перезаписью.
 *
 * Для каждой записи остаётся та версия, что побеждает по логическим часам.
 * Прежняя реализация клала архив поверх базы целиком, и восстановление
 * старого архива молча стирало всё, что было сделано после него.
 *
 * Разделы, которых эта версия приложения не знает, пропускаются: архив,
 * сделанный более новой версией, не должен обрывать импорт на середине.
 */
export async function importVault(
  db: IDBDatabase,
  sink: MutationSink,
  raw: unknown,
): Promise<ImportReport> {
  const check = inspectVault(raw)
  if (!check.ok) return { ok: false, reason: check.reason ?? 'архив не прочитан' }

  const data = (raw as { data: Record<string, unknown> }).data
  let added = 0
  let updated = 0
  let kept = 0

  for (const store of STORES) {
    const list = data[store]
    if (!Array.isArray(list)) continue
    const local = new Map((await getAll<Row>(db, store)).map((r) => [r.id, r]))
    const toWrite: unknown[] = []

    for (const item of list.map(decodeRow) as Row[]) {
      if (!item || typeof item.id !== 'string') continue
      if (item.meta) sink.observe(item.meta.lamport)
      const mine = local.get(item.id)
      if (!mine) {
        toWrite.push(item)
        added++
      } else if (item.meta && (!mine.meta || wins(item.meta, mine.meta))) {
        toWrite.push(item)
        updated++
      } else {
        kept++
      }
    }
    await putAll(db, store, toWrite)
  }
  return { ok: true, added, updated, kept }
}
