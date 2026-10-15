import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  // strictPort: fail instead of moving to another port, which the backend would reject (CORS)
  server: { port: 5174, strictPort: true },
})
