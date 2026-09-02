import { request } from '@/api/config/request'

// 前端版本信息（唯一来源）
export const FRONTEND_VERSION = '0.1.7'
export const RELEASE_DATE = '2026-09-02'
export const CHANGELOG: string[] = [
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
