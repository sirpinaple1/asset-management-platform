import { onBeforeUnmount, watch } from 'vue'
import { ElMessageBox } from 'element-plus'

/**
 * 表单未保存离开拦截（P1）。
 *
 * 覆盖三条丢失路径：
 * 1. Modal 主动关闭（X / Esc / 遮罩）→ confirmLeave() 弹确认，接入 el-dialog 的 before-close
 * 2. 浏览器刷新/关闭 → beforeunload 原生确认（表单打开且脏时注册）
 * 3. 路由切页 → keep-alive 下表单状态保留，切回仍在，无需拦截
 *
 * 用法：
 *   const { guardBeforeClose } = useFormDirtyGuard({ visible: () => props.visible, isDirty })
 *   <el-dialog :before-close="guardBeforeClose">
 *
 * 提交成功后的编程式关闭不走 before-close，无需特判。
 */
export function useFormDirtyGuard(options: { visible: () => boolean; isDirty: () => boolean }) {
  const { visible, isDirty } = options

  /** 弹确认框；返回 true = 放弃修改继续关闭 */
  const confirmLeave = async (): Promise<boolean> => {
    if (!visible.value || !isDirty()) return true
    try {
      await ElMessageBox.confirm('表单已有修改，关闭后将丢失未保存的内容', '放弃修改？', {
        type: 'warning',
        confirmButtonText: '放弃修改',
        cancelButtonText: '继续编辑',
        autoFocusButton: 'cancel',
      })
      return true
    } catch {
      return false
    }
  }

  /** el-dialog before-close 适配器 */
  const guardBeforeClose = (done: () => void) => {
    void confirmLeave().then((ok) => ok && done())
  }

  /* beforeunload：表单打开且脏时提醒（注册/注销随 visible 走） */
  const onBeforeUnload = (e: BeforeUnloadEvent) => {
    if (visible.value && isDirty()) {
      e.preventDefault()
      e.returnValue = ''
    }
  }
  watch(visible, (v) => {
    if (v) window.addEventListener('beforeunload', onBeforeUnload)
    else window.removeEventListener('beforeunload', onBeforeUnload)
  }, { immediate: true })
  onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))

  return { confirmLeave, guardBeforeClose }
}
