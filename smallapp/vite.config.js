import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // The backend only allows 5173 (customer) and 5174 (admin). strictPort stops
  // Vite from silently moving to another port when this one is already taken.
  server: { port: 5173, strictPort: true },
})
