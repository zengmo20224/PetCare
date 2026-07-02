<template>
  <view v-if="!isWeixin" class="pc-bottom-nav">
    <view
      v-for="tab in tabs"
      :key="tab.pagePath"
      class="pc-bottom-nav__item"
      :class="{ 'pc-bottom-nav__item--active': currentPath === tab.pagePath }"
      @tap="onTabSwitch(tab.pagePath)"
    >
      <view class="pc-bottom-nav__icon">
        <wd-icon :name="tab.icon" size="20px" :color="currentPath === tab.pagePath ? '#00796B' : '#314D48'" />
      </view>
      <text class="pc-bottom-nav__text">{{ tab.text }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { openAllServices } from '@/utils/service-navigation'
import { isWeixinMiniProgram } from '@/utils/platform'

interface TabItem {
  pagePath: string
  text: string
  icon: string
}

const props = defineProps<{
  currentPath: string
}>()

// 图标使用 wot-design-uni 内置图标（语义匹配：首页/预约/社区/商品/我的）
const tabs: TabItem[] = [
  { pagePath: 'pages/home/index', text: '首页', icon: 'home' },
  { pagePath: 'pages/services/index', text: '预约', icon: 'calendar' },
  { pagePath: 'pages/community/index', text: '社区', icon: 'chat' },
  { pagePath: 'pages/products/index', text: '商品', icon: 'cart' },
  { pagePath: 'pages/profile/index', text: '我的', icon: 'user' },
]

const isWeixin = isWeixinMiniProgram()

function onTabSwitch(path: string) {
  if (props.currentPath === path) return
  if (path === 'pages/services/index') {
    openAllServices()
    return
  }
  uni.switchTab({ url: `/${path}` })
}

onMounted(() => {
  if (!isWeixin) {
    uni.hideTabBar({ animation: false })
  }
})
</script>

<style scoped>
.pc-bottom-nav {
  position: fixed;
  left: 50%;
  right: auto;
  bottom: 0;
  z-index: 900;
  display: flex;
  width: 100%;
  max-width: 480px;
  min-height: 64px;
  padding: 6px 10px 8px;
  border-top: 1px solid #E2E9E6;
  background: #FFFFFF;
  box-shadow: 0 -10px 30px rgba(25, 50, 46, 0.1);
  transform: translateX(-50%);
  box-sizing: border-box;
}

.pc-bottom-nav__item {
  flex: 1;
  min-width: 0;
  min-height: 50px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.pc-bottom-nav__icon {
  width: 28px;
  height: 28px;
  margin-bottom: 2px;
  border-radius: 14px;
  background: #F3F7F5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pc-bottom-nav__text {
  font-size: 11px;
  line-height: 1.2;
  color: #314D48;
}

.pc-bottom-nav__item--active .pc-bottom-nav__icon {
  background: #DFF2ED;
}

.pc-bottom-nav__item--active .pc-bottom-nav__text {
  color: #00796B;
}
</style>
