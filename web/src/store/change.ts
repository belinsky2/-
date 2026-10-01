import type { SyncMeta } from '../domain/identity'

/**
 * Что было и что стало. Нужно отмене: если запись существовала — вернуть
 * прежнюю, если её создали только что — положить надгробие поверх новой.
 * Прежде при создании возвращался null, отмена не регистрировалась, и
 * полоса продолжала предлагать отменить предыдущее действие.
 */
export interface Change<T> {
  readonly prev: T | null
  readonly row: T
}

/** Запись, которую нужно положить в базу, чтобы отменить изменение. */
export function undoRowOf<T extends { meta: SyncMeta }>(c: Change<T>): T {
  return c.prev ?? { ...c.row, meta: { ...c.row.meta, deletedAt: c.row.meta.updatedAt } }
}
