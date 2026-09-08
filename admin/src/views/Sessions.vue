<template>
  <div class="page-card">
    <div class="page-title">💬 会话列表 <span class="muted">(最近 100 条)</span></div>
    <el-table :data="rows" stripe size="small">
      <el-table-column prop="id" label="会话ID" width="180"/>
      <el-table-column prop="title" label="标题" min-width="200"/>
      <el-table-column prop="agentId" label="专家" width="100"/>
      <el-table-column prop="status" label="状态" width="80"/>
      <el-table-column prop="lastMessageAt" label="最后消息" width="170"/>
      <el-table-column prop="createdAt" label="创建时间" width="170"/>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { SessionAPI } from '../api'
const props = defineProps<{ refreshKey: number }>()
const rows = ref<any[]>([])
async function load() {
  const r: any = await SessionAPI.list(1, 100)
  if (r.code === 0) rows.value = r.data || []
}
watch(() => props.refreshKey, load, { immediate: true })
</script>