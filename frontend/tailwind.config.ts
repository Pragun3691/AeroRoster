import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: { extend: { colors: { navy: '#10243A', 'navy-2': '#183B56', teal: '#16A6A0', canvas: '#F3F6FA', ink: '#172B3A', muted: '#64748B', line: '#E2E8F0' } } },
  plugins: [],
} satisfies Config
