<template>
  <router-view />
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
</script>

<style lang="scss">
#app {
  width: 100%;
  height: 100vh;
}
</style>
