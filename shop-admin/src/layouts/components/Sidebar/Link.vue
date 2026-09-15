<template>
    <component :is="type" v-bind="linkProps" class="app-link">
      <slot />
    </component>
  </template>
  
  <script setup lang="ts">
  import { computed } from 'vue'
  import type { RouteLocationRaw } from 'vue-router'
  import validate from '@/utils/validate'
  
  interface Props {
    to: string | RouteLocationRaw
  }

  const props = defineProps<Props>()

  const isExternalLink = computed(() => {
    if (typeof props.to === 'string') {
      return validate.isExternal(props.to)
    }
    return false
  })
  
  const type = computed(() => isExternalLink.value ? 'a' : 'router-link')
  
  const linkProps = computed(() => {
    if (isExternalLink.value && typeof props.to === 'string') {
      return {
        href: props.to,
        target: '_blank',
        rel: 'noopener'
      }
    }
    return {
      to: props.to
    }
  })
  </script>
  
  <style scoped>
  .app-link {
    text-decoration: none;
    color: inherit;
  }
  
  .app-link:hover,
  .app-link:focus,
  .app-link:active {
    text-decoration: none;
    color: inherit;
  }
  </style> 