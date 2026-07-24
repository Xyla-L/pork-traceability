<template>
  <div class="admin-layout">
    <SidebarMenu />
    <div class="main-content">
      <TopBar />
      <div class="page-content"><router-view /></div>
    </div>
  </div>
</template>

<script setup>
import SidebarMenu from './SidebarMenu.vue'
import TopBar from './TopBar.vue'
</script>

<style lang="scss" scoped>
.admin-layout { display: flex; height: 100vh; overflow: hidden; }
.main-content { flex: 1; display: flex; flex-direction: column; background-color: #f5f7fa; }
.page-content { flex: 1; overflow-y: auto; padding: 20px; }
</style>