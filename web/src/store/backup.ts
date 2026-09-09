import { STORES, getAll, type StoreName } from './db'

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

export async function buildVault(
  db: IDBDatabase,
  deviceId: string,
  lamport: number,
  now: number,
): Promise<Vault> {
  const data: Record<string, readonly unknown[]> = {}
  for (const s of STORES) {
    // Надгробия тоже уезжают в архив: без них удаление не переживёт слияние.
    data[s] = await getAll<unknown>(db, s as StoreName)
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
