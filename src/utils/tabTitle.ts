/**
 * 浏览器 tab 标题角标（对齐钉钉/Gmail 等大厂 tab 未读提示惯例）：
 * 未读数以【N】前缀合成进 document.title，超 99 显示【99+】，为 0 时恢复原标题。
 * 两个数据来源解耦：
 * - base 标题由路由 afterEach 写入（页面切换只换 base，角标保留）；
 * - 角标数由通知 store 写入（未读数轮询/已读扣减后即时同步）。
 */

let baseTitle = '资产管理系统'
let badge = 0

function render(): void {
  const prefix = badge > 0 ? `【${badge > 99 ? '99+' : badge}】` : ''
  document.title = `${prefix}${baseTitle}`
}

/** 设置基础标题（路由切换时调用；角标保留，重新合成） */
export function setBaseTitle(title: string): void {
  baseTitle = title
  render()
}

/** 设置未读角标数（通知未读数变化时调用；0 时移除角标） */
export function setTitleBadge(count: number): void {
  badge = Math.max(0, count)
  render()
}
