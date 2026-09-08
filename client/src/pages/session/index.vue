<template>
  <view class="session">
    <view class="page-title">会话历史</view>
    <view v-if="!list.length && !loading" class="empty">
      暂无会话
    </view>
    <view v-for="s in list" :key="s.id" class="row" @click="go(s)">
      <view class="title">{{ s.title }}</view>
      <view class="meta">{{ formatTime(s.lastMessageAt || s.createdAt) }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { sessionApi, type SessionVo } from '@/api';

const list = ref<SessionVo[]>([]);
const loading = ref(true);

async function load() {
  loading.value = true;
  try {
    const r = await sessionApi.list(1, 50);
    if (r.code === 0) list.value = r.data || [];
  } finally {
    loading.value = false;
  }
}
onMounted(load);
onShow(load);

function go(s: SessionVo) {
  uni.navigateTo({ url: `/pages/chat/index?sessionId=${s.id}` });
}
function formatTime(t: string) {
  if (!t) return '';
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}
</script>

<style scoped>
.session { padding: 16px; }
.page-title { font-size: 20px; font-weight: 600; margin-bottom: 16px; }
.empty { text-align: center; color: #999; padding: 64px; }
.row {
  background: #fff; padding: 16px; border-radius: 8px; margin-bottom: 8px;
}
.title { font-size: 14px; font-weight: 500; }
.meta { margin-top: 4px; font-size: 11px; color: #999; }
</style>
