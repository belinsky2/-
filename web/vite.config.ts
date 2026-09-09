import { defineConfig } from 'vite'
import preact from '@preact/preset-vite'

// base задан относительным: приложение публикуется по адресу вида
// https://<user>.github.io/<repo>/, и абсолютные пути ресурсов там ломаются.
export default defineConfig({
  base: './',
  plugins: [preact()],
  build: { outDir: 'dist', sourcemap: true },
})
