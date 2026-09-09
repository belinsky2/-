import { useState } from 'preact/hooks'
import type { Bit } from '../../domain/domain'
import { STATUS_LABEL, T, UNDO_LABEL } from '../labels'

interface Props {
  bits: readonly Bit[]
  topicTitle: (id: string | null) => string
  onAdd: (title: string) => Promise<void>
  onOpen: (id: string) => void
}

/**
 * Захват. Одно поле и одна кнопка: если записать мысль дороже, чем её забыть,
 * её и забудут.
 */
export function InboxScreen({ bits, topicTitle, onAdd, onOpen }: Props) {
  const [text, setText] = useState('')
  const trimmed = text.trim()

  async function submit() {
    if (!trimmed) return
    await onAdd(trimmed)
    setText('')
  }

  return (
    <div class="scroll" data-screen="inbox">
      <div class="card">
        <h2>{T.tabInbox}</h2>
        <div class="row">
          <input
            class="grow"
            type="text"
            data-testid="capture-input"
            placeholder={T.capturePlaceholder}
            value={text}
            onInput={(e) => setText((e.target as HTMLInputElement).value)}
            onKeyDown={(e) => { if (e.key === 'Enter') void submit() }}
          />
          <button class="btn" data-testid="capture-add" disabled={!trimmed} onClick={() => void submit()}>
            {T.captureAdd}
          </button>
        </div>
        <p class="hint">{T.captureHint}</p>
      </div>

      {bits.length === 0 ? (
        <p class="empty" data-testid="inbox-empty">{T.inboxEmpty}</p>
      ) : (
        <ul class="list" data-testid="bit-list">
          {bits.map((b) => (
            <li key={b.id} class="item" data-testid="bit-item" onClick={() => onOpen(b.id)}>
              <div class="grow">
                <div class="title">{b.title}</div>
                <div class="sub">{topicTitle(b.topicId)}</div>
              </div>
              <span class="badge">{STATUS_LABEL[b.status]}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export const inboxUndoLabels = UNDO_LABEL
