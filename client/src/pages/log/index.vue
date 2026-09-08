<template>
  <view class="log">
    <view class="page-title">我的操作记录</view>

    <view class="card">
      <view v-if="!records.length && !loading" class="empty">暂无操作记录</view>
      <view v-for="(r, i) in records" :key="i" class="row">
        <view class="log-info">
          <view class="log-time">{{ formatTime(r.time) }}</view>
          <view class="log-action">{{ r.action }}</view>
        </view>
        <text class="badge" :class="r.status">{{ r.statusText }}</text>
        <view v-if="i < records.length - 1" class="divider"></view>
      </view>
      <view class="muted tip">记录不可篡改，保存 ≥ 3 年</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { ledgerApi, type LogVo } from '@/api';

const records = ref<LogVo[]>([]);
const loading = ref(true);

async function load() {
  loading.value = true;
  try {
    const r = await ledgerApi.logs(1, 50);
    if (r.code === 0) records.value = r.data || [];
  } finally {
    loading.value = false;
  }
}
onMounted(load);
onShow(load);

function formatTime(t: string) {
  if (!t) return '';
  const d = new Date(t);
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}
</script>

<style scoped>
.log { padding: 14px; background: #f4f6fa; min-height: 100vh; }
.page-title { font-size: 18px; font-weight: 700; margin-bottom: 12px; }
.card { background: #fff; border-radius: 14px; padding: 14px; margin-bottom: 12px; border: 1px solid #e6eaf2; }
.row { display: flex; justify-content: space-between; align-items: center; padding: 11px 0; font-size: 13px; }
.log-info { flex: 1; }
.log-time { font-size: 13px; color: #1a2233; }
.log-action { font-size: 11px; color: #6b7486; margin-top: 2px; }
.badge { font-size: 10px; border-radius: 4px; padding: 2px 6px; }
.badge.ok { color: #12a56b; background: #e7f7f0; }
.badge.wait { color: #f59e0b; background: #fef4e6; }
.divider { height: 1px; background: #e6eaf2; margin-top: 11px; }
.muted { color: #6b7486; font-size: 12px; }
.tip { margin-top: 8px; }
.empty { text-align: center; color: #6b7486; padding: 32px; font-size: 13px; }
</style>
