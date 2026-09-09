import { render } from 'preact'
import { App } from './ui/app'
import './ui/styles.css'

render(<App />, document.getElementById('root')!)

// Регистрация после загрузки: на первом открытии важнее показать экран,
// чем встать в оффлайн-режим.
if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
  addEventListener('load', () => {
    // Относительно страницы, а не относительно этого модуля: собранный скрипт
    // лежит в assets/, и import.meta.url увёл бы регистрацию туда.
    void navigator.serviceWorker.register(new URL('sw.js', document.baseURI), { scope: './' })
  })
}
