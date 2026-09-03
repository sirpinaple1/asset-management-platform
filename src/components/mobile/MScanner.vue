<template>
  <div class="m-scanner">
    <video ref="videoEl" class="m-scanner-video" playsinline muted autoplay />

    <!-- 取景框 + 扫描线 -->
    <div class="m-scanner-vf" aria-hidden="true">
      <i class="vf-corner tl" /><i class="vf-corner tr" /><i class="vf-corner bl" /><i class="vf-corner br" />
      <i class="vf-line" />
    </div>

    <div class="m-scanner-topbar">
      <button class="m-scanner-ghost" type="button" @click="close">取消</button>
      <span>扫资产标签</span>
      <button
        v-if="torchAvailable"
        class="m-scanner-ghost"
        type="button"
        :class="{ on: torchOn }"
        @click="toggleTorch"
      >
        {{ torchOn ? '关灯' : '手电' }}
      </button>
      <span v-else class="m-scanner-ph" />
    </div>

    <p class="m-scanner-hint">对准标签上的条形码 / 二维码<br />保持 10-15cm，避免反光，自动识别</p>
    <p v-if="statusText" class="m-scanner-status">{{ statusText }}</p>
  </div>
</template>

<script setup lang="ts">
/**
 * 页内连续扫码组件（zxing 逐帧解码）：
 * - 相比钉钉 dd.biz.util.scan 的单次拍照识别，逐帧重试对一维码（Code128 等）容错高得多
 * - 需要 HTTPS / localhost 安全上下文才能拿到摄像头；不可用由父组件降级钉钉原生扫码
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { BrowserMultiFormatReader } from '@zxing/library'

const emit = defineEmits<{
  scan: [text: string]
  error: [message: string]
  close: []
}>()

const videoEl = ref<HTMLVideoElement>()
const statusText = ref('')
const torchAvailable = ref(false)
const torchOn = ref(false)

let reader: BrowserMultiFormatReader | null = null
let stream: MediaStream | null = null
let stopped = false

const stop = () => {
  reader?.reset()
  reader = null
  stream?.getTracks().forEach((t) => t.stop())
  stream = null
}

const close = () => {
  stopped = true
  stop()
  emit('close')
}

const toggleTorch = async () => {
  const track = stream?.getVideoTracks()[0]
  if (!track) return
  const next = !torchOn.value
  try {
    await track.applyConstraints({
      advanced: [{ torch: next }] as unknown as MediaTrackConstraintSet[],
    })
    torchOn.value = next
  } catch {
    torchAvailable.value = false
  }
}

onMounted(async () => {
  try {
    if (!navigator.mediaDevices?.getUserMedia) {
      emit('error', '当前环境不支持摄像头')
      return
    }

    // 自管 MediaStream：轨道在手，方便停流与手电筒控制
    statusText.value = '正在启动摄像头…'
    stream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: { ideal: 'environment' },
        width: { ideal: 1280 },
        height: { ideal: 720 },
      },
      audio: false,
    })
    if (stopped) {
      stream.getTracks().forEach((t) => t.stop())
      return
    }

    if (videoEl.value) {
      videoEl.value.srcObject = stream
      await videoEl.value.play().catch(() => {})
    }

    // 动态加载：zxing 约 300KB，仅扫码时拉取
    const { BrowserMultiFormatReader: Reader, DecodeHintType, BarcodeFormat } = await import(
      '@zxing/library'
    )
    if (stopped) return

    const hints = new Map()
    hints.set(DecodeHintType.POSSIBLE_FORMATS, [
      BarcodeFormat.CODE_128,
      BarcodeFormat.CODE_39,
      BarcodeFormat.CODABAR,
      BarcodeFormat.ITF,
      BarcodeFormat.EAN_13,
      BarcodeFormat.EAN_8,
      BarcodeFormat.QR_CODE,
      BarcodeFormat.DATA_MATRIX,
    ])
    hints.set(DecodeHintType.TRY_HARDER, true)

    reader = new Reader(hints, 150)
    await reader.decodeFromStream(stream, videoEl.value!, (result) => {
      const text = result?.getText()?.trim()
      if (text && !stopped) {
        stopped = true
        navigator.vibrate?.(40)
        stop()
        emit('scan', text)
      }
    })
    statusText.value = ''

    // 手电筒支持性探测（部分机型无 flash）
    const caps = stream?.getVideoTracks()[0]?.getCapabilities?.() as
      | (MediaTrackCapabilities & { torch?: boolean })
      | undefined
    torchAvailable.value = Boolean(caps?.torch)
  } catch (e) {
    if (stopped) return
    const msg =
      e instanceof DOMException && (e.name === 'NotAllowedError' || e.name === 'SecurityError')
        ? '摄像头权限被拒绝'
        : '摄像头启动失败'
    stop()
    emit('error', msg)
  }
})

onBeforeUnmount(() => {
  stopped = true
  stop()
})
</script>

<style scoped>
.m-scanner {
  position: fixed;
  inset: 0;
  z-index: 110; /* 高于全屏弹层 m-sheet(100) */
  background: #000;
  display: flex;
  flex-direction: column;
}

.m-scanner-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* ---------- 取景框 ---------- */
.m-scanner-vf {
  position: absolute;
  left: 50%;
  top: 42%;
  transform: translate(-50%, -50%);
  width: min(78vw, 320px);
  height: min(48vw, 200px);
}

.vf-corner {
  position: absolute;
  width: 22px;
  height: 22px;
  border: 3px solid var(--color-primary);
}
.vf-corner.tl { left: -1px; top: -1px; border-right: none; border-bottom: none; border-radius: 6px 0 0 0; }
.vf-corner.tr { right: -1px; top: -1px; border-left: none; border-bottom: none; border-radius: 0 6px 0 0; }
.vf-corner.bl { left: -1px; bottom: -1px; border-right: none; border-top: none; border-radius: 0 0 0 6px; }
.vf-corner.br { right: -1px; bottom: -1px; border-left: none; border-top: none; border-radius: 0 0 6px 0; }

.vf-line {
  position: absolute;
  left: 6px;
  right: 6px;
  top: 0;
  height: 2px;
  border-radius: 1px;
  background: linear-gradient(90deg, transparent, var(--color-primary), transparent);
  animation: vf-sweep 2.1s cubic-bezier(0.45, 0, 0.55, 1) infinite;
}

@keyframes vf-sweep {
  0% { transform: translateY(4px); opacity: 0.4; }
  12% { opacity: 1; }
  50% { transform: translateY(calc(min(48vw, 200px) - 6px)); opacity: 1; }
  88% { opacity: 1; }
  100% { transform: translateY(4px); opacity: 0.4; }
}

/* ---------- 顶栏 ---------- */
.m-scanner-topbar {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  padding-top: calc(10px + env(safe-area-inset-top));
  color: var(--color-text-inverse);
  font-size: var(--text-md);
  font-weight: 600;
}

.m-scanner-ghost {
  border: none;
  background: rgba(0, 0, 0, 0.35);
  color: var(--color-text-inverse);
  border-radius: var(--radius-round);
  padding: 7px 16px;
  font-size: var(--text-sm);
  transition: background 0.15s;
}
.m-scanner-ghost.on { background: rgba(255, 255, 255, 0.28); }
.m-scanner-ph { width: 64px; }

/* ---------- 提示 ---------- */
.m-scanner-hint {
  position: relative;
  z-index: 1;
  margin-top: auto;
  margin-bottom: calc(96px + env(safe-area-inset-bottom));
  text-align: center;
  color: rgba(255, 255, 255, 0.85);
  font-size: var(--text-sm);
  line-height: 1.7;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
}

.m-scanner-status {
  position: relative;
  z-index: 1;
  text-align: center;
  color: rgba(255, 255, 255, 0.6);
  font-size: var(--text-xs);
  margin: 0 0 calc(12px + env(safe-area-inset-bottom));
}

@media (prefers-reduced-motion: reduce) {
  .vf-line { animation: none; }
}
</style>
