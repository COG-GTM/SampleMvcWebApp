/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: { sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'] },
      colors: { brand: { 50: '#eef6ff', 100: '#d9eaff', 500: '#2f6fed', 600: '#1f5ad6', 700: '#1a47a8' } },
    },
  },
  plugins: [],
};
