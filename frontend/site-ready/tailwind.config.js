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
          "primary":          "#1a273a",
          "primary-content":  "#eef2f6",

          // Secondary — teal/mint green (charts, highlights, links)
          "secondary":        "#135bf9",
          "secondary-content":"#eff6ff",

          // Accent — soft teal for interactive highlights
          "accent":           "#1a368b",
          "accent-content":   "#eff6ff",

          // Neutral — mid grey for tags and subtle elements
          "neutral":          "#61738d",
          "neutral-content":  "#ffffff",

          // Base — light grey page background matching the app
          "base-100":         "#f8f8f8",   // page background
          "base-200":         "#f5f5f4",   // sidebar, card headers
          "base-300":         "#e6e4e3",   // borders, dividers
          "base-content":     "#1a273a",   // primary text

          // Semantic
          "info":             "#2a7eff",
          "info-content":     "#ffffff",
          "success":          "#00c850",   // same teal as secondary
          "success-content":  "#ffffff",
          "warning":          "#fa9700",
          "warning-content":  "#ffffff",
          "error":            "#f82834",
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
