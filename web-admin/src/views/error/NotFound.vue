<template>
  <div class="error-page">
    <el-result icon="error" title="404" sub-title="抱歉，您访问的页面不存在">
      <template #extra>
        <el-button type="primary" @click="$router.push('/admin/dashboard')">
          返回工作台
        </el-button>
        <el-button @click="$router.go(-1)">返回上一页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style lang="scss" scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #f0f2f5;
}
</style>
