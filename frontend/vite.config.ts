import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Proxy /api and /data to the backend Spring Boot server (:8084) during dev,
// so the app makes same-origin calls and image src's are just `/data/...`.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5177,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8084', changeOrigin: true },
      '/data': { target: 'http://127.0.0.1:8084', changeOrigin: true },
    },
  },
})
