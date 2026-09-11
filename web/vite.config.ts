import { execSync } from 'node:child_process'
import { defineConfig } from 'vite'
import preact from '@preact/preset-vite'

/**
 * Отпечаток сборки.
 *
 * Приложение показывает его в настройках, чтобы вопрос «а эта версия
 * опубликовалась?» проверялся глазами за две секунды, а не принимался на
 * веру. На CI коммит приходит из окружения, локально — из git.
 */
function buildStamp() {
  const sha =
    process.env.GITHUB_SHA?.slice(0, 7) ??
    (() => {
      try {
        return execSync('git rev-parse --short HEAD').toString().trim()
      } catch {
        return 'локальная'
      }
    })()
  return { sha, at: new Date().toISOString() }
}

// base задан относительным: приложение публикуется по адресу вида
// https://<user>.github.io/<repo>/, и абсолютные пути ресурсов там ломаются.
export default defineConfig({
  base: './',
  plugins: [preact()],
  define: { __BUILD__: JSON.stringify(buildStamp()) },
  build: { outDir: 'dist', sourcemap: true },
})
