import { createRouter, createWebHistory } from 'vue-router'
import { resetLoginRedirectFlag } from '@/api/config/request'
import { clearToken, extractTokenFromUrl, getToken, redirectToAuthCenter, setToken, stripAuthParamsFromUrl } from '@/utils/token'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('@/layouts/DefaultLayout.vue'),
      children: [
        {
          path: '',
          redirect: '/dashboard'
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/dashboard/index.vue'),
          meta: { title: '工作台' }
        },
        {
          path: 'basedata/manufacturers',
          name: 'basedata-manufacturers',
          component: () => import('@/views/basedata/ManufacturerList.vue'),
          meta: { title: '厂商管理' }
        },
        {
          path: 'basedata/suppliers',
          name: 'basedata-suppliers',
          component: () => import('@/views/basedata/SupplierList.vue'),
          meta: { title: '供应商管理' }
        },
        {
          path: 'basedata/companies',
          name: 'basedata-companies',
          component: () => import('@/views/basedata/CompanyList.vue'),
          meta: { title: '公司主体' }
        },
        {
          path: 'basedata/categories',
          name: 'basedata-categories',
          component: () => import('@/views/basedata/ComingSoon.vue'),
          meta: { title: '分类管理' }
        },
        {
          path: 'basedata/locations',
          name: 'basedata-locations',
          component: () => import('@/views/basedata/ComingSoon.vue'),
          meta: { title: '位置管理' }
        },
        {
          path: 'basedata/models',
          name: 'basedata-models',
          component: () => import('@/views/basedata/ComingSoon.vue'),
          meta: { title: '型号管理' }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/error/NotFound.vue'),
      meta: { title: '页面不存在' }
    }
  ]
})

router.beforeEach((to) => {
  // 0. 重置 401 跳登录防抖标志：新导航 = 新会话周期（含从 auth-center 携新 token 返回），
  //    防止上一次 401 置位后卡死，二次 token 过期时无法再次跳转登录
  resetLoginRedirectFlag()

  // 1. 接收 auth-center 跳转携带的 token：存 localStorage 后清洗 URL，以干净路径重新导航
  const urlToken = extractTokenFromUrl(to.query.token)
  if (urlToken) {
    setToken(urlToken)
    stripAuthParamsFromUrl()
    return { path: to.path, query: {}, hash: to.hash, replace: true }
  }

  // 2. 未登录（无 token）：跳回 auth-center 登录页，登录后可经 returnUrl 跳回
  if (!getToken()) {
    clearToken()
    redirectToAuthCenter()
    return false
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 资产管理系统` : '资产管理系统'
})

export default router
