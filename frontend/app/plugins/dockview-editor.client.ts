import editor from '~/components/editor/index.vue'
import editorPanel from '~/components/editor/dockview/editor-panel.vue'
import textPanel from '~/components/editor/dockview/text-panel.vue'
import editorTab from '~/components/editor/dockview/editor-tab.vue'
import projectPanel from '~/components/editor/dockview/project-panel.vue'
import projectTab from '~/components/editor/dockview/project-tab.vue'
import editorTabGroupMaximize from '~/components/editor/tab-group-maxizimize-button.vue'

export default defineNuxtPlugin((nuxtApp) => {
  nuxtApp.vueApp.component('EditorDockviewEditor', editor)
  nuxtApp.vueApp.component('EditorDockviewEditorPanel', editorPanel)
  nuxtApp.vueApp.component('EditorDockviewDefaultPanel', editorPanel)
  nuxtApp.vueApp.component('EditorDockviewTab', editorTab)
  nuxtApp.vueApp.component('EditorDockviewProjectPanel', projectPanel)
  nuxtApp.vueApp.component('EditorDockviewProjectTab', projectTab)

  nuxtApp.vueApp.component('EditorDockviewTextPanel', textPanel)
  nuxtApp.vueApp.component('EditorDockviewDefaultTextPanel', textPanel)
  nuxtApp.vueApp.component('EditorDockviewTabGroupMaximizeButton', editorTabGroupMaximize)
})
