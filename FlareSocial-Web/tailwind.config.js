/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: "#6C5CE7",
        secondary: "#E84393",
        background: "#FFFFFF",
        surface: "#F8F9FA",
        textSecondary: "#636E72",
        flarePink: "#FF007F",
        flareBlue: "#00F2FE",
        flarePurple: "#7928CA",
      },
    },
  },
  plugins: [],
}
