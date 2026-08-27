<script>
export default {
  onLaunch() {
    // 应用启动
  },
  onShow() {
    // 应用展示
  },
  onHide() {
    // 应用隐藏
  },
}
</script>

<style lang="scss">
/* ========== 全局样式 ========== */
page {
  background-color: #f5f7fa;
  color: #303133;
  font-size: 28rpx;
  line-height: 1.5;
}

view,
text,
scroll-view,
image,
input,
textarea,
button {
  box-sizing: border-box;
}

/* 通用按钮重置 */
button::after {
  border: none;
}

/* 通用卡片 */
.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
}

/* 标题 */
.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16rpx;
}
</style>
