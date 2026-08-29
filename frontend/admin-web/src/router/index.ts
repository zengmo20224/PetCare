import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '../store/user'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/index.vue'),
    meta: { hidden: true },
  },
  {
    path: '/',
    component: () => import('../layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/index.vue'),
        meta: { title: 'Dashboard', icon: 'Menu' },
      },
      {
        path: 'analytics',
        name: 'Analytics',
        component: () => import('../views/analytics/index.vue'),
        meta: { title: '运营统计', icon: 'DataLine', permission: 'analytics:dashboard:read' },
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('../views/user/index.vue'),
        meta: { title: '用户管理', icon: 'UserFilled', permission: 'user:profile:read' },
      },
      {
        path: 'store/info',
        name: 'StoreInfo',
        component: () => import('../views/store/info.vue'),
        meta: { title: '门店信息', icon: 'OfficeBuilding', permission: 'store:info:read' },
      },
      {
        path: 'store/config',
        name: 'StoreConfig',
        component: () => import('../views/store/config.vue'),
        meta: { title: '门店配置', icon: 'Setting', permission: 'store:config:read' },
      },
      {
        path: 'services',
        name: 'ServiceItems',
        component: () => import('../views/service/index.vue'),
        meta: { title: '服务项目', icon: 'Briefcase', permission: 'service:item:read' },
      },
      {
        path: 'staff',
        name: 'Staff',
        component: () => import('../views/staff/index.vue'),
        meta: { title: '员工管理', icon: 'User', permission: 'staff:profile:read' },
      },
      {
        path: 'bookings',
        name: 'Bookings',
        component: () => import('../views/booking/index.vue'),
        meta: { title: '预约管理', icon: 'Calendar', permission: 'booking:booking:read' },
      },
      {
        path: 'products',
        name: 'Products',
        component: () => import('../views/product/index.vue'),
        meta: { title: '商品管理', icon: 'ShoppingBag', permission: 'product:item:read' },
      },
      {
        path: 'product-orders',
        name: 'ProductOrders',
        component: () => import('../views/product-order/index.vue'),
        meta: { title: '自提订单', icon: 'Box', permission: 'product:order:read' },
      },
      {
        path: 'wallet/accounts',
        name: 'WalletAccounts',
        component: () => import('../views/wallet/accounts/index.vue'),
        meta: { title: '用户钱包', icon: 'Wallet', permission: 'wallet:account:read' },
      },
      {
        path: 'wallet/transactions',
        name: 'WalletTransactions',
        component: () => import('../views/wallet/transactions/index.vue'),
        meta: { title: '钱包流水', icon: 'List', permission: 'wallet:transaction:read' },
      },
      {
        path: 'announcements',
        name: 'Announcements',
        component: () => import('../views/announcement/index.vue'),
        meta: { title: '公告管理', icon: 'Bell', permission: 'system:config' },
      },
      {
        path: 'activities',
        name: 'Activities',
        component: () => import('../views/activity/index.vue'),
        meta: { title: '营销活动', icon: 'Bell', permission: 'marketing:activity:read' },
      },
      {
        path: 'community/posts',
        name: 'CommunityPosts',
        component: () => import('../views/community/posts.vue'),
        meta: { title: '帖子管理', icon: 'ChatDotRound', permission: 'community:post:read' },
      },
      {
        path: 'community/reports',
        name: 'CommunityReports',
        component: () => import('../views/community/reports.vue'),
        meta: { title: '举报处理', icon: 'Warning', permission: 'community:report:handle' },
      },
      {
        path: 'moderation/sensitive-words',
        name: 'SensitiveWords',
        component: () => import('../views/moderation/sensitive-words.vue'),
        meta: { title: '敏感词', icon: 'Filter', permission: 'community:sensitive-word:manage' },
      },
      {
        path: 'operation-logs',
        name: 'OperationLogs',
        component: () => import('../views/operation-logs/index.vue'),
        meta: { title: '操作日志', icon: 'Document', permission: 'admin:operation-log:read' },
      },
      {
        path: 'ai/reports',
        name: 'AiReports',
        component: () => import('../views/ai/reports.vue'),
        meta: { title: 'AI 分析报告', icon: 'DataAnalysis', permission: 'ai:analysis:generate' },
      },
      {
        path: 'ai/usage',
        name: 'AiUsage',
        component: () => import('../views/ai/usage.vue'),
        meta: { title: 'AI 调用用量', icon: 'TrendCharts', permission: 'ai:usage:read' },
      },
    ],
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('../views/error/403.vue'),
    meta: { hidden: true },
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('../views/error/404.vue'),
    meta: { hidden: true },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
    meta: { hidden: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  const token = userStore.token

  if (to.path === '/login') {
    if (token) {
      next({ path: '/' })
    } else {
      next()
    }
  } else {
    if (token) {
      if (!userStore.userInfo) {
        try {
          await userStore.getInfoAction()
        } catch {
          userStore.logoutAction()
          const safePath = to.path.startsWith('/') && !to.path.startsWith('//') ? to.path : '/'
          next(`/login?redirect=${safePath}`)
          return
        }
      }
      // Enforce route-level permission — defense in depth (backend is authoritative)
      const requiredPermission = to.meta.permission as string | undefined
      if (requiredPermission && !userStore.hasPermission(requiredPermission)) {
        next('/403')
        return
      }
      next()
    } else {
      const safePath = to.path.startsWith('/') && !to.path.startsWith('//') ? to.path : '/'
      next(`/login?redirect=${safePath}`)
    }
  }
})

export default router
