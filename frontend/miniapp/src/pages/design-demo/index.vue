<template>
  <view class="demo-page">
    <view class="demo-header">
      <view class="demo-header__copy">
        <text class="demo-header__brand">萌宠家园</text>
        <view class="demo-header__location">
          <wd-icon name="location" size="14px" color="#6F7D79" />
          <text>上海徐汇店</text>
        </view>
      </view>
      <wd-tag type="success" plain round>
        <view class="demo-status">
          <view class="demo-status__dot" />
          <text>营业中</text>
        </view>
      </wd-tag>
    </view>

    <view class="care-hero">
      <view class="care-hero__topline">
        <view class="care-hero__pet">
          <wd-icon name="heart-filled" size="18px" color="#11796F" />
        </view>
        <text class="care-hero__eyebrow">团子的日常照护</text>
      </view>
      <text class="care-hero__title">给毛孩子安排一次舒服的洗护</text>
      <text class="care-hero__summary">专业洗护 · 过程可沟通 · 到店与上门可选</text>
      <wd-button
        class="care-hero__action"
        type="primary"
        size="large"
        icon="calendar"
        block
        :round="false"
        custom-style="min-height: 88rpx; border-radius: 14rpx; background: #ffffff; color: #174f49; border: 0;"
        @click="showDemoFeedback('预约服务')"
      >
        预约服务
      </wd-button>
    </view>

    <view class="notice-row" @tap="showDemoFeedback('门店公告')">
      <view class="notice-row__icon">
        <wd-icon name="notification" size="19px" color="#11796F" />
      </view>
      <view class="notice-row__copy">
        <text class="notice-row__label">门店提醒</text>
        <text class="notice-row__text">周末预约较满，建议提前 1 天安排</text>
      </view>
      <wd-icon name="arrow-right" size="16px" color="#9AA5A2" />
    </view>

    <view class="demo-section">
      <view class="section-heading">
        <text class="section-heading__title">常用服务</text>
        <text class="section-heading__link" @tap="showDemoFeedback('全部服务')">查看全部</text>
      </view>
      <view class="service-grid">
        <view
          v-for="service in services"
          :key="service.name"
          class="service-item"
          @tap="selectService(service.name)"
        >
          <view class="service-item__icon" :class="`service-item__icon--${service.tone}`">
            <wd-icon :name="service.icon" size="25px" :color="service.color" />
          </view>
          <text class="service-item__name">{{ service.name }}</text>
          <text class="service-item__hint">{{ service.hint }}</text>
        </view>
      </view>
    </view>

    <view class="demo-section">
      <view class="section-heading">
        <text class="section-heading__title">近期安排</text>
      </view>
      <view class="appointment-list">
        <view class="appointment-row" @tap="showDemoFeedback('预约详情')">
          <view class="appointment-row__date">
            <text class="appointment-row__day">03</text>
            <text class="appointment-row__month">8 月</text>
          </view>
          <view class="appointment-row__body">
            <view class="appointment-row__title-line">
              <text class="appointment-row__title">团子 · 基础洗护</text>
              <wd-tag type="warning" plain size="small">待到店</wd-tag>
            </view>
            <text class="appointment-row__meta">周一 14:30 · 约 60 分钟</text>
          </view>
          <wd-icon name="arrow-right" size="16px" color="#9AA5A2" />
        </view>
      </view>
    </view>

    <view class="demo-section demo-section--last">
      <view class="section-heading">
        <text class="section-heading__title">养宠灵感</text>
        <text class="section-heading__link" @tap="showDemoFeedback('社区')">逛社区</text>
      </view>
      <view class="story-row" @tap="showDemoFeedback('社区内容')">
        <view class="story-row__visual">
          <wd-icon name="photo" size="30px" color="#D27555" />
        </view>
        <view class="story-row__body">
          <view class="story-row__tag">
            <wd-icon name="star" size="13px" color="#D27555" />
            <text>本周精选</text>
          </view>
          <text class="story-row__title">让狗狗慢慢爱上洗澡的 4 个小方法</text>
          <view class="story-row__meta">
            <view class="story-row__stat">
              <wd-icon name="heart" size="14px" color="#64736F" />
              <text>32</text>
            </view>
            <view class="story-row__stat">
              <wd-icon name="chat" size="14px" color="#64736F" />
              <text>5</text>
            </view>
          </view>
        </view>
        <wd-icon name="arrow-right" size="16px" color="#9AA5A2" />
      </view>
    </view>

    <view class="demo-tabbar" :style="tabbarStyle">
      <view
        v-for="item in demoTabs"
        :key="item.name"
        class="demo-tabbar__item"
        :class="{ 'demo-tabbar__item--active': activeTab === item.name }"
        @tap="selectTab(item.name)"
      >
        <wd-icon
          :name="item.icon"
          size="20px"
          :color="activeTab === item.name ? '#11796F' : '#71817D'"
        />
        <text>{{ item.title }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'

type DemoService = {
  name: string
  hint: string
  icon: 'service' | 'edit-outline' | 'home' | 'heart'
  color: string
  tone: 'mint' | 'amber' | 'blue' | 'rose'
}

const services: readonly DemoService[] = [
  { name: '日常洗护', hint: '¥58 起', icon: 'service', color: '#11796F', tone: 'mint' },
  { name: '精致美容', hint: '¥98 起', icon: 'edit-outline', color: '#B86D16', tone: 'amber' },
  { name: '上门照护', hint: '¥120 起', icon: 'home', color: '#426DA9', tone: 'blue' },
  { name: '安心寄养', hint: '¥80 / 天', icon: 'heart', color: '#B45772', tone: 'rose' },
]

const demoTabs = [
  { name: 'home', title: '首页', icon: 'home' },
  { name: 'booking', title: '预约', icon: 'calendar' },
  { name: 'community', title: '社区', icon: 'chat' },
  { name: 'products', title: '商品', icon: 'goods' },
  { name: 'profile', title: '我的', icon: 'user' },
] as const

const activeTab = ref('home')
const tabbarStyle = Object.freeze({ paddingBottom: `${readBottomSafeArea()}px` })

function readBottomSafeArea(): number {
  try {
    return uni.getSystemInfoSync().safeAreaInsets?.bottom ?? 0
  } catch {
    return 0
  }
}

function showDemoFeedback(label: string) {
  uni.showToast({ title: `${label} · 静态演示`, icon: 'none' })
}

function selectService(serviceName: string) {
  showDemoFeedback(serviceName)
}

function selectTab(tabName: typeof demoTabs[number]['name']) {
  if (tabName !== 'home') {
    showDemoFeedback('本页用于首页视觉评审')
  }
}
</script>

<style scoped>
.demo-page {
  --demo-primary: #11796f;
  --demo-primary-dark: #174f49;
  --demo-ink: #182622;
  --demo-muted: #64736f;
  --demo-line: #e5e8e6;
  --demo-surface: #ffffff;
  min-height: 100vh;
  padding: 32rpx 32rpx 56rpx;
  background: #f7f6f2;
  color: var(--demo-ink);
  box-sizing: border-box;
}

.demo-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 88rpx;
  padding: 4rpx 0 24rpx;
}

.demo-header__copy {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.demo-header__brand {
  font-size: 38rpx;
  line-height: 1.2;
  font-weight: 600;
  letter-spacing: -0.5rpx;
}

.demo-header__location,
.demo-status,
.care-hero__topline,
.story-row__tag,
.story-row__meta {
  display: flex;
  align-items: center;
}

.demo-header__location {
  gap: 6rpx;
  font-size: 24rpx;
  color: var(--demo-muted);
}

.demo-status {
  gap: 8rpx;
}

.demo-status__dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: #19a474;
}

.care-hero {
  padding: 30rpx;
  border-radius: 28rpx;
  background: var(--demo-primary-dark);
  color: #ffffff;
}

.care-hero__topline {
  gap: 12rpx;
}

.care-hero__pet {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: #dff2ed;
}

.care-hero__eyebrow {
  font-size: 24rpx;
  color: #d4e8e4;
}

.care-hero__title {
  display: block;
  max-width: 560rpx;
  margin-top: 20rpx;
  font-size: 38rpx;
  line-height: 1.35;
  font-weight: 600;
}

.care-hero__summary {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.5;
  color: #c8ded9;
}

.care-hero__action {
  margin-top: 28rpx;
}

.notice-row,
.appointment-row,
.story-row {
  display: flex;
  align-items: center;
  min-height: 88rpx;
  background: var(--demo-surface);
}

.notice-row {
  gap: 18rpx;
  margin-top: 20rpx;
  padding: 20rpx 24rpx;
  border: 1rpx solid var(--demo-line);
  border-radius: 18rpx;
}

.notice-row__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  background: #e6f4f0;
  flex-shrink: 0;
}

.notice-row__copy {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 4rpx;
}

.notice-row__label {
  font-size: 24rpx;
  color: var(--demo-primary);
}

.notice-row__text {
  overflow: hidden;
  font-size: 25rpx;
  color: #354541;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.demo-section {
  margin-top: 40rpx;
}

.demo-section--last {
  padding-bottom: 140rpx;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 56rpx;
  margin-bottom: 18rpx;
}

.section-heading__title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--demo-ink);
}

.section-heading__link {
  display: flex;
  align-items: center;
  min-height: 88rpx;
  padding: 18rpx 0 18rpx 28rpx;
  font-size: 24rpx;
  color: var(--demo-primary);
  box-sizing: border-box;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12rpx;
}

.service-item {
  display: flex;
  align-items: center;
  min-width: 0;
  min-height: 176rpx;
  padding: 12rpx 4rpx;
  flex-direction: column;
  box-sizing: border-box;
}

.service-item__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88rpx;
  height: 88rpx;
  margin-bottom: 10rpx;
  border-radius: 24rpx;
}

.service-item__icon--mint {
  background: #dff2ed;
}

.service-item__icon--amber {
  background: #fff0d1;
}

.service-item__icon--blue {
  background: #e8f0fe;
}

.service-item__icon--rose {
  background: #f9e5eb;
}

.service-item__name {
  max-width: 100%;
  overflow: hidden;
  font-size: 25rpx;
  font-weight: 600;
  color: #283a35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-item__hint {
  margin-top: 4rpx;
  font-size: 24rpx;
  color: var(--demo-muted);
}

.appointment-list {
  overflow: hidden;
  border: 1rpx solid var(--demo-line);
  border-radius: 20rpx;
}

.appointment-row {
  gap: 20rpx;
  padding: 24rpx;
}

.appointment-row__date {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 84rpx;
  height: 84rpx;
  border-radius: 18rpx;
  flex-direction: column;
  background: #f3ece2;
  flex-shrink: 0;
}

.appointment-row__day {
  font-size: 30rpx;
  line-height: 1.05;
  font-weight: 600;
  color: #93583f;
}

.appointment-row__month {
  margin-top: 2rpx;
  font-size: 20rpx;
  color: #8b6f63;
}

.appointment-row__body,
.story-row__body {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.appointment-row__title-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}

.appointment-row__title {
  overflow: hidden;
  font-size: 27rpx;
  font-weight: 600;
  color: #263732;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.appointment-row__meta {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--demo-muted);
}

.story-row {
  gap: 20rpx;
  padding: 22rpx;
  border: 1rpx solid var(--demo-line);
  border-radius: 20rpx;
}

.story-row__visual {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120rpx;
  height: 120rpx;
  border-radius: 18rpx;
  background: #f5ebe5;
  flex-shrink: 0;
}

.story-row__tag {
  gap: 6rpx;
  font-size: 24rpx;
  color: #a45d43;
}

.story-row__title {
  display: -webkit-box;
  margin-top: 8rpx;
  overflow: hidden;
  font-size: 27rpx;
  line-height: 1.4;
  font-weight: 600;
  color: #263732;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.story-row__meta {
  gap: 20rpx;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: var(--demo-muted);
}

.story-row__stat {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.demo-tabbar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 20;
  display: flex;
  height: 112rpx;
  border-top: 1rpx solid #e8ebe9;
  background: #ffffff;
}

.demo-tabbar__item {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 88rpx;
  flex: 1;
  flex-direction: column;
  gap: 5rpx;
  font-size: 20rpx;
  color: var(--demo-muted);
}

.demo-tabbar__item--active {
  color: #11796f;
}

.demo-tabbar::before {
  position: absolute;
  top: -1rpx;
  right: 0;
  left: 0;
  height: 1rpx;
  background: #e8ebe9;
  content: '';
}

.demo-tabbar__item:active {
  background: #f4f7f6;
}

.notice-row:active,
.appointment-row:active,
.story-row:active,
.service-item:active,
.section-heading__link:active {
  opacity: 0.72;
}

.demo-tabbar__item--active::after {
  position: absolute;
  width: 8rpx;
  height: 8rpx;
  margin-top: 74rpx;
  border-radius: 50%;
  background: #11796f;
  content: '';
}

.demo-tabbar__item {
  position: relative;
}
</style>
