import type { Clock, DeviceId, SyncMeta } from '../domain/identity'

/**
 * Единственная точка, через которую проходит любая запись в базу.
 *
 * Сейчас она делает немного: проставляет время, логические часы и устройство.
 * Смысл в другом — когда появится синхронизация с Mac, журнал операций
 * добавляется здесь одним файлом, а не правкой полусотни мест, куда иначе
 * разбрелись бы вызовы хранилища.
 */
export class MutationSink {
  private lamport: number

  constructor(
    private readonly clock: Clock,
    private readonly deviceId: DeviceId,
    initialLamport = 0,
  ) {
    this.lamport = initialLamport
  }

  /** Метаданные для новой или изменённой записи. */
  stamp(): SyncMeta {
    return {
      updatedAt: this.clock(),
      lamport: ++this.lamport,
      deviceId: this.deviceId,
      deletedAt: null,
    }
  }

  /** Надгробие вместо физического удаления: иначе запись воскреснет при слиянии. */
  tombstone(): SyncMeta {
    const now = this.clock()
    return { updatedAt: now, lamport: ++this.lamport, deviceId: this.deviceId, deletedAt: now }
  }

  /**
   * Подтягивает счётчик выше чужого значения. Вызывается при импорте и,
   * позже, при слиянии: логические часы обязаны обгонять всё увиденное,
   * иначе свежая локальная правка проиграет чужой уже устаревшей.
   */
  observe(foreignLamport: number): void {
    if (this.lamport <= foreignLamport) this.lamport = foreignLamport + 1
  }

  current(): number {
    return this.lamport
  }
}
