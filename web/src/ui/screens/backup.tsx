import { useState } from 'preact/hooks'
import { HELP } from '../help'
import { T } from '../labels'
import { count } from '../plural'

interface Props {
  bitCount: number
  topicCount: number
  persistent: boolean
  lastBackupAt: number | null
  onExport: () => void
  onImport: (file: File) => Promise<void>
  onExportMarkdown: () => void
}

/**
 * Архив. Один файл, который открывается на телефоне без распаковки.
 * Прошлая версия отдавала zip с тремя файлами внутри — так было честнее
 * к формату, но человеку на телефоне непонятно, что с этим делать.
 */
export function BackupScreen(
  { bitCount, topicCount, persistent, lastBackupAt, onExport, onImport, onExportMarkdown }: Props,
) {
  const [chosen, setChosen] = useState<string | null>(null)

  const when = lastBackupAt
    ? new Date(lastBackupAt).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })
    : T.backupNever

  return (
    <div class="scroll" data-screen="backup">
      {/* Общая карта приложения. В настройках, потому что сюда заходят,
          когда что-то ищут, а не когда работают над шуткой. */}
      <div class="card" data-testid="guide">
        <h2>{HELP.backup!.title}</h2>
        {HELP.backup!.lines.map((line, i) => (
          <p key={i} class="hint" style={i === 0 ? 'margin-top:0' : ''}>{line}</p>
        ))}
      </div>

      <div class="card">
        <h2>{T.backupTitle}</h2>
        <p class="hint" style="margin-top:0">{T.backupExplain}</p>
        <p class="hint">
          {T.backupCounts} {count(bitCount, 'шутка', 'шутки', 'шуток')},{' '}
          {count(topicCount, 'тема', 'темы', 'тем')}
        </p>
        <p class="hint">{T.backupLast} {when}</p>
        <p class="hint">{persistent ? T.backupStorageOk : T.backupStorageWeak}</p>
        <button class="btn" data-testid="backup-export" style="margin-top:10px" onClick={onExport}>
          {T.backupNow}
        </button>
      </div>

      <div class="card">
        <h2>{T.backupRestore}</h2>
        {/* Родная кнопка выбора файла подписана по-английски и не поддаётся
            оформлению — прячем её и нажимаем через подпись. */}
        <label class="btn" style="display:inline-block">
          {T.backupChooseFile}
          <input
            type="file" accept="application/json,.json" data-testid="backup-import" hidden
            onChange={(e) => {
              const f = (e.target as HTMLInputElement).files?.[0]
              if (!f) return
              setChosen(f.name)
              void onImport(f)
            }}
          />
        </label>
        <p class="hint">{chosen ?? T.backupNoFile}</p>
      </div>

      {/* Версия сборки. Нужна ровно для одного: чтобы проверить, дошла ли до
          устройства та версия, о которой шла речь, — не полагаясь на слова. */}
      <div class="card" data-testid="version">
        <h2>{T.versionTitle}</h2>
        <p class="hint" style="margin-top:0">
          {new Date(__BUILD__.at).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })}
          {' · '}
          <code>{__BUILD__.sha}</code>
        </p>
        <p class="hint">{T.versionHint}</p>
      </div>

      <div class="card">
        <h2>{T.exportTitle}</h2>
        {/* Архив — для возврата в приложение, Markdown — чтобы читать глазами
            и открыть где угодно через десять лет. Это разные задачи. */}
        <button class="btn" data-testid="export-md" onClick={onExportMarkdown}>{T.exportDo}</button>
      </div>
    </div>
  )
}
