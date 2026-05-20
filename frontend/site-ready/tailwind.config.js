/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,ts}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Inter"', 'sans-serif'],
        // sans: ['"DM Sans"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
      fontSize: {
        xs: ['0.70rem', {lineHeight: '1rem'}],
        sm: ['0.78rem', {lineHeight: '1.15rem'}],
        base: ['0.85rem', {lineHeight: '1.35rem'}],
        lg: ['0.95rem', {lineHeight: '1.4rem'}],
        xl: ['1.05rem', {lineHeight: '1.5rem'}],
        '2xl': ['1.2rem', {lineHeight: '1.6rem'}],
        '3xl': ['1.4rem', {lineHeight: '1.75rem'}],
      },
    },
  },
  plugins: [require("daisyui")],
  daisyui: {
    themes: [
      {
        ssvlight: {
          "primary":          "#0f4c81",
          "primary-content":  "#ffffff",
          "secondary":        "#1a6b4a",
          "secondary-content":"#ffffff",
          "accent":           "#d97706",
          "accent-content":   "#ffffff",
          "neutral":          "#2a2a35",
          "neutral-content":  "#ffffff",
          "base-100":         "#f8f9fb",
          "base-200":         "#eef0f4",
          "base-300":         "#dde1e8",
          "base-content":     "#1a1c22",
          "info":             "#0284c7",
          "success":          "#16a34a",
          "warning":          "#d97706",
          "error":            "#dc2626",
          "--rounded-box":    "0.5rem",
          "--rounded-btn":    "0.375rem",
          "--rounded-badge":  "0.25rem",
          "--tab-radius":     "0.375rem",
        },
      },
      "dark",
    ],
    defaultTheme: "ssvlight",
    darkTheme: "dark",
    base: true,
    styled: true,
    utils: true,
    logs: false,
  },
};
