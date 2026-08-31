import { request } from '@/api/config/request'

// 前端版本信息（唯一来源）
export const FRONTEND_VERSION = '0.10.0'
export const RELEASE_DATE = '2026-08-31'
export const CHANGELOG: string[] = [
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
