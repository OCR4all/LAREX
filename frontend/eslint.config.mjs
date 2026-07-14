// @ts-check
import withNuxt from './.nuxt/eslint.config.mjs'

export default withNuxt({
  rules: {
    // Existing editor, WebGL, and API-boundary code still contains intentionally untyped
    // integration values. Typecheck remains mandatory; tightening these types is separate work.
    '@typescript-eslint/no-explicit-any': 'off',
    // Dynamic cache/record eviction is intentional throughout the editor and lookup composables.
    '@typescript-eslint/no-dynamic-delete': 'off',
    'vue/no-multiple-template-root': 'off',
    'vue/max-attributes-per-line': ['error', { singleline: 3 }]
  }
})
