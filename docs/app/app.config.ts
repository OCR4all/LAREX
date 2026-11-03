export default defineAppConfig({
  seo: {
    title: 'LAREX Documentation',
    titleTemplate: '%s - LAREX Documentation',
    description: 'Documentation for LAREX, the Layout Analysis and Recognition application.'
  },
  header: {
    title: 'LAREX Docs'
  },
  github: {
    url: 'https://github.com/OCR4all/larex',
    branch: 'main',
    rootDir: 'docs'
  },
  toc: {
    title: 'On this page'
  },
  ui: {
    colors: {
      primary: 'navy',
      neutral: 'smoke'
    }
  }
})
