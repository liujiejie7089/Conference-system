<template>
  <view class="todo">
    <!-- 顶部标题栏 -->
    <view class="topbar">
      <text class="title">待办</text>
      <view class="actions">
        <text class="more">···</text>
        <switch :checked="onlyPending" color="#2f6fed" @change="onSwitch" style="transform:scale(0.7)" />
      </view>
    </view>
    <view class="sub">审批、审核、确认事项实时汇聚 · 点击即可处理</view>

    <!-- 待我处理 -->
    <view class="section">
      <view class="sec-title">待我处理 <text class="badge-num">{{ pending.length }}</text></view>
      <view v-if="pending.length === 0" class="empty">暂无待处理事项 🎉</view>
      <view v-for="t in pending" :key="t.id" class="card" @click="onTap(t)">
        <view class="row">
          <view class="ico" :style="{ background: t.iconBg || '#eaf1ff' }">{{ t.icon || '📌' }}</view>
          <view class="content">
            <view class="title-line">
              <text class="t-title">{{ t.title }}</text>
              <text class="badge" :class="badgeClass(t.status)">{{ t.statusText }}</text>
            </view>
            <view class="meta">{{ t.summary }}</view>
          </view>
        </view>
      </view>
    </view>

    <!-- 我的申请 -->
    <view class="section">
      <view class="sec-title">我的申请</view>
      <view v-if="applied.length === 0" class="empty">还没有提交过申请</view>
      <view v-for="t in applied" :key="t.id" class="card" @click="onTap(t)">
        <view class="row">
          <view class="ico" :style="{ background: t.iconBg || '#eaf1ff' }">{{ t.icon || '📌' }}</view>
          <view class="content">
            <view class="title-line">
              <text class="t-title">{{ t.title }}</text>
              <text class="badge" :class="badgeClass(t.status)">{{ t.statusText }}</text>
            </view>
            <view class="meta">{{ t.summary }}</view>
          </view>
        </view>
      </view>
    </view>

    <view class="footer-note">待办与消息通知实时联动,处理记录全程留痕可查</view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onShow } from 'vue';
import { todoApi, type TodoVo } from '@/api';

const onlyPending = ref(false);
const pending = ref<TodoVo[]>([]);
const applied = ref<TodoVo[]>([]);

async function load() {
  const [a, b] = await Promise.all([
    todoApi.list('pending'),
    todoApi.list('applied'),
  ]);
  if (a.code === 0) pending.value = a.data || [];
  if (b.code === 0) applied.value = b.data || [];
  await setBadge();
}

async function setBadge() {
  try {
    const r = await todoApi.count();
    const n = (r.code === 0 && r.data?.pending) ? r.data!.pending : pending.value.length;
    if (n > 0) {
      uni.setTabBarBadge({ index: 1, text: String(n) });
    } else {
      uni.removeTabBarBadge({ index: 1 });
    }
  } catch { /* ignore */ }
}

function badgeClass(st: number) {
  if (st === 1) return 'b-pending';
  if (st === 2) return 'b-running';
  if (st === 3) return 'b-rejected';
  if (st === 4) return 'b-done';
  return '';
}

function onSwitch(e: any) {
  onlyPending.value = !!e.detail.value;
  uni.showToast({ title: onlyPending.value ? '仅看待办中' : '显示全部', icon: 'none' });
}

function onTap(t: TodoVo) {
  // 演示:点击不同来源跳到不同页面
  if (t.source === 'skill_form' && t.sessionId) {
    uni.navigateTo({ url: `/pages/chat/index?sessionId=${t.sessionId}&title=${encodeURIComponent(t.title)}` });
  } else if (t.source === 'approval_ticket') {
    uni.navigateTo({ url: `/pages/chat/index?sessionId=${t.sessionId}&title=${encodeURIComponent(t.title)}` });
  } else {
    uni.showToast({ title: '打开详情(演示)', icon: 'none' });
  }
}

onMounted(load);
onShow(load); // 从其他页返回时刷新,并刷新 badge
</script>

<style scoped>
.todo {
  min-height: 100vh;
  background: #f4f6fa;
  padding: 16px 14px 40px;
  box-sizing: border-box;
}
.topbar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 4px 4px 0;
}
.title { font-size: 22px; font-weight: 800; color: #1f2329; }
.actions { display: flex; align-items: center; gap: 10px; }
.more { font-size: 18px; color: #6b7486; padding: 4px 6px; }
.sub {
  font-size: 12px; color: #8c93a3;
  margin: 6px 4px 18px;
}

.section { margin-bottom: 18px; }
.sec-title {
  font-size: 14px; font-weight: 700; color: #1f2329;
  margin: 4px 4px 10px;
  display: flex; align-items: center; gap: 6px;
}
.badge-num {
  font-size: 11px; background: #ff4d4f; color: #fff;
  border-radius: 10px; padding: 1px 7px; font-weight: 600;
}
.empty {
  text-align: center; color: #8c93a3; font-size: 12px; padding: 24px 0;
  background: #fff; border-radius: 12px; border: 1px dashed #e6eaf2;
}
.card {
  background: #fff; border-radius: 12px;
  padding: 14px 14px; margin-bottom: 10px;
  border: 1px solid #eef0f5;
  box-shadow: 0 1px 2px rgba(31,35,41,.03);
}
.row { display: flex; gap: 12px; align-items: flex-start; }
.ico {
  flex: 0 0 40px; width: 40px; height: 40px;
  border-radius: 10px; display: flex; align-items: center; justify-content: center;
  font-size: 20px;
}
.content { flex: 1; min-width: 0; }
.title-line {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 8px;
}
.t-title {
  font-size: 13.5px; font-weight: 600; color: #1f2329;
  line-height: 1.5; flex: 1;
}
.badge {
  flex-shrink: 0; font-size: 10.5px;
  padding: 2px 8px; border-radius: 10px; font-weight: 600;
  line-height: 1.4; margin-top: 2px;
}
.b-pending  { background: #fff4e6; color: #d97706; }
.b-running  { background: #eaf1ff; color: #2f6fed; }
.b-rejected { background: #ffeceb; color: #e23b3b; }
.b-done     { background: #e8f7ee; color: #12a56b; }
.meta {
  font-size: 11.5px; color: #8c93a3; margin-top: 6px; line-height: 1.55;
}
.footer-note {
  margin-top: 24px; text-align: center;
  font-size: 11.5px; color: #a8aeba;
}
</style>