import { request } from '@/api/config/request'
import type { MigrationResult } from '@/api/interface/migration'

/**
 * M08 历史数据迁移 API。
 * 契约依据：asset-backend migration 包 MigrationController（/api/v1/migration）。
 * 迁移串行处理 2500+ 行 Excel 并匹配 comm_public_basic 用户，耗时远超 axios 全局 15s 超时，此请求单独放宽到 5 分钟。
 */
export const migrationApi = {
  /**
   * 执行历史数据迁移（幂等，可重复运行）。
   * 顺序：基础数据 → 资产主数据 → 领用单（ARE）→ 调拨单（ATR）→ 操作日志；
   * 资产/单据按编码 upsert，历史导入的日志按时间区段清除后重灌。
   */
  run: () =>
    request<MigrationResult>({
      url: '/v1/migration/run',
      method: 'post',
      timeout: 300000,
    }),
}
