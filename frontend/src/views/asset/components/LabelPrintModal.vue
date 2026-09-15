<script setup lang="ts">
import { ref, watch } from 'vue'
import QRCode from 'qrcode'
import type { Asset } from '@/api/interface/asset'

/**
 * 资产二维码标签打印（P0）：
 * - 标签规格 40×30mm（热敏/不干胶），内容 = 二维码（编码文本）+ 资产编码 + 资产名称
 * - 打印通过隐藏 iframe + @page { size: 40mm 30mm } 逐张分页，浏览器直接打印
 * - 弹窗内为等比预览（放大 2 倍便于查看，打印仍按实际尺寸）
 */
const props = defineProps<{
  visible: boolean
  rows: Asset[]
}>()

const emit = defineEmits<{ (e: 'update:visible', v: boolean): void }>()

const LABEL_W_MM = 40
const LABEL_H_MM = 30

/** 每行资产生成一张标签（二维码内容 = 资产编码，扫码/扫码枪可直接得到编码） */
interface LabelItem {
  barcode: string
  name: string
  qr: string
}
const labels = ref<LabelItem[]>([])
const generating = ref(false)

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) return
    generating.value = true
    try {
      labels.value = await Promise.all(
        props.rows.map(async (row) => ({
          barcode: row.barcode,
          name: row.name,
          qr: await QRCode.toDataURL(row.barcode, { margin: 0, width: 160, errorCorrectionLevel: 'M' }),
        })),
      )
    } finally {
      generating.value = false
    }
  },
)

const close = () => emit('update:visible', false)

/** 单张标签 HTML（打印与预览共用同一结构，保证所见即所得） */
const labelHtml = (item: LabelItem) => `
  <div class="label">
    <img src="${item.qr}" alt="${item.barcode}" />
    <div class="txt">
      <div class="bc">${item.barcode}</div>
      <div class="nm">${item.name.replace(/</g, '&lt;')}</div>
    </div>
  </div>`

const printCss = `
  @page { size: ${LABEL_W_MM}mm ${LABEL_H_MM}mm; margin: 0; }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif; }
  .label {
    width: ${LABEL_W_MM}mm; height: ${LABEL_H_MM}mm;
    display: flex; align-items: center; gap: 1mm; padding: 2mm;
    overflow: hidden; page-break-after: always; break-after: page;
  }
  .label:last-child { page-break-after: auto; break-after: auto; }
  .label img { width: 24mm; height: 24mm; flex: none; }
  .txt { flex: 1; min-width: 0; }
  .bc { font: bold 5.5pt/1.25 'Menlo', 'Courier New', monospace; word-break: break-all; }
  .nm { font-size: 4.5pt; line-height: 1.3; color: #333; margin-top: 0.8mm; word-break: break-all; }
`

/** 打印：隐藏 iframe 写入标签页文档后调起打印（不影响当前页面样式） */
const handlePrint = () => {
  const iframe = document.createElement('iframe')
  iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden;'
  document.body.appendChild(iframe)
  const doc = iframe.contentDocument
  if (!doc) return
  doc.open()
  doc.write(`<!doctype html><html><head><meta charset="utf-8"><style>${printCss}</style></head><body>${labels.value.map(labelHtml).join('')}</body></html>`)
  doc.close()
  // data URL 图片解码极快，稍等渲染稳定后调起打印
  window.setTimeout(() => {
    iframe.contentWindow?.focus()
    iframe.contentWindow?.print()
    // 打印对话框关闭后移除 iframe（print 为阻塞调用，多数浏览器此处已在对话框关闭后）
    window.setTimeout(() => iframe.remove(), 500)
  }, 300)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="打印资产标签（40×30mm）"
    width="640px"
    :close-on-click-modal="false"
    append-to-body
    @update:model-value="close"
  >
    <div v-loading="generating" class="print-body">
      <div class="preview-grid">
        <div v-for="item in labels" :key="item.barcode" class="label label--preview">
          <img :src="item.qr" alt="" />
          <div class="txt">
            <div class="bc">{{ item.barcode }}</div>
            <div class="nm">{{ item.name }}</div>
          </div>
        </div>
      </div>
      <div v-if="!labels.length && !generating" class="empty-tip">未选择资产</div>
    </div>
    <template #footer>
      <span class="footer-info">共 {{ labels.length }} 张 · 二维码内容为资产编码</span>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :disabled="!labels.length" @click="handlePrint">打印</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.print-body {
  min-height: 120px;
  max-height: 52vh;
  overflow: auto;
}

/* 预览按实际尺寸放大 2 倍展示，打印仍按 40×30mm 原样输出 */
.preview-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.label {
  width: 80px;
  height: 60px;
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 5px;
  border: 1px dashed var(--color-border-2);
  border-radius: 2px;
  overflow: hidden;
}

.label img {
  width: 48px;
  height: 48px;
  flex: none;
}

.txt {
  flex: 1;
  min-width: 0;
}

.bc {
  font-size: 8px;
  font-weight: 700;
  font-family: Menlo, 'Courier New', monospace;
  line-height: 1.25;
  word-break: break-all;
}

.nm {
  font-size: 7px;
  color: var(--color-text-3);
  line-height: 1.3;
  margin-top: 2px;
  word-break: break-all;
}

.empty-tip {
  text-align: center;
  color: var(--color-text-3);
  padding: 32px 0;
}

.footer-info {
  float: left;
  font-size: var(--text-sm);
  color: var(--color-text-3);
  line-height: 32px;
  margin-right: 12px;
}
</style>
