/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        midnight: {
          950: '#060b18',
          900: '#0a1224',
          850: '#0d1629',
          800: '#111c33',
          700: '#1b2a48',
          600: '#2a3b5f',
          500: '#41557d',
        },
        ink: { DEFAULT: '#e6edf7', secondary: '#b4c0d3', muted: '#8a9ab5' },
        accent: { DEFAULT: '#22d3ee', strong: '#67e8f9', teal: '#2dd4bf' },
      },
      fontFamily: { sans: ['system-ui', '-apple-system', '"Segoe UI"', 'Roboto', 'sans-serif'] },
    },
  },
  plugins: [],
};
