import { h } from 'vue'
import { ElButton, ElMessage } from 'element-plus'

/** 撤销提示驻留时长（ms） */
const UNDO_DURATION = 8000

/**
 * 显示带"撤销"按钮的成功消息（可撤销操作反馈）。
 * 点撤销执行 onUndo（失败由 axios 拦截器提示），无论成败都关闭消息。
 */
export function showUndoMessage(text: string, onUndo: () => Promise<unknown>) {
  let undoing = false
  const instance = ElMessage({
    type: 'success',
    duration: UNDO_DURATION,
    showClose: true,
    message: h('span', { style: 'display:inline-flex;align-items:center;gap:4px' }, [
      text,
      h(
        ElButton,
        {
          link: true,
          type: 'primary',
          size: 'small',
          style: 'margin-left:8px',
          onClick: async () => {
            if (undoing) return
            undoing = true
            try {
              await onUndo()
            } finally {
              instance.close()
            }
          },
        },
        () => '撤销',
      ),
    ]),
  })
}
