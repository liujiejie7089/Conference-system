<template>
  <div class="page-card">
    <div class="page-title">⚡ 词元账单 <span class="muted">(FR-D5 · 最近 100 条)</span></div>
    <el-table :data="rows" stripe size="small">
      <el-table-column prop="createdAt" label="时间" width="170"/>
      <el-table-column prop="model" label="模型" width="120"/>
      <el-table-column prop="sessionId" label="会话" width="160"/>
      <el-table-column prop="inputTokens" label="输入词元" width="100"/>
      <el-table-column prop="outputTokens" label="输出词元" width="100"/>
      <el-table-column prop="totalTokens" label="合计" width="100"/>
      <el-table-column prop="costAmount" label="费用(元)" width="100"/>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { LedgerAPI } from '../api'
const props = defineProps<{ refreshKey: number }>()
const rows = ref<any[]>([])
async function load() {
  const r: any = await LedgerAPI.bills(1, 100)
  if (r.code === 0) rows.value = r.data || []
}
watch(() => props.refreshKey, load, { immediate: true })
</script>