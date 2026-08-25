<script setup lang="ts">
import type { UserProfile, UpdateUserProfileRequest } from '~/types'

const toast = useToast()
const { resetTours } = useOnboarding()
const { uploadFormDataWithProgress } = useTrackedUpload()
const { fetch: refreshUserSession } = useUserSession()
const { invalidate: invalidateAvatarSource } = useManagedAvatarSources()
const isResettingTour = ref(false)

const handleResetTours = async () => {
  isResettingTour.value = true
  try {
    await resetTours()
    toast.add({
      title: 'Tours reset',
      description: 'Onboarding tours were reset and re-enabled.',
      color: 'success'
    })
  } catch (e) {
    console.error('Failed to reset tours:', e)
    toast.add({
      title: 'Reset failed',
      description: 'Failed to reset onboarding tours',
      color: 'error'
    })
  } finally {
    isResettingTour.value = false
  }
}

const { data: profile, pending, error, refresh } = await useFetch<UserProfile>('/api/profile', {
  key: globalKey('user', 'profile')
})

const isEditing = ref(false)
const isSaving = ref(false)
const isUploadingImage = ref(false)

const form = reactive({
  firstName: '',
  lastName: '',
  avatar: ''
})

const imageUploadRef = ref<HTMLInputElement>()

const extractErrorMessage = (error: unknown, fallback: string) => {
  if (typeof error === 'object' && error !== null) {
    const candidate = error as { data?: { message?: string, error?: string }, message?: string }
    return candidate.data?.message || candidate.data?.error || candidate.message || fallback
  }
  return fallback
}

const refreshSessionProfile = async () => {
  await $fetch('/api/auth/refresh-profile', {
    method: 'POST'
  })
  await refreshUserSession()
}

watchEffect(() => {
  if (profile.value) {
    form.firstName = profile.value.firstName || ''
    form.lastName = profile.value.lastName || ''
    form.avatar = resolveManagedProfileAvatarSrc(profile.value.avatar) || ''
  }
})

const displayName = computed(() => {
  if (profile.value?.firstName && profile.value?.lastName) {
    return `${profile.value.firstName} ${profile.value.lastName}`
  } else if (profile.value?.firstName) {
    return profile.value.firstName
  } else if (profile.value?.lastName) {
    return profile.value.lastName
  } else {
    return profile.value?.username || 'Unknown User'
  }
})

const avatarSrc = computed(() => {
  return resolveManagedProfileAvatarSrc(form.avatar || profile.value?.avatar)
})

const startEditing = () => {
  isEditing.value = true
}

const cancelEditing = () => {
  if (profile.value) {
    form.firstName = profile.value.firstName || ''
    form.lastName = profile.value.lastName || ''
    form.avatar = resolveManagedProfileAvatarSrc(profile.value.avatar) || ''
  }
  isEditing.value = false
}

const saveProfile = async () => {
  if (!profile.value) return

  isSaving.value = true
  try {
    const updateRequest: UpdateUserProfileRequest = {
      firstName: form.firstName.trim() || undefined,
      lastName: form.lastName.trim() || undefined
    }

    await $fetch('/api/profile', {
      method: 'PUT',
      body: updateRequest
    })

    await refresh()

    try {
      await refreshSessionProfile()
    } catch (error) {
      console.warn('Failed to refresh session data:', error)
    }

    toast.add({
      title: 'Profile updated',
      description: 'Your profile has been successfully updated',
      color: 'success'
    })

    isEditing.value = false
  } catch (error: unknown) {
    console.error('Failed to update profile:', error)
    toast.add({
      title: 'Failed to update profile',
      description: extractErrorMessage(error, 'An unexpected error occurred'),
      color: 'error'
    })
  } finally {
    isSaving.value = false
  }
}

const triggerImageUpload = () => {
  imageUploadRef.value?.click()
}

const handleImageUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]

  if (!file) return

  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    toast.add({
      title: 'Invalid file type',
      description: 'Please upload a JPEG, PNG, or WebP image',
      color: 'error'
    })
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    toast.add({
      title: 'File too large',
      description: 'Please upload an image smaller than 5MB',
      color: 'error'
    })
    return
  }

  isUploadingImage.value = true

  try {
    const previousAvatarSrc = avatarSrc.value
    const formData = new FormData()
    formData.append('file', file)

    const response = await uploadFormDataWithProgress<{ avatarUrl: string }>({
      title: 'Uploading profile image',
      workspaceId: 'user-profile',
      files: [{ file }],
      url: '/api/upload-proxy/profile/image',
      formData
    })

    invalidateAvatarSource(previousAvatarSrc)
    form.avatar = resolveManagedProfileAvatarSrc(response.avatarUrl) || ''
    await refresh()

    try {
      await refreshSessionProfile()
    } catch (error) {
      console.warn('Failed to refresh session data:', error)
    }

    toast.add({
      title: 'Image uploaded',
      description: 'Your profile image has been updated',
      color: 'success'
    })
  } catch (error: unknown) {
    console.error('Failed to upload image:', error)
    toast.add({
      title: 'Upload failed',
      description: extractErrorMessage(error, 'Failed to upload image'),
      color: 'error'
    })
  } finally {
    isUploadingImage.value = false
    if (input) input.value = ''
  }
}

const removeImage = async () => {
  if (!avatarSrc.value) return

  isUploadingImage.value = true

  try {
    const removedAvatarSrc = avatarSrc.value
    await $fetch('/api/upload-proxy/profile/image', {
      method: 'DELETE'
    })

    invalidateAvatarSource(removedAvatarSrc)
    form.avatar = ''
    await refresh()

    try {
      await refreshSessionProfile()
    } catch (error) {
      console.warn('Failed to refresh session data:', error)
    }

    toast.add({
      title: 'Image removed',
      description: 'Your profile image has been removed',
      color: 'success'
    })
  } catch (error: unknown) {
    console.error('Failed to remove image:', error)
    toast.add({
      title: 'Remove failed',
      description: 'Failed to remove image',
      color: 'error'
    })
  } finally {
    isUploadingImage.value = false
  }
}
</script>

<template>
  <div>
    <UPageCard
      data-tour="settings-profile-card"
      title="Profile"
      description="Manage your personal information and avatar."
      variant="subtle"
      class="mb-4"
    >
      <UTooltip v-if="profile && !isEditing" text="Edit profile">
        <UButton
          data-tour="settings-profile-edit"
          icon="i-lucide-pencil"
          color="primary"
          variant="ghost"
          size="sm"
          square
          aria-label="Edit profile"
          type="button"
          class="absolute end-4 top-4 sm:end-6 sm:top-6"
          @click="startEditing"
        />
      </UTooltip>

      <div v-if="error" class="text-error">
        Failed to load profile: {{ error }}
      </div>

      <div v-else-if="pending" class="flex items-center justify-center p-8">
        <UButton loading variant="ghost" disabled>
          Loading profile...
        </UButton>
      </div>

      <UForm
        v-else-if="profile"
        :state="form"
        class="space-y-6"
        @submit="saveProfile"
      >
        <div class="flex items-center gap-4">
          <AppAvatar
            :seed="profile.id"
            :src="avatarSrc"
            :alt="displayName"
            size="xl"
          />
          <div v-if="!isEditing">
            <h3 class="text-lg font-semibold">
              {{ displayName }}
            </h3>
            <p class="text-muted">
              {{ profile.email }}
            </p>
            <p class="text-muted text-sm">
              @{{ profile.username }}
            </p>
          </div>
          <div v-else class="flex-1">
            <UFormField label="Profile picture">
              <div class="flex gap-2">
                <UButton
                  color="neutral"
                  variant="outline"
                  :loading="isUploadingImage"
                  @click="triggerImageUpload"
                >
                  Upload Image
                </UButton>
                <UButton
                  v-if="avatarSrc"
                  color="error"
                  variant="outline"
                  :loading="isUploadingImage"
                  @click="removeImage"
                >
                  Remove
                </UButton>
              </div>
              <p class="text-xs text-muted">
                Upload a JPEG, PNG, or WebP image (max 5MB). Images will be cropped to square and resized to 400x400px.
              </p>
              <input
                ref="imageUploadRef"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                class="hidden"
                @change="handleImageUpload"
              >
            </UFormField>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <UFormField label="First name">
            <UInput
              v-model="form.firstName"
              placeholder="Enter your first name"
              :disabled="!isEditing || isSaving"
            />
          </UFormField>

          <UFormField label="Last name">
            <UInput
              v-model="form.lastName"
              placeholder="Enter your last name"
              :disabled="!isEditing || isSaving"
            />
          </UFormField>

          <UFormField label="Username" hint="Read-only">
            <UInput :model-value="profile.username" readonly />
          </UFormField>

          <UFormField label="Email" hint="Read-only">
            <UInput :model-value="profile.email || 'Not set'" readonly />
          </UFormField>
        </div>

        <div v-if="isEditing" class="flex justify-end gap-2 border-t border-default pt-4">
          <UButton
            label="Cancel"
            color="neutral"
            variant="outline"
            type="button"
            @click="cancelEditing"
          />
          <UButton
            label="Save"
            icon="i-lucide-save"
            variant="solid"
            type="submit"
            :loading="isSaving"
          />
        </div>
      </UForm>
    </UPageCard>

    <UPageCard
      data-tour="settings-tour-reset"
      title="Onboarding Tour"
      description="Reset the interactive guided tours to see them again."
      variant="subtle"
      orientation="horizontal"
      class="mt-4"
    >
      <UButton
        variant="outline"
        icon="i-lucide-route"
        label="Reset & Start Tour"
        color="primary"
        class="w-fit lg:ms-auto"
        :loading="isResettingTour"
        @click="handleResetTours"
      />
    </UPageCard>
  </div>
</template>
