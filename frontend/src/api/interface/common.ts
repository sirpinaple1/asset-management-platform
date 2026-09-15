/** 统一分页响应（asset-backend common.PageResp） */
export interface PageResp<T> {
  records: T[]
  total: number
  page: number
  size: number
}
