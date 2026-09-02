<template>
  <div class="m-page">
    <!-- 扫码入口 -->
    <div class="m-scan-entry">
      <button class="m-scan-btn" type="button" @click="onScan">
        <span class="m-scan-icon">📷</span>
        <span>扫资产条码</span>
      </button>
      <p class="m-scan-tip">扫描资产上的二维码 / 条形码，立即查看资产详情</p>
    </div>

    <!-- 手动输入条码兜底 -->
    <div class="m-manual">
      <input
        v-model="manualCode"
        class="m-input"
        type="text"
        placeholder="或手动输入资产编码"
        enterkeyhint="search"
        @keyup.enter="searchByBarcode(manualCode.trim())"
      />
      <button
        class="m-search-btn"
        type="button"
        :disabled="!manualCode.trim() || searching"
        @click="searchByBarcode(manualCode.trim())"
      >
        查询
      </button>
    </div>

    <!-- 查询结果 -->
    <div v-if="searching" class="m-empty">查询中…</div>
    <div v-else-if="errorMsg" class="m-error">{{ errorMsg }}</div>

    <template v-if="asset">
      <div class="m-asset-card">
        <div class="m-asset-head">
          <span class="m-asset-name">{{ asset.name }}</span>
          <span class="m-card-status" :class="statusClass(asset.status)">{{ asset.statusLabel }}</span>
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
        <div v-for="log in logs" :key="log.id" class="m-log-row">
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
  ({ IDLE: 'ok', IN_USE: 'pending', DISCARDED: 'bad' })[status] || 'info'

const fmtTime = (s: string) => {
  const d = new Date(s)
  return Number.isNaN(d.getTime())
    ? s
    : d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

const searchByBarcode = async (barcode: string) => {
  if (!barcode || searching.value) return
  searching.value = true
  asset.value = undefined
  logs.value = []
  errorMsg.value = ''
  try {
    const page = await assetApi.getAssets({ keyword: barcode, page: 1, size: 20 })
    // keyword 是模糊匹配，优先精确命中编码
    const hit = page.records.find((a) => a.barcode === barcode) || page.records[0]
    if (!hit) {
      errorMsg.value = `未找到编码为「${barcode}」的资产`
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
    errorMsg.value = '扫码需要在钉钉内打开使用'
    return
  }
  try {
    const text = await scanBarcode()
    manualCode.value = text
    await searchByBarcode(text)
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '扫码失败'
  }
}
</script>

<style scoped>
.m-page {
  padding: 16px 12px 20px;
}

.m-scan-entry {
  text-align: center;
  padding: 20px 0 8px;
}

.m-scan-btn {
  width: 132px;
  height: 132px;
  border-radius: 50%;
  border: none;
  background: var(--el-color-primary);
  color: #fff;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.35);
  transition: transform 0.1s;
}

.m-scan-btn:active {
  transform: scale(0.96);
}

.m-scan-icon {
  font-size: 34px;
  line-height: 1;
}

.m-scan-tip {
  margin-top: 14px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.m-manual {
  display: flex;
  gap: 8px;
  margin-top: 20px;
}

.m-input {
  flex: 1;
  height: 40px;
  padding: 0 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.m-input:focus {
  outline: none;
  border-color: var(--el-color-primary);
}

.m-search-btn {
  height: 40px;
  padding: 0 20px;
  border: none;
  border-radius: 8px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 14px;
}

.m-search-btn:disabled {
  opacity: 0.5;
}

.m-error {
  text-align: center;
  padding: 24px 0;
  color: var(--el-color-danger);
  font-size: 14px;
}

.m-empty {
  text-align: center;
  padding: 48px 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.m-asset-card {
  margin-top: 16px;
}

.m-asset-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  background: var(--el-bg-color);
  border-radius: 10px 10px 0 0;
  padding: 12px 14px;
  margin-bottom: 4px;
}

.m-asset-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-card-status {
  flex-shrink: 0;
  font-size: 12px;
}

.m-card-status.pending { color: var(--el-color-warning); }
.m-card-status.ok { color: var(--el-color-success); }
.m-card-status.bad { color: var(--el-color-danger); }
.m-card-status.info { color: var(--el-text-color-secondary); }

.m-row {
  display: flex;
  gap: 12px;
  padding: 10px 14px;
  background: var(--el-bg-color);
  margin-bottom: 4px;
  font-size: 14px;
}

.m-row:last-child {
  border-radius: 0 0 10px 10px;
}

.m-row span {
  flex-shrink: 0;
  width: 88px;
  color: var(--el-text-color-secondary);
}

.m-row b {
  flex: 1;
  font-weight: 400;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.m-logs {
  margin-top: 16px;
}

.m-logs-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 8px 2px;
}

.m-log-row {
  background: var(--el-bg-color);
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 6px;
}

.m-log-content {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.m-log-time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
