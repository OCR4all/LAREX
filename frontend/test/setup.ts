import { defineStore } from 'pinia'
import {
  computed,
  nextTick,
  reactive,
  readonly,
  ref,
  shallowRef,
  toValue,
  triggerRef,
  watch
} from 'vue'

// Production receives these APIs through Nuxt auto-imports. Keep the unit-test
// runtime lightweight while exposing the same stable Vue/Pinia primitives.
Object.assign(globalThis, {
  computed,
  defineStore,
  nextTick,
  reactive,
  readonly,
  ref,
  shallowRef,
  toValue,
  triggerRef,
  watch
})
