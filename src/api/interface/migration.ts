/**
 * M08 历史数据迁移类型。
 * 契约依据：asset-backend migration 包 MigrationController（POST /api/v1/migration/run）+ MigrationResult。
 * 双重门禁（业务错误，HTTP 200 + code != 200）：后端 app.migration.enabled=false → 503；
 * 当前用户无 required-role 角色 → 403。均由 axios 拦截器统一 ElMessage 提示。
 */

/** 迁移阶段执行计数（phase 由后端命名，固定五阶段） */
export interface MigrationPhase {
  /**
   * 阶段名（后端原文）：
   * 基础数据（公司/分类/位置/供应商）/ 资产主数据 / 领用单（ARE）/ 调拨单（ATR）/ 操作日志
   */
  phase: string
  /** 处理总行数（Excel 有效行） */
  rows: number
  /** 新增（首次导入） */
  inserted: number
  /** 更新（重跑 upsert 刷新为 Excel 基线） */
  updated: number
  /** 跳过（如持有关系已存在的补建跳过） */
  skipped: number
}

/** 迁移执行结果（POST /v1/migration/run 响应） */
export interface MigrationResult {
  startedAt: string
  finishedAt: string
  phases: MigrationPhase[]
  /** 自动创建的 sys_user 账号（旧系统使用人未匹配到 comm_public_basic 用户时按姓名建号，初始密码为后端配置默认值） */
  createdUsers: string[]
  /** 需人工关注的警告（重名取最小 id、未知状态、无匹配位置等），上限 200 条 */
  warnings: string[]
}

/** 迁移执行所需角色（后端 app.migration.required-role 默认值；前端仅做按钮预检，后端为权威门禁） */
export const MIGRATION_REQUIRED_ROLE = 'asset-资产管理员'
