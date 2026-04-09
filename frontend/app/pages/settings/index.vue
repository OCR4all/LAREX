<script setup lang="ts">
import type { UserProfile, UpdateUserProfileRequest } from '~/types'
import { globalKey } from '@/utils/fetch-keys'
import { getAvatarInitials, resolveManagedProfileAvatarSrc } from '@/utils/avatar'

const toast = useToast()
const { resetTours } = useOnboarding()
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

const avatarFallback = computed(() => {
  return getAvatarInitials({
    firstName: profile.value?.firstName,
    lastName: profile.value?.lastName,
    username: profile.value?.username
  })
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
      lastName: form.lastName.trim() || undefined,
      avatar: resolveManagedProfileAvatarSrc(form.avatar) || undefined
    }

    await $fetch('/api/profile', {
      method: 'PUT',
      body: updateRequest
    })

    await refresh()

    try {
      await $fetch('/api/auth/refresh-profile', {
        method: 'POST'
      })
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
    const formData = new FormData()
    formData.append('file', file)

    const response = await $fetch<{ avatarUrl: string }>('/api/upload-proxy/profile/image', {
      method: 'POST',
      body: formData
    })

    form.avatar = resolveManagedProfileAvatarSrc(response.avatarUrl) || ''
    await refresh()

    try {
      await $fetch('/api/auth/refresh-profile', {
        method: 'POST'
      })
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
    await $fetch('/api/upload-proxy/profile/image', {
      method: 'DELETE'
    })

    form.avatar = ''
    await refresh()

    try {
      await $fetch('/api/auth/refresh-profile', {
        method: 'POST'
      })
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
      orientation="horizontal"
      class="mb-4"
    >
      <UButton
        v-if="!isEditing"
        data-tour="settings-profile-edit"
        variant="outline"
        icon="i-lucide-pencil"
        label="Edit Profile"
        color="primary"
        class="w-fit lg:ms-auto"
        @click="startEditing"
      />
      <div v-else class="flex gap-2 w-fit lg:ms-auto">
        <UButton
          label="Cancel"
          color="neutral"
          variant="outline"
          @click="cancelEditing"
        />
        <UButton
          label="Save"
          icon="i-lucide-save"
          variant="solid"
          :loading="isSaving"
          @click="saveProfile"
        />
      </div>
    </UPageCard>

    <UPageCard
      v-if="error"
      title="Error"
      variant="subtle"
      class="mb-4"
    >
      <p class="text-red-600">
        Failed to load profile: {{ error }}
      </p>
    </UPageCard>

    <UPageCard variant="subtle">
      <div v-if="pending" class="flex items-center justify-center p-8">
        <UButton loading variant="ghost" disabled>
          Loading profile...
        </UButton>
      </div>

      <div v-else-if="profile" class="space-y-6">
        <div class="flex items-center gap-4">
          <UAvatar
            :src="avatarSrc"
            :alt="displayName"
            size="xl"
            :fallback="avatarFallback"
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
            <div class="space-y-2">
              <label class="block text-sm font-medium">Profile Picture</label>
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
            </div>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium mb-1">First Name</label>
            <UInput
              v-if="isEditing"
              v-model="form.firstName"
              placeholder="Enter your first name"
            />
            <p v-else class="p-2 bg-neutral-50 rounded-sm border text-sm">
              {{ profile.firstName || 'Not set' }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium mb-1">Last Name</label>
            <UInput
              v-if="isEditing"
              v-model="form.lastName"
              placeholder="Enter your last name"
            />
            <p v-else class="p-2 bg-neutral-50 rounded-sm border text-sm">
              {{ profile.lastName || 'Not set' }}
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium mb-1">Username</label>
            <p class="p-2 bg-neutral-100 rounded-sm border text-sm text-muted">
              {{ profile.username }} (read-only)
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium mb-1">Email</label>
            <p class="p-2 bg-neutral-100 rounded-sm border text-sm text-muted">
              {{ profile.email || 'Not set' }} (read-only)
            </p>
          </div>
        </div>
      </div>
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
