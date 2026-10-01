import type { Id, SyncMeta } from '../domain/identity'
import type { MutationSink } from './sink'
import { put, type StoreName } from './db'

/**
 * Возврат записи к прежнему виду — основа отмены для любого раздела.
 *
 * Время, часы и устройство проставляются заново: отмена — тоже изменение,
 * и при слиянии она обязана победить то, что отменяет. Признак удаления
 * берётся из самой записи: отмена создания кладёт надгробие, и если брать
 * его из свежего штампа, где он всегда пуст, отменённая запись воскресает.
 */
export async function restoreRow(
  db: IDBDatabase,
  sink: MutationSink,
  store: StoreName,
  row: { id: Id; meta: SyncMeta },
): Promise<void> {
  const stamp = sink.stamp()
  await put(db, store, { ...row, meta: { ...stamp, deletedAt: row.meta.deletedAt } })
}
