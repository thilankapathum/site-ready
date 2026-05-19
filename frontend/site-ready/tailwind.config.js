/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,ts}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"DM Sans"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
    },
  },
  plugins: [require("daisyui")],
  daisyui: {
    themes: [
      {
        ssvlight: {
          "primary":          "#0f4c81",   // Deep telecom blue
          "primary-content":  "#ffffff",
          "secondary":        "#1a6b4a",   // Success green
          "secondary-content":"#ffffff",
          "accent":           "#d97706",   // Amber for warnings
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
