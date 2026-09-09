import { T } from '../labels'

interface Props {
  bitCount: number
  topicCount: number
  persistent: boolean
  lastBackupAt: number | null
  onExport: () => void
  onImport: (file: File) => Promise<void>
}

/**
 * Архив. Один файл, который открывается на телефоне без распаковки.
 * Прошлая версия отдавала zip с тремя файлами внутри — так было честнее
 * к формату, но человеку на телефоне непонятно, что с этим делать.
 */
export function BackupScreen({ bitCount, topicCount, persistent, lastBackupAt, onExport, onImport }: Props) {
  const when = lastBackupAt
    ? new Date(lastBackupAt).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })
    : T.backupNever

  return (
    <div class="scroll" data-screen="backup">
      <div class="card">
        <h2>{T.backupTitle}</h2>
        <p class="hint" style="margin-top:0">{T.backupExplain}</p>
        <p class="hint">
          {T.backupCounts} {bitCount} {T.backupBits}, {topicCount} {T.backupTopics}
        </p>
        <p class="hint">{T.backupLast} {when}</p>
        <p class="hint">{persistent ? T.backupStorageOk : T.backupStorageWeak}</p>
        <button class="btn" data-testid="backup-export" style="margin-top:10px" onClick={onExport}>
          {T.backupNow}
        </button>
      </div>

      <div class="card">
        <h2>{T.backupRestore}</h2>
        <input
          type="file" accept="application/json,.json" data-testid="backup-import"
          onChange={(e) => {
            const f = (e.target as HTMLInputElement).files?.[0]
            if (f) void onImport(f)
          }}
        />
      </div>
    </div>
  )
}
