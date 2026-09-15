import { nextTick, onBeforeUnmount, watch } from 'vue'
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
 *   const { guardBeforeClose } = useFormDirtyGuard({ visible: () => props.visible, form: () => formData })
 *   <el-dialog :before-close="guardBeforeClose">
 *
 * 脏判定：弹窗打开时对 form() 做一次快照，之后内容与快照不一致视为脏；
 * 也可通过 isDirty 自定义（优先于 form 快照）。
 * 提交成功后的编程式关闭不走 before-close，无需特判。
 */
export function useFormDirtyGuard(options: {
  visible: () => boolean
  /** 表单数据 getter：打开时快照，与快照不一致视为脏 */
  form?: () => unknown
  /** 自定义脏判定（优先于 form 快照） */
  isDirty?: () => boolean
}) {
  const { visible, form, isDirty } = options

  let baseline = ''

  const dirty = () => (isDirty ? isDirty() : form ? JSON.stringify(form()) !== baseline : false)

  /** 弹确认框；返回 true = 放弃修改继续关闭 */
  const confirmLeave = async (): Promise<boolean> => {
    if (!visible() || !dirty()) return true
    try {
      await ElMessageBox.confirm('表单已有修改，关闭后将丢失未保存的内容', '放弃修改？', {
        type: 'warning',
        confirmButtonText: '放弃修改',
        cancelButtonText: '继续编辑',
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

  /* 打开时重置快照（nextTick 等表单回填完成）；关闭时清空 */
  watch(visible, (v) => {
    if (v) {
      void nextTick(() => {
        baseline = form ? JSON.stringify(form()) : ''
      })
    } else {
      baseline = ''
    }
  }, { immediate: true })

  /* beforeunload：表单打开且脏时提醒（注册/注销随 visible 走） */
  const onBeforeUnload = (e: BeforeUnloadEvent) => {
    if (visible() && dirty()) {
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
