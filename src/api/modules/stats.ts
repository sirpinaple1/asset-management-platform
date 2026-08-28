import { request } from '@/api/config/request'
import type { StatsOverviewVO } from '@/api/interface/stats'

/** B3：工作台统计聚合（每小时可缓存，此处直接拉最新） */
export const statsApi = {
  overview: () => request<StatsOverviewVO>({ url: '/v1/stats/overview', method: 'get' }),
}
