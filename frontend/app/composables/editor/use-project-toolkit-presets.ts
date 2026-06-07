import type { ProjectToolkitSettings } from '@/stores/editor/editor.document.store'

export type ProjectToolkitPresetPatch = Partial<{
  codecId: string | null
  labelSetId: string | null
  dictionaryId: string | null
  tagSetId: string | null
  normalizationProfileId: string | null
  validationRulesetId: string | null
  virtualKeyboardId: string | null
  allowCodecOverride: boolean
  allowDictionaryOverride: boolean
  allowVirtualKeyboardOverride: boolean
  allowLabelSetOverride: boolean
  allowTagSetOverride: boolean
  allowNormalizationProfileOverride: boolean
  allowValidationRulesetOverride: boolean
}>

type ProjectToolkitProject = ProjectToolkitSettings & {
  id: string
}

export function toProjectToolkitSettings(project: Partial<ProjectToolkitProject>): ProjectToolkitSettings {
  return {
    codecId: project.codecId ?? null,
    labelSetId: project.labelSetId ?? null,
    dictionaryId: project.dictionaryId ?? null,
    tagSetId: project.tagSetId ?? null,
    normalizationProfileId: project.normalizationProfileId ?? null,
    validationRulesetId: project.validationRulesetId ?? null,
    virtualKeyboardId: project.virtualKeyboardId ?? null,
    allowCodecOverride: project.allowCodecOverride !== false,
    allowDictionaryOverride: project.allowDictionaryOverride !== false,
    allowVirtualKeyboardOverride: project.allowVirtualKeyboardOverride !== false,
    allowLabelSetOverride: project.allowLabelSetOverride !== false,
    allowTagSetOverride: project.allowTagSetOverride !== false,
    allowNormalizationProfileOverride: project.allowNormalizationProfileOverride !== false,
    allowValidationRulesetOverride: project.allowValidationRulesetOverride !== false
  }
}

export function useProjectToolkitPresets() {
  const { refreshProjectCaches } = useDataRefresh()

  async function patchProjectToolkitPresets(
    workspaceId: string,
    projectId: string,
    patch: ProjectToolkitPresetPatch
  ): Promise<ProjectToolkitProject> {
    const current = await $fetch<ProjectToolkitProject>(`/api/workspaces/${workspaceId}/projects/${projectId}`)
    const body: ProjectToolkitSettings = {
      ...toProjectToolkitSettings(current),
      ...patch
    }
    const updated = await $fetch<ProjectToolkitProject>(`/api/workspaces/${workspaceId}/projects/${projectId}/toolkit-presets`, {
      method: 'PATCH',
      body
    })
    await refreshProjectCaches(workspaceId, projectId)
    return updated
  }

  return {
    patchProjectToolkitPresets
  }
}
