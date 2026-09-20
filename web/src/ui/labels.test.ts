import { describe, expect, it } from 'vitest'
import { ROLE_LABEL, T, TECHNIQUE_LABEL } from './labels'

/**
 * Словарь интерфейса — жаргон русской сцены.
 *
 * Тест существует из-за конкретной ошибки: основной панч был подписан словом
 * «добивка», а «добивка» на сцене означает дополнительный панч после
 * основного (англ. tag). Настоящие добивки при этом назывались «тегами».
 * Термины были перепутаны местами, и для человека из сцены это читалось
 * как чужеродный текст.
 *
 * Здесь закреплены только те слова, путаница в которых меняет смысл.
 */
describe('словарь интерфейса', () => {
  it('основная смешная строчка называется панчлайном, а не добивкой', () => {
    expect(T.fieldPunch).toBe('Панчлайн')
    expect(T.fieldPunch.toLowerCase()).not.toContain('добивк')
  })

  it('подводка называется сетапом', () => {
    expect(T.fieldSetup).toBe('Сетап')
  })

  it('роли в сете названы так, как их называют за кулисами', () => {
    expect(ROLE_LABEL.OPENER).toBe('опенер')
    expect(ROLE_LABEL.CLOSER).toBe('клоузер')
    expect(ROLE_LABEL.CALLBACK).toBe('каллбэк')
  })

  it('техники панча названы жаргоном, а не переводом', () => {
    expect(TECHNIQUE_LABEL.MIX).toBe('микс')
    expect(TECHNIQUE_LABEL.TURN).toBe('твист')
  })

  it('длительность везде называется таймингом, а не хронометражем', () => {
    for (const [key, value] of Object.entries(T)) {
      if (typeof value !== 'string') continue
      expect(value.toLowerCase(), `в «${key}» осталось старое слово`).not.toContain('хронометраж')
    }
  })

  it('слово «добивка» не используется для основного панча нигде в подписях', () => {
    // Само слово остаётся допустимым — но только про теги, а не про панч.
    expect(T.fieldTags).toBe('Теги')
    expect(T.setCandidatesHint).toContain('панчлайн')
  })
})
