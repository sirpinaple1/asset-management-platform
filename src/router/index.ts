import { nextTick } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { resetLoginRedirectFlag } from '@/api/config/request'
import { clearToken, extractTokenFromUrl, getToken, redirectToAuthCenter, setToken, stripAuthParamsFromUrl } from '@/utils/token'
import { dingTalkLogin, isInDingTalk } from '@/utils/dingtalk'
import { isMobileUA } from '@/utils/device'
import { setBaseTitle } from '@/utils/tabTitle'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/m',
      component: () => import('@/layouts/MobileLayout.vue'),
      children: [
        {
          path: '',
          redirect: '/m/approvals'
        },
        {
          path: 'approvals',
          name: 'm-approvals',
          component: () => import('@/views/mobile/MApprovals.vue'),
          meta: { title: '审批中心', mobile: true }
        },
        {
          path: 'assets',
          name: 'm-assets',
          component: () => import('@/views/mobile/MAssets.vue'),
          meta: { title: '我的资产', mobile: true }
        },
        {
          path: 'scan',
          name: 'm-scan',
          component: () => import('@/views/mobile/MScan.vue'),
          meta: { title: '扫码查资产', mobile: true }
        },
        {
          path: 'apply',
          name: 'm-apply',
          component: () => import('@/views/mobile/MApply.vue'),
          meta: { title: '发起申请', mobile: true }
        },
        {
          path: 'profile',
          name: 'm-profile',
          component: () => import('@/views/mobile/MProfile.vue'),
          meta: { title: '我的', mobile: true }
        }
      ]
    },
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
          path: 'approvals',
          name: 'approvals-center',
          component: () => import('@/views/approval/ApprovalCenter.vue'),
          meta: { title: '审批中心' }
        },
        {
          path: 'approvals/all',
          name: 'approvals-all',
          component: () => import('@/views/approval/DocumentOverview.vue'),
          meta: { title: '全部单据' }
        },
        {
          path: 'assets',
          name: 'assets-list',
          component: () => import('@/views/asset/AssetList.vue'),
          meta: { title: '资产列表' }
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
          component: () => import('@/views/basedata/CategoryList.vue'),
          meta: { title: '分类管理' }
        },
        {
          path: 'basedata/locations',
          name: 'basedata-locations',
          component: () => import('@/views/basedata/LocationList.vue'),
          meta: { title: '位置管理' }
        },
        {
          path: 'basedata/models',
          name: 'basedata-models',
          component: () => import('@/views/basedata/ComingSoon.vue'),
          meta: { title: '型号管理' }
        },
        {
          path: 'basedata/migration',
          name: 'basedata-migration',
          component: () => import('@/views/basedata/MigrationPage.vue'),
          meta: { title: '数据迁移' }
        },
        {
          path: 'basedata/approval-configs',
          name: 'basedata-approval-configs',
          component: () => import('@/views/basedata/ApprovalConfigList.vue'),
          meta: { title: '组织架构管理' }
        },
        {
          path: 'receipts/receive',
          name: 'receipts-receive',
          component: () => import('@/views/receipt/ReceiveList.vue'),
          meta: { title: '领用&退库' }
        },
        {
          path: 'receipts/borrow',
          name: 'receipts-borrow',
          component: () => import('@/views/receipt/BorrowList.vue'),
          meta: { title: '借用&归还' }
        },
        {
          path: 'transfers',
          name: 'transfers-list',
          component: () => import('@/views/transfer/TransferList.vue'),
          meta: { title: '资产调拨' }
        },
        {
          path: 'changes',
          name: 'changes-list',
          component: () => import('@/views/change/ChangeList.vue'),
          meta: { title: '实物信息变更' }
        },
        {
          path: 'stocktakes',
          name: 'stocktakes-list',
          component: () => import('@/views/stocktake/StocktakeList.vue'),
          meta: { title: '盘点管理' }
        },
        {
          path: 'exception/:type?',
          name: 'exception',
          component: () => import('@/views/error/ExceptionPage.vue'),
          meta: { title: '系统异常' }
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

router.beforeEach(async (to, from) => {
  // 0. 重置 401 跳登录防抖标志：新导航 = 新会话周期（含从 auth-center 携新 token 返回），
  //    防止上一次 401 置位后卡死，二次 token 过期时无法再次跳转登录
  resetLoginRedirectFlag()

  // 滚动恢复：离开页面前记录内容区滚动位置（SPA 内容滚动在 .content-scroll，非 window）
  if (from.fullPath) {
    const scrollEl = document.querySelector('.content-scroll')
    if (scrollEl) scrollMap.set(from.fullPath, scrollEl.scrollTop)
  }

  // 1. 接收 auth-center 跳转携带的 token：存 localStorage 后清洗 URL，以干净路径重新导航
  const urlToken = extractTokenFromUrl(to.query.token)
  if (urlToken) {
    setToken(urlToken)
    stripAuthParamsFromUrl()
    return { path: to.path, query: {}, hash: to.hash, replace: true }
  }

  // 1.5 移动端 UA 分流：手机访问桌面路径 → 跳移动版首页（/m/* 不受影响；
  // 放在 token 处理后，免登/登录回调的目标路径即移动页，登录完直接回到移动版）
  if (isMobileUA() && !to.path.startsWith('/m/')) {
    return { path: '/m/approvals', replace: true }
  }

  // 2. 未登录（无 token）：钉钉容器内静默免登；失败或非钉钉环境则跳 auth-center 登录页
  if (!getToken()) {
    clearToken()
    if (isInDingTalk()) {
      try {
        const token = await dingTalkLogin()
        setToken(token)
        return true
      } catch (e) {
        // 免登失败（未绑定/默认密码/授权异常）：降级登录页，用户可用账密登录后跳回
        console.warn('[dingtalk] 免登失败，降级登录页：', e)
      }
    }
    redirectToAuthCenter()
    return false
  }

  // 3. 手机 UA（手机浏览器 / 钉钉手机容器）进入移动 H5 布局；
  //    放在 token 处理之后，避免吞掉 auth-center 携 token 返回的 query
  if (isMobileUA() && !to.path.startsWith('/m')) {
    return { path: '/m/approvals', replace: true }
  }

  return true
})

router.afterEach((to) => {
  setBaseTitle(to.meta.title ? `${to.meta.title} - 资产管理系统` : '资产管理系统')

  // 最近使用足迹：工作台"最近使用"卡数据源（排除工作台自身与 404）
  recordRecentRoute(to)

  // 滚动恢复：回到访问过的页面时滚回上次离开的位置，新页面回顶部
  nextTick(() => {
    const scrollEl = document.querySelector('.content-scroll')
    if (scrollEl) scrollEl.scrollTop = scrollMap.get(to.fullPath) ?? 0
  })
})

/** 最近使用足迹（localStorage）：同 fullPath 去重置顶，上限 8 条 */
const RECENT_KEY = 'asset.recent.routes'
const RECENT_MAX = 8
function recordRecentRoute(to: { fullPath: string; name?: unknown; meta: { title?: unknown; mobile?: unknown } }) {
  if (to.fullPath === '/dashboard' || to.name === 'not-found' || !to.meta.title || to.meta.mobile) return
  try {
    const list: { path: string; title: string; ts: number }[] = JSON.parse(
      localStorage.getItem(RECENT_KEY) || '[]',
    )
    const next = [
      { path: to.fullPath, title: String(to.meta.title), ts: Date.now() },
      ...list.filter((it) => it.path !== to.fullPath),
    ].slice(0, RECENT_MAX)
    localStorage.setItem(RECENT_KEY, JSON.stringify(next))
  } catch {
    /* 忽略损坏的本地存储 */
  }
}

/** 各页面内容区滚动位置记忆（fullPath → scrollTop） */
const scrollMap = new Map<string, number>()

export default router
