/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,ts}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Inter"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
      fontSize: {
        xs:    ['0.70rem', { lineHeight: '1rem' }],
        sm:    ['0.78rem', { lineHeight: '1.15rem' }],
        base:  ['0.85rem', { lineHeight: '1.35rem' }],
        lg:    ['0.95rem', { lineHeight: '1.4rem' }],
        xl:    ['1.05rem', { lineHeight: '1.5rem' }],
        '2xl': ['1.2rem',  { lineHeight: '1.6rem' }],
        '3xl': ['1.4rem',  { lineHeight: '1.75rem' }],
      },
    },
  },
  plugins: [require("daisyui")],
  daisyui: {
    themes: [
      {
        ssvlight: {
          // Primary — dark navy (the "+ New" button, active nav)
          "primary":          "#121932",
          "primary-content":  "#ffffff",

          // Secondary — teal/mint green (charts, highlights, links)
          "secondary":        "#08EEB1",
          "secondary-content":"#ffffff",

          // Accent — soft teal for interactive highlights
          "accent":           "#0d9488",
          "accent-content":   "#ffffff",

          // Neutral — mid grey for tags and subtle elements
          "neutral":          "#6b7280",
          "neutral-content":  "#ffffff",

          // Base — light grey page background matching the app
          "base-100":         "#F6F6F6",   // page background
          "base-200":         "#F6F6F6",   // sidebar, card headers
          "base-300":         "#EEEDEE",   // borders, dividers
          "base-content":     "#121932",   // primary text

          // Semantic
          "info":             "#0ea5e9",
          "info-content":     "#ffffff",
          "success":          "#00D577",   // same teal as secondary
          "success-content":  "#ffffff",
          "warning":          "#f59e0b",
          "warning-content":  "#ffffff",
          "error":            "#ef4444",
          "error-content":    "#ffffff",

          // Shape
          "--rounded-box":    "0.5rem",
          "--rounded-btn":    "0.375rem",
          "--rounded-badge":  "0.25rem",
          "--tab-radius":     "0.375rem",
          "--border-btn":     "1px",
        },
      },
    ],
    defaultTheme: "ssvlight",
    base: true,
    styled: true,
    utils: true,
    logs: false,
  },
};
