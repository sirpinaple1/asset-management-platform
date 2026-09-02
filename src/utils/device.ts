/**
 * 设备检测工具（移动 H5 子应用路由分发用）。
 */

/** 是否移动端 UA（手机浏览器 / 钉钉手机容器；iPad 新版 UA 伪装为 Mac 不识别，可接受） */
export function isMobileUA(): boolean {
  return /Android|iPhone|iPod|Mobile/i.test(navigator.userAgent)
}
