/**
 * Тонкая обёртка над IndexedDB.
 *
 * Хранилища повторяют таблицы Room один в один, включая поля синхронизации:
 * формат данных — это контракт с будущим Mac-приложением и с архивом-бэкапом,
 * и расходиться ему с Kotlin-версией нельзя.
 */

export const DB_NAME = 'punchline'
export const DB_VERSION = 2

export const STORES = [
  'topics', 'bits', 'setLists', 'gigs', 'performances',
  'journal', 'exercises', 'audio', 'versions', 'settings', 'meta',
] as const
export type StoreName = (typeof STORES)[number]

export function openDb(name = DB_NAME): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(name, DB_VERSION)
    req.onupgradeneeded = () => {
      const db = req.result
      // Хранилища добавляются, но никогда не удаляются: старая версия базы
      // на другом устройстве может содержать записи, которых здесь ещё нет.
      for (const s of STORES) {
        if (!db.objectStoreNames.contains(s)) db.createObjectStore(s, { keyPath: 'id' })
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

function promisify<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

export async function putAll(db: IDBDatabase, store: StoreName, rows: readonly unknown[]): Promise<void> {
  if (rows.length === 0) return
  const tx = db.transaction(store, 'readwrite')
  const os = tx.objectStore(store)
  for (const r of rows) os.put(r)
  await new Promise<void>((resolve, reject) => {
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
}

export async function put(db: IDBDatabase, store: StoreName, row: unknown): Promise<void> {
  await putAll(db, store, [row])
}

export async function getAll<T>(db: IDBDatabase, store: StoreName): Promise<T[]> {
  const tx = db.transaction(store, 'readonly')
  return promisify(tx.objectStore(store).getAll() as IDBRequest<T[]>)
}

export async function get<T>(db: IDBDatabase, store: StoreName, id: string): Promise<T | undefined> {
  const tx = db.transaction(store, 'readonly')
  return promisify(tx.objectStore(store).get(id) as IDBRequest<T | undefined>)
}

/**
 * Просит браузер не выселять хранилище при нехватке места.
 * Материал за год — это не кэш, и терять его молча нельзя.
 */
export async function requestPersistence(): Promise<boolean> {
  if (!navigator.storage?.persist) return false
  if (await navigator.storage.persisted()) return true
  return navigator.storage.persist()
}
