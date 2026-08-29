<template>
  <el-container class="app-wrapper">
    <el-aside :width="sidebarWidth" class="pc-sidebar">
      <div class="pc-sidebar__logo">
        <span class="pc-sidebar__logo-text">PetCare Admin</span>
      </div>
      <el-menu
        router
        :default-active="$route.path"
        :collapse="sidebarCollapsed"
        background-color="var(--pc-primary-dark)"
        text-color="rgba(255, 255, 255, 0.7)"
        active-text-color="#fff"
        class="pc-sidebar__menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Menu /></el-icon>
          <span>运营总览</span>
        </el-menu-item>

        <el-menu-item v-if="userStore.hasPermission('analytics:dashboard:read')" index="/analytics">
          <el-icon><DataLine /></el-icon>
          <span>运营统计</span>
        </el-menu-item>

        <el-menu-item v-if="userStore.hasPermission('user:profile:read')" index="/users">
          <el-icon><UserFilled /></el-icon>
          <span>用户管理</span>
        </el-menu-item>

        <el-sub-menu v-if="userStore.hasPermission('store:info:read') || userStore.hasPermission('store:config:read')" index="store-sub">
          <template #title>
            <el-icon><OfficeBuilding /></el-icon>
            <span>门店管理</span>
          </template>
          <el-menu-item v-if="userStore.hasPermission('store:info:read')" index="/store/info">门店信息</el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('store:config:read')" index="/store/config">门店配置</el-menu-item>
        </el-sub-menu>

        <el-menu-item v-if="userStore.hasPermission('service:item:read')" index="/services">
          <el-icon><Briefcase /></el-icon>
          <span>服务项目</span>
        </el-menu-item>

        <el-menu-item v-if="userStore.hasPermission('staff:profile:read')" index="/staff">
          <el-icon><User /></el-icon>
          <span>员工管理</span>
        </el-menu-item>

        <el-menu-item v-if="userStore.hasPermission('booking:booking:read')" index="/bookings">
          <el-icon><Calendar /></el-icon>
          <span>预约管理</span>
        </el-menu-item>

        <el-menu-item v-if="userStore.hasPermission('system:config')" index="/announcements">
          <el-icon><Bell /></el-icon>
          <span>公告管理</span>
        </el-menu-item>

        <el-menu-item v-if="userStore.hasPermission('marketing:activity:read')" index="/activities">
          <el-icon><Bell /></el-icon>
          <span>营销活动</span>
        </el-menu-item>

        <el-sub-menu v-if="userStore.hasPermission('product:item:read') || userStore.hasPermission('product:order:read')" index="product-sub">
          <template #title>
            <el-icon><ShoppingBag /></el-icon>
            <span>商品管理</span>
          </template>
          <el-menu-item v-if="userStore.hasPermission('product:item:read')" index="/products">商品列表</el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('product:order:read')" index="/product-orders">商品订单</el-menu-item>
        </el-sub-menu>

        <el-sub-menu
          v-if="userStore.hasPermission('wallet:account:read') || userStore.hasPermission('wallet:transaction:read')"
          index="wallet-sub"
        >
          <template #title>
            <el-icon><Wallet /></el-icon>
            <span>钱包管理</span>
          </template>
          <el-menu-item v-if="userStore.hasPermission('wallet:account:read')" index="/wallet/accounts">用户钱包</el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('wallet:transaction:read')" index="/wallet/transactions">钱包流水</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="userStore.hasPermission('community:post:read') || userStore.hasPermission('community:report:handle')" index="community-sub">
          <template #title>
            <el-icon><ChatDotRound /></el-icon>
            <span>社区管理</span>
          </template>
          <el-menu-item v-if="userStore.hasPermission('community:post:read')" index="/community/posts">帖子审核</el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('community:report:handle')" index="/community/reports">举报处理</el-menu-item>
        </el-sub-menu>

        <el-menu-item v-if="userStore.hasPermission('community:sensitive-word:manage')" index="/moderation/sensitive-words">
          <el-icon><Filter /></el-icon>
          <span>敏感词</span>
        </el-menu-item>

        <el-menu-item v-if="userStore.hasPermission('admin:operation-log:read')" index="/operation-logs">
          <el-icon><Document /></el-icon>
          <span>操作日志</span>
        </el-menu-item>

        <el-menu-item v-if="userStore.hasPermission('ai:analysis:generate')" index="/ai/reports">
          <el-icon><DataAnalysis /></el-icon>
          <span>AI 分析报告</span>
        </el-menu-item>

        <el-menu-item v-if="userStore.hasPermission('ai:usage:read')" index="/ai/usage">
          <el-icon><TrendCharts /></el-icon>
          <span>AI 调用用量</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container class="pc-main">
      <el-header class="pc-navbar">
        <div class="pc-navbar__toggle" @click="sidebarCollapsed = !sidebarCollapsed">
          <el-icon :size="18"><Fold v-if="!sidebarCollapsed" /><Expand v-else /></el-icon>
        </div>
        <div class="pc-navbar__right">
          <span class="pc-navbar__user">{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</span>
          <el-tag size="small" type="info">{{ userStore.userInfo?.role }}</el-tag>
          <el-button type="primary" link @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="pc-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useUserStore } from '../store/user'
import { useRouter } from 'vue-router'
import {
  Menu,
  OfficeBuilding,
  Briefcase,
  User,
  UserFilled,
  Calendar,
  Bell,
  ShoppingBag,
  ChatDotRound,
  Filter,
  Document,
  DataAnalysis,
  DataLine,
  TrendCharts,
  Fold,
  Expand,
  Wallet,
} from '@element-plus/icons-vue'

const userStore = useUserStore()
const router = useRouter()
const sidebarCollapsed = ref(false)

const sidebarWidth = computed(() =>
  sidebarCollapsed.value ? '64px' : 'var(--pc-sidebar-width)'
)

const handleLogout = () => {
  userStore.logoutAction()
  router.push('/login')
}
</script>

<style scoped>
.app-wrapper {
  height: 100vh;
  width: 100vw;
  min-width: var(--pc-min-width);
}

/* ─── Sidebar ─── */

.pc-sidebar {
  background-color: var(--pc-primary-dark);
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.3s ease;
}

.pc-sidebar__logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--pc-primary-dark);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.pc-sidebar__logo-text {
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.pc-sidebar__menu {
  border-right: none;
}

.pc-sidebar__menu .el-menu-item.is-active {
  background-color: var(--pc-primary) !important;
}

/* ─── Main Area ─── */

.pc-main {
  background-color: var(--pc-surface);
}

/* ─── Navbar ─── */

.pc-navbar {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid var(--pc-line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--pc-content-gap);
  box-shadow: var(--pc-shadow-sm);
}

.pc-navbar__toggle {
  cursor: pointer;
  display: flex;
  align-items: center;
  color: var(--pc-muted);
  transition: color 0.2s;
}

.pc-navbar__toggle:hover {
  color: var(--pc-ink);
}

.pc-navbar__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pc-navbar__user {
  font-size: var(--pc-font-size-base);
  color: var(--pc-ink);
}

/* ─── Content ─── */

.pc-content {
  padding: var(--pc-content-gap);
  min-height: 0;
}
</style>
