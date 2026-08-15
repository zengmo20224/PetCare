<template>
  <view class="pc-page ai-status">
    <PcBlockedFeature title="智能助手暂未开放" reason="AI 能力正在开发中（D-008），敬请期待" />

    <!-- Wot Design Uni 接入验证 demo（H5 only，不影响小程序契约） -->
    <view class="wot-demo">
      <view class="wot-demo__title">Wot Design Uni 已接入</view>
      <view class="wot-demo__row">
        <wd-button type="primary">主要按钮</wd-button>
        <wd-button type="success">成功按钮</wd-button>
        <wd-button type="info">信息按钮</wd-button>
      </view>
      <view class="wot-demo__row">
        <wd-tag type="primary">主要标签</wd-tag>
        <wd-tag type="warning">警告标签</wd-tag>
        <wd-tag type="danger">危险标签</wd-tag>
      </view>
      <wd-icon name="check" size="20px" color="#11796F" />
    </view>
  </view>
</template>

<script setup lang="ts">
import PcBlockedFeature from '@/components/PcBlockedFeature.vue'
</script>

<style scoped>
.ai-status {
  padding: 40rpx;
  padding-top: 120rpx;
}

.wot-demo {
  margin-top: 56rpx;
  padding: 40rpx;
  background: var(--pc-user-surface);
  border-radius: 32rpx;
  box-shadow: 0 8px 24px rgba(25, 50, 46, 0.08);
}

.wot-demo__title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--pc-user-ink);
  margin-bottom: 32rpx;
}

.wot-demo__row {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-bottom: 32rpx;
  flex-wrap: wrap;
}
</style>
