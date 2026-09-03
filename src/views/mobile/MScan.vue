<template>
  <div class="m-page">
    <!-- 扫码入口 -->
    <div class="m-scan-entry">
      <button class="m-scan-btn" type="button" @click="onScan">
        <svg width="36" height="36" viewBox="0 0 20 20" fill="none">
          <path d="M3 7V5C3 3.89543 3.89543 3 5 3H7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          <path d="M13 3H15C16.1046 3 17 3.89543 17 5V7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          <path d="M17 13V15C17 16.1046 16.1046 17 15 17H13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          <path d="M7 17H5C3.89543 17 3 16.1046 3 15V13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          <line x1="5.5" y1="10" x2="14.5" y2="10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
        </svg>
        <span>扫资产标签</span>
      </button>
      <p class="m-scan-tip">支持二维码与新旧条形码标签<br />条码请保持平整，距标签 10-15cm 避免反光</p>
    </div>

    <!-- 手动输入兜底 -->
    <div class="m-manual">
      <input
        v-model="manualCode"
        class="m-manual-input"
        type="text"
        placeholder="或手动输入资产编码 / 序列号"
        enterkeyhint="search"
        @keyup.enter="search(manualCode.trim())"
      />
      <button
        class="m-manual-btn"
        type="button"
        :disabled="!manualCode.trim() || searching"
        @click="search(manualCode.trim())"
      >
        查询
      </button>
    </div>

    <!-- 查询状态 -->
    <div v-if="searching" class="m-skeleton-list">
      <div class="m-skeleton-card" />
      <div class="m-skeleton-card" style="height: 44px" />
      <div class="m-skeleton-card" style="height: 44px" />
    </div>
    <div v-else-if="errorMsg" class="m-error">
      <svg width="36" height="36" viewBox="0 0 20 20" fill="none">
        <circle cx="10" cy="10" r="7.5" stroke="currentColor" stroke-width="1.5" />
        <path d="M10 6V11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
        <circle cx="10" cy="13.5" r="0.75" fill="currentColor" />
      </svg>
      <p>{{ errorMsg }}</p>
    </div>

    <template v-if="asset">
      <div class="m-asset-card">
        <div class="m-asset-head">
          <span class="m-asset-name">{{ asset.name }}</span>
          <span class="m-status" :class="statusClass(asset.status)">{{ asset.statusLabel }}</span>
        </div>
        <div class="m-row"><span>资产编码</span><b>{{ asset.barcode }}</b></div>
        <div v-if="asset.sn" class="m-row"><span>序列号</span><b>{{ asset.sn }}</b></div>
        <div v-if="asset.spec" class="m-row"><span>细则</span><b>{{ asset.spec }}</b></div>
        <div v-if="asset.categoryName" class="m-row"><span>分类</span><b>{{ asset.categoryName }}</b></div>
        <div v-if="asset.locationName" class="m-row">
          <span>当前位置</span><b>{{ asset.locationName }}{{ asset.locationDetail ? ` · ${asset.locationDetail}` : '' }}</b>
        </div>
        <div v-if="asset.homeLocationName" class="m-row"><span>应归放位置</span><b>{{ asset.homeLocationName }}</b></div>
        <div v-if="asset.userName" class="m-row">
          <span>使用人</span><b>{{ asset.userName }}{{ asset.userDepartment ? ` · ${asset.userDepartment}` : '' }}</b>
        </div>
        <div v-if="asset.adminUserName" class="m-row"><span>资产管理员</span><b>{{ asset.adminUserName }}</b></div>
        <div v-if="asset.companyName" class="m-row"><span>所属公司</span><b>{{ asset.companyName }}</b></div>
        <div v-if="asset.purchaseDate" class="m-row"><span>购置日期</span><b>{{ asset.purchaseDate }}</b></div>
      </div>

      <!-- 操作日志 -->
      <div v-if="logs.length" class="m-logs">
        <div class="m-logs-title">操作记录（{{ logs.length }}）</div>
        <div v-for="(log, i) in logs" :key="log.id" class="m-log-row" :style="{ '--i': Math.min(i, 8) }">
          <div class="m-log-content">{{ log.content }}</div>
          <div class="m-log-time">{{ fmtTime(log.createdAt) }}</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { isInDingTalk, scanBarcode } from '@/utils/dingtalk'
import { assetApi } from '@/api/modules/asset'
import type { Asset, AssetLog } from '@/api/interface/asset'

const manualCode = ref('')
const searching = ref(false)
const asset = ref<Asset>()
const logs = ref<AssetLog[]>([])
const errorMsg = ref('')

const statusClass = (status: Asset['status']) =>
  ({ IDLE: 'info', IN_USE: 'ok', PENDING_CONFIRM: 'pending', DISCARD: 'bad' })[status] || 'info'

const fmtTime = (s: string) => {
  const d = new Date(s)
  return Number.isNaN(d.getTime())
    ? s
    : d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

/**
 * 生成扫码文本的候选变体：原始 / 去内部空白 / 大写归一。
 * 旧条形码（Code128 等）可能被扫码器附加空格或大小写偏差，逐个候选尝试直到命中。
 */
const candidates = (code: string): string[] =>
  Array.from(new Set([code, code.replace(/\s+/g, ''), code.toUpperCase()].filter(Boolean)))

/**
 * 按编码/序列号查资产（兼容新旧标签）：
 * keyword 为编码/名称/序列号三字段模糊匹配（后端契约），扫码内容含 "-" 时
 * 会被多关键词语法拆分，但子串 AND 语义仍能命中；精确匹配（编码→序列号）优先兜底。
 */
const search = async (code: string) => {
  if (!code || searching.value) return
  searching.value = true
  asset.value = undefined
  logs.value = []
  errorMsg.value = ''
  try {
    let hit: Asset | undefined
    for (const kw of candidates(code)) {
      const page = await assetApi.getAssets({ keyword: kw, page: 1, size: 20 })
      hit =
        page.records.find((a) => a.barcode === kw) ||
        page.records.find((a) => a.sn === kw) ||
        page.records.find((a) => a.barcode?.toUpperCase() === kw) ||
        page.records[0]
      if (hit) break
    }
    if (!hit) {
      errorMsg.value = `未找到编码为「${code}」的资产，请核对标签印刷字符后手动输入`
      return
    }
    asset.value = await assetApi.getAssetById(hit.id)
    logs.value = await assetApi.getAssetLogs(hit.id)
  } catch {
    /* 拦截器已提示 */
  } finally {
    searching.value = false
  }
}

const onScan = async () => {
  if (!isInDingTalk()) {
    errorMsg.value = '扫码需要在钉钉内打开使用，也可在上方手动输入编码查询'
    return
  }
  try {
    const text = await scanBarcode()
    manualCode.value = text
    await search(text)
  } catch (e) {
    errorMsg.value = `${e instanceof Error ? e.message : '扫码失败'}。可尝试对准条码、保持 10-15cm 距离避免反光，或直接手动输入标签上的编码`
  }
}
</script>

<style scoped>
.m-scan-entry {
  text-align: center;
  padding: 24px 0 12px;
}

.m-scan-btn {
  width: 124px;
  height: 124px;
  border-radius: 34px;
  border: none;
  background: var(--color-bg-2);
  color: var(--color-primary);
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: var(--text-base);
  font-weight: 600;
  box-shadow: var(--shadow-lg);
  transition: transform 0.18s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.m-scan-btn:active {
  transform: scale(0.93);
}

.m-scan-tip {
  margin-top: 14px;
  font-size: var(--text-sm);
  color: var(--color-text-3);
}

/* ---------- 手动输入 ---------- */
.m-manual {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}

.m-manual-input {
  flex: 1;
  min-width: 0;
  height: 40px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-2);
  color: var(--color-text-1);
  font-size: var(--text-base);
}

.m-manual-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.m-manual-btn {
  height: 40px;
  padding: 0 20px;
  border: none;
  border-radius: var(--radius-lg);
  background: var(--color-primary);
  color: var(--color-text-inverse);
  font-size: var(--text-base);
  transition: opacity 0.15s;
}

.m-manual-btn:disabled {
  opacity: 0.45;
}

.m-skeleton-list {
  margin-top: 16px;
}

/* ---------- 错误态 ---------- */
.m-error {
  text-align: center;
  padding: 28px 0;
  color: var(--color-text-3);
  font-size: var(--text-base);
}

.m-error svg {
  color: var(--color-warning);
  margin-bottom: 8px;
}

.m-error p {
  margin: 0;
}

/* ---------- 结果卡片 ---------- */
.m-asset-card {
  margin-top: 16px;
}

.m-asset-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  background: var(--color-bg-2);
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  padding: 12px 14px;
  margin-bottom: 4px;
}

.m-asset-name {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--color-text-1);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-asset-card .m-row {
  margin-bottom: 4px;
  border-radius: 0;
}

.m-asset-card .m-row:first-of-type {
  border-radius: 0;
}

.m-asset-card .m-row:last-of-type {
  border-radius: 0 0 var(--radius-xl) var(--radius-xl);
}

/* ---------- 日志 ---------- */
.m-logs {
  margin-top: 16px;
}

.m-logs-title {
  font-size: var(--text-sm);
  color: var(--color-text-3);
  margin: 8px 2px;
}

.m-log-row {
  background: var(--color-bg-2);
  border-radius: var(--radius-lg);
  padding: 10px 14px;
  margin-bottom: 6px;
  animation: m-enter 0.3s cubic-bezier(0.3, 0.7, 0.4, 1) both;
  animation-delay: calc(var(--i, 0) * 32ms);
}

.m-log-content {
  font-size: var(--text-sm);
  color: var(--color-text-1);
}

.m-log-time {
  margin-top: 4px;
  font-size: var(--text-xs);
  color: var(--color-text-3);
}
</style>
