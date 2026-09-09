import { useState } from 'preact/hooks'
import type { Topic } from '../../domain/domain'
import { T } from '../labels'
import { count } from '../plural'

interface Props {
  topics: readonly Topic[]
  bitCount: (topicId: string) => number
  onAdd: (title: string) => Promise<void>
  onDelete: (id: string) => Promise<void>
}

export function TopicsScreen({ topics, bitCount, onAdd, onDelete }: Props) {
  const [text, setText] = useState('')
  const trimmed = text.trim()

  async function submit() {
    if (!trimmed) return
    await onAdd(trimmed)
    setText('')
  }

  return (
    <div class="scroll" data-screen="topics">
      <div class="card">
        <h2>{T.tabTopics}</h2>
        <div class="row">
          <input
            class="grow"
            type="text"
            data-testid="topic-input"
            placeholder={T.topicPlaceholder}
            value={text}
            onInput={(e) => setText((e.target as HTMLInputElement).value)}
            onKeyDown={(e) => { if (e.key === 'Enter') void submit() }}
          />
          <button class="btn" data-testid="topic-add" disabled={!trimmed} onClick={() => void submit()}>
            {T.topicAdd}
          </button>
        </div>
      </div>

      {topics.length === 0 ? (
        <p class="empty">{T.topicsEmpty}</p>
      ) : (
        <ul class="list" data-testid="topic-list">
          {topics.map((t) => (
            <li key={t.id} class="item" data-testid="topic-item">
              <div class="grow">
                <div class="title">{t.title}</div>
                <div class="sub">{count(bitCount(t.id), 'шутка', 'шутки', 'шуток')}</div>
              </div>
              <button class="btn danger" onClick={() => void onDelete(t.id)}>{T.topicDelete}</button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
