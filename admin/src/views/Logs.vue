<template>
  <div class="page-card">
    <div class="page-title">🧾 全量操作记录 <span class="muted">(FR-H1 留痕 · 最近 200 条)</span>
      <el-select v-model="actionFilter" placeholder="按动作筛选" clearable size="small" style="width:160px;float:right;margin-right:8px;">
        <el-option v-for="a in actions" :key="a" :label="a" :value="a"/>
      </el-select>
    </div>
    <el-table :data="filtered" stripe size="small">
      <el-table-column prop="time" label="时间" width="170"/>
      <el-table-column prop="action" label="动作" width="180"/>
      <el-table-column prop="statusText" label="状态" width="80"/>
      <el-table-column prop="detail" label="详情"/>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { LedgerAPI } from '../api'
const props = defineProps<{ refreshKey: number }>()
const rows = ref<any[]>([])
const actionFilter = ref('')
async function load() {
  const r: any = await LedgerAPI.logs(1, 200)
  if (r.code === 0) rows.value = r.data || []
}
const actions = computed(() => Array.from(new Set((rows.value || []).map((x: any) => x.action).filter(Boolean))))
const filtered = computed(() => actionFilter.value ? rows.value.filter((r: any) => r.action === actionFilter.value) : rows.value)
watch(() => props.refreshKey, load, { immediate: true })
</script>