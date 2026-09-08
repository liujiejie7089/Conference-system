<template>
  <div>
    <el-row :gutter="14">
      <el-col :span="6"><div class="metric-card"><div class="num">{{ metrics.pending }}</div><div class="label">📋 待审批</div></div></el-col>
      <el-col :span="6"><div class="metric-card" style="background:linear-gradient(135deg,#12a56b,#5fc89a)"><div class="num">{{ metrics.sessions }}</div><div class="label">💬 会话总数</div></div></el-col>
      <el-col :span="6"><div class="metric-card" style="background:linear-gradient(135deg,#f59e0b,#fbbf77)"><div class="num">{{ formatNum(metrics.tokens) }}</div><div class="label">⚡ 已耗词元</div></div></el-col>
      <el-col :span="6"><div class="metric-card" style="background:linear-gradient(135deg,#9333ea,#c084fc)"><div class="num">{{ formatNum(metrics.remaining) }}</div><div class="label">💎 剩余额度</div></div></el-col>
    </el-row>

    <div class="page-card" style="margin-top:14px;">
      <div class="page-title">最近 10 条操作记录 <span class="muted">(FR-H1 实时留痕)</span></div>
      <el-table :data="logs" stripe size="small">
        <el-table-column prop="time" label="时间" width="170"/>
        <el-table-column prop="action" label="动作" width="160"/>
        <el-table-column prop="statusText" label="状态" width="80"/>
        <el-table-column prop="detail" label="详情"/>
      </el-table>
    </div>

    <div class="page-card">
      <div class="page-title">最近 5 条账目 <span class="muted">(FR-D5 词元计量)</span></div>
      <el-table :data="bills" stripe size="small">
        <el-table-column prop="createdAt" label="时间" width="170"/>
        <el-table-column prop="model" label="模型" width="120"/>
        <el-table-column prop="inputTokens" label="输入词元" width="100"/>
        <el-table-column prop="outputTokens" label="输出词元" width="100"/>
        <el-table-column prop="totalTokens" label="合计" width="100"/>
        <el-table-column prop="costAmount" label="费用(元)" width="100"/>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { TodoAPI, LedgerAPI, SessionAPI } from '../api'

const props = defineProps<{ refreshKey: number }>()
const metrics = ref({ pending: 0, sessions: 0, tokens: 0, remaining: 0 })
const logs = ref<any[]>([])
const bills = ref<any[]>([])

function formatNum(n: any) {
  return Number(n || 0).toLocaleString()
}

async function load() {
  try {
    const [a, b, c, d] = await Promise.all([
      TodoAPI.count(), LedgerAPI.quota(), LedgerAPI.logs(1, 10), LedgerAPI.bills(1, 5),
    ])
    if (a.code === 0) metrics.value.pending = a.data?.pending || 0
    if (b.code === 0) {
      metrics.value.tokens = b.data.usedQuota || 0
      metrics.value.remaining = b.data.remainingQuota || 0
    }
    if (c.code === 0) logs.value = c.data || []
    if (d.code === 0) bills.value = d.data || []
    // sessions count via list endpoint
    const e: any = await SessionAPI.list(1, 1)
    if (e.code === 0) {
      // use length as approx if backend returns full list; otherwise fetch all
      const full: any = await SessionAPI.list(1, 100)
      if (full.code === 0) metrics.value.sessions = (full.data || []).length
    }
  } catch {}
}
watch(() => props.refreshKey, load, { immediate: true })
</script>