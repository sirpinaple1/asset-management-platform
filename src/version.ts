import { request } from '@/api/config/request'

// 前端版本信息（唯一来源）
export const FRONTEND_VERSION = '0.1.12'
export const RELEASE_DATE = '2026-09-02'
export const CHANGELOG: string[] = [
  '修复 PC 钉钉免登静默失败（JSAPI 需在 dd.ready 内调用）；登录页跳转改为相对路径，http 入口不再被自签证书拦截',
  '钉钉免登：在钉钉 PC/手机工作台打开本系统可静默登录，无需输账号密码；token 失效自动免登重进（浏览器内仍走登录中心）',
  '浏览器 tab 标题新增未读角标：被抄送/审批新进展等通知未读数以【N】前缀显示在标签页标题（超99显示99+），切后台也不错过提醒',
  '新增退出登录：右上角用户菜单可退出账号，清理本地登录态后跳转登录中心，重新登录支持换账号并自动跳回',
  '审批中心新增「抄送我的」第四视图（DOC_CC 通知表格，可深链单据详情）；修复右上角与左下角铃铛红点不同步（未读数改为共享 store）',
  '通知中心新增「抄送我的」视图（type=DOC_CC），并对齐后端新通知契约（type/typeLabel/bizType/bizId/readFlag）',
  '侧边栏交互优化：进入审批中心仅亮“审批中心”图标（不再联动“资产管理”），资产功能/基础设置分组自动折叠、保留入口可展开跳转',
  '多页签工作台 + 命令面板（Ctrl+K 全局搜索）',
  '审批中心：待办/发起/处理过/抄送四视图，两级审批进度展示',
  '资产管理列表 + 基础数据管理（公司/厂商/供应商/分类/位置/组织架构）',
  '领用/借用/调拨/变更/盘点五类单据全流程',
  '通知中心 + 左下角帮助面板版本信息（本面板）',
]
export const CREDIT = '© 2026 潘雨松 & GLM-5.3 · 共同完成'

export interface BackendVersion { version: string; releaseDate: string; changelog: string[]; credit: string }

// 帮助面板打开时调用；失败返回 null，面板降级只显示前端版本
export function fetchBackendVersion(): Promise<BackendVersion | null> {
  return request<BackendVersion>({ url: '/v1/version', method: 'get', skipErrorToast: true }).catch(() => null)
}
