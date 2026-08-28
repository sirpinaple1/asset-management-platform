<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { migrationApi } from '@/api/modules/migration'
import { MIGRATION_REQUIRED_ROLE } from '@/api/interface/migration'
import type { MigrationResult } from '@/api/interface/migration'

/**
 * M08 历史数据迁移工具页：触发 POST /v1/migration/run 并展示执行报告。
 * 一次性管理员工具（旧系统 Excel → 新系统基线），放基础设置分组；
 * 后端双重门禁（app.migration.enabled + asset-资产管理员 角色）为权威校验，前端只做按钮预检。
 */
defineOptions({ name: 'basedata-migration' })

const userStore = useUserStore()

const running = ref(false)
const result = ref<MigrationResult | null>(null)

/* ---------------- 角色预检（me 未加载时不禁用，交由后端裁决） ---------------- */
const hasRole = computed(() => {
  const roles = userStore.me?.roles
  return !roles || roles.includes(MIGRATION_REQUIRED_ROLE)
})

onMounted(() => {
  if (!userStore.me) userStore.loadMe().catch(() => undefined)
})

/* ---------------- 执行中计时（迁移耗时可能数十秒，给用户进度感知） ---------------- */
const elapsed = ref(0)
let timer: ReturnType<typeof setInterval> | undefined

const startTimer = () => {
  elapsed.value = 0
  timer = setInterval(() => {
    elapsed.value += 1
  }, 1000)
}
const stopTimer = () => {
  if (timer) {
    clearInterval(timer)
    timer = undefined
  }
}
onBeforeUnmount(stopTimer)

const secondsText = (seconds: number) =>
  seconds >= 60 ? `${Math.floor(seconds / 60)}分${seconds % 60}秒` : `${seconds}秒`

const elapsedText = computed(() => secondsText(elapsed.value))

/* ---------------- 触发迁移 ---------------- */
const handleRun = async () => {
  try {
    await ElMessageBox.confirm(
      '迁移为幂等操作，可重复运行。执行时资产/单据将按 Excel 基线 upsert（本地测试数据会被重置），历史导入的日志将清除后重写。确认执行？',
      '执行历史数据迁移',
      { confirmButtonText: '执行迁移', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return // 用户取消
  }

  running.value = true
  result.value = null
  startTimer()
  try {
    result.value = await migrationApi.run()
    ElMessage.success('迁移执行完成')
  } catch {
    // 业务失败（未启用 503 / 无权限 403 等）已由 axios 拦截器统一提示，此处保留原结果面板
  } finally {
    running.value = false
    stopTimer()
  }
}

/* ---------------- 结果展示 ---------------- */
/** 后端 LocalDateTime 序列化为 ISO（yyyy-MM-ddTHH:mm:ss[.fff]），截断为可读格式 */
const formatTime = (t?: string) => (t ? t.replace('T', ' ').slice(0, 19) : '—')

const durationSeconds = computed(() => {
  if (!result.value) return 0
  const start = new Date(result.value.startedAt).getTime()
  const end = new Date(result.value.finishedAt).getTime()
  if (!Number.isFinite(start) || !Number.isFinite(end)) return 0
  return Math.max(0, Math.round((end - start) / 1000))
})
</script>

<template>
  <div class="page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">数据迁移</h2>
        <p class="page-desc">
          将旧系统 2026-08-18 导出的 4 份 Excel（资产 / 领用单 / 调拨单 / 操作日志）一次性导入，建立历史数据基线
        </p>
      </div>

      <el-alert type="info" :closable="false" class="intro-alert">
        <template #title>迁移说明</template>
        <div class="intro-list">
          <p>· 顺序执行五个阶段：基础数据 → 资产主数据 → 领用单（ARE）→ 调拨单（ATR）→ 操作日志</p>
          <p>· 幂等可重跑：资产 / 单据按编码 upsert，重复运行结果一致</p>
          <p>· 门禁：需「{{ MIGRATION_REQUIRED_ROLE }}」角色，且后端迁移开关（app.migration.enabled）已开启</p>
          <p>· 未匹配到账号的旧系统使用人会按姓名自动创建账号，初始密码为后端配置默认值，改密后方可登录</p>
        </div>
      </el-alert>

      <div class="action-bar">
        <el-button type="primary" :loading="running" :disabled="!hasRole" @click="handleRun">
          {{ running ? `迁移执行中… ${elapsedText}` : '执行迁移' }}
        </el-button>
        <span v-if="!hasRole" class="role-hint">需「{{ MIGRATION_REQUIRED_ROLE }}」角色</span>
        <span v-else-if="running" class="running-hint">正在处理 2500+ 行数据，请勿关闭页面</span>
      </div>

      <template v-if="result">
        <el-divider content-position="left">执行结果</el-divider>

        <div class="result-meta">
          开始 {{ formatTime(result.startedAt) }}　·　结束 {{ formatTime(result.finishedAt) }}　·　耗时
          {{ secondsText(durationSeconds) }}
        </div>

        <el-table :data="result.phases" row-key="phase" size="default">
          <el-table-column prop="phase" label="阶段" min-width="240" />
          <el-table-column prop="rows" label="总行数" width="100" align="right" />
          <el-table-column prop="inserted" label="新增" width="100" align="right" />
          <el-table-column prop="updated" label="更新" width="100" align="right" />
          <el-table-column prop="skipped" label="跳过" width="100" align="right" />
        </el-table>

        <div v-if="result.createdUsers.length" class="result-section">
          <div class="section-title">
            自动创建账号（{{ result.createdUsers.length }}）
            <span class="section-sub">初始密码为后端配置默认值，改密后方可登录</span>
          </div>
          <div class="user-tags">
            <el-tag v-for="name in result.createdUsers" :key="name" class="user-tag" type="info">
              {{ name }}
            </el-tag>
          </div>
        </div>

        <div class="result-section">
          <el-alert v-if="result.warnings.length" type="warning" :closable="false">
            <template #title>警告 {{ result.warnings.length }} 条，需人工复核</template>
            <div class="warning-list">
              <div v-for="(warning, i) in result.warnings" :key="i" class="warning-item">
                {{ warning }}
              </div>
            </div>
          </el-alert>
          <el-alert v-else type="success" :closable="false" title="无警告，全部按规则映射完成" />
        </div>
      </template>

      <el-empty v-else-if="!running" description="尚未执行迁移" />
    </div>
  </div>
</template>

<style scoped>
.page-container {
  background: var(--color-bg-2);
  border-radius: var(--radius-xl);
  padding: 24px;
  box-shadow: var(--shadow-md);
  max-width: 960px;
}

.page-header {
  margin-bottom: 16px;
}

.page-title {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-text-1);
  margin: 0 0 4px;
}

.page-desc {
  font-size: var(--text-sm);
  color: var(--color-text-3);
  margin: 0;
}

.intro-alert {
  margin-bottom: 16px;
}

.intro-list p {
  margin: 2px 0;
  font-size: var(--text-sm);
  line-height: 22px;
}

.action-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.role-hint {
  font-size: var(--text-sm);
  color: var(--color-warning-text);
}

.running-hint {
  font-size: var(--text-sm);
  color: var(--color-text-3);
}

.result-meta {
  font-size: var(--text-sm);
  color: var(--color-text-2);
  margin-bottom: 12px;
}

.result-section {
  margin-top: 16px;
}

.section-title {
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--color-text-1);
  margin-bottom: 8px;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.section-sub {
  font-size: var(--text-xs);
  font-weight: 400;
  color: var(--color-text-3);
}

.user-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-height: 160px;
  overflow-y: auto;
  padding: 4px 0;
}

.user-tag {
  font-size: var(--text-sm);
}

.warning-list {
  max-height: 240px;
  overflow-y: auto;
}

.warning-item {
  font-size: var(--text-sm);
  line-height: 22px;
  word-break: break-all;
}
</style>
