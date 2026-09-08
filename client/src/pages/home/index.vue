<template>
  <view class="home">
    <view class="greet">你好，{{ displayName }} 👋</view>
    <view class="muted">今天想让我帮你做点什么？</view>

    <view class="card">
      <view class="row-between">
        <text class="sec-title">我的额度</text>
        <text class="muted">企业套餐 · {{ memberLevel }}</text>
      </view>
      <view class="quota-line">
        <text class="quota-num">{{ formatNum(quota.remainingQuota) }}</text>
        <text class="muted">/ {{ formatNum(quota.totalQuota) }} 词元（本月套餐）</text>
      </view>
      <view class="progress" :class="{ low: usedPercent > 80 }">
        <view class="progress-bar" :style="{ width: usedPercent + '%' }"></view>
      </view>
      <view class="muted">今日免费额度剩 {{ formatNum(dailyFree) }} · 额度变化实时留痕</view>
      <view class="btn-row">
        <button class="btn small" @click="goBill">用量账单</button>
        <button class="btn ghost small" @click="goMember">购买词元包</button>
      </view>
    </view>

    <view class="sec-title">快捷技能</view>
    <view class="grid">
      <view v-for="s in skills" :key="s.name" class="grid-item" @click="openSkill(s.name)">
        <view class="ico">{{ s.icon }}</view>
        <view class="name">{{ s.name }}</view>
      </view>
    </view>

    <view class="sec-title">推荐专家</view>
    <view class="card">
      <view v-for="(a, i) in topAgents" :key="a.id" class="expert-row" @click="goChat(a)">
        <view class="avatar">{{ a.icon || a.name?.[0] || '?' }}</view>
        <view class="expert-info">
          <view class="expert-name">{{ a.name }}</view>
          <view class="muted">{{ a.description || '—' }}</view>
        </view>
        <text class="muted">›</text>
        <view v-if="i < topAgents.length - 1" class="divider"></view>
      </view>
    </view>

    <view class="sec-title">最近会话</view>
    <view v-if="!sessions.length" class="empty">还没有会话，去专家页发起一个吧</view>
    <view class="card" v-else>
      <view v-for="(s, i) in sessions" :key="s.id" class="session-row" @click="goSession(s)">
        <view class="session-title">{{ s.title }}</view>
        <view class="session-time">{{ formatTime(s.lastMessageAt || s.createdAt) }}</view>
        <view v-if="i < sessions.length - 1" class="divider"></view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useUserStore } from '@/stores/user';
import { agentApi, sessionApi, ledgerApi, type AgentVo, type SessionVo, type QuotaVo } from '@/api';

const userStore = useUserStore();
const displayName = computed(() => userStore.user?.displayName || userStore.user?.username || '访客');
const memberLevel = computed(() => {
  const total = quota.value.totalQuota || 0;
  if (total >= 500000) return '金牌会员';
  if (total >= 100000) return '银牌会员';
  return '铜牌会员';
});

const agents = ref<AgentVo[]>([]);
const topAgents = computed(() => agents.value.slice(0, 4));
const sessions = ref<SessionVo[]>([]);
const quota = ref<Partial<QuotaVo>>({});
const dailyFree = ref(2000);

const skills = [
  { icon: '📄', name: '公文写作' },
  { icon: '⚖️', name: '合同初审' },
  { icon: '📊', name: '数据分析' },
  { icon: '🏛️', name: '办事指引' }
];

const usedPercent = computed(() => {
  if (!quota.value.totalQuota) return 0;
  return Math.min(100, Math.round(((quota.value.usedQuota || 0) / quota.value.totalQuota) * 100));
});

async function load() {
  if (!userStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' });
    return;
  }
  try {
    const [a, s, q] = await Promise.all([
      agentApi.list(),
      sessionApi.list(1, 5),
      ledgerApi.quota()
    ]);
    if (a.code === 0) agents.value = (a.data || []).map(x => ({ ...x, icon: pickIcon(x.name) }));
    if (s.code === 0) sessions.value = s.data || [];
    if (q.code === 0 && q.data) quota.value = q.data;
  } catch { /* ignore */ }
}
onMounted(load);
onShow(load);

function pickIcon(name?: string | null): string {
  if (!name) return '🧑‍💼';
  if (name.includes('政策') || name.includes('咨询')) return '🏛️';
  if (name.includes('法律') || name.includes('合同')) return '⚖️';
  if (name.includes('开办') || name.includes('企业')) return '🏢';
  if (name.includes('公文') || name.includes('办文')) return '📝';
  if (name.includes('差旅') || name.includes('travel')) return '✈️';
  if (name.includes('审批')) return '📋';
  return '🧑‍💼';
}

function goChat(a: AgentVo) {
  uni.navigateTo({ url: `/pages/chat/index?agentId=${a.id}&agentName=${encodeURIComponent(a.name)}` });
}
function goSession(s: SessionVo) {
  uni.navigateTo({ url: `/pages/chat/index?sessionId=${s.id}` });
}
function goBill() {
  uni.navigateTo({ url: '/pages/bill/index' });
}
function goMember() {
  uni.navigateTo({ url: '/pages/member/index' });
}
function openSkill(name: string) {
  uni.navigateTo({ url: `/pages/skill/index?name=${encodeURIComponent(name)}` });
}
function formatNum(n?: number): string {
  if (n == null) return '--';
  return Number(n).toLocaleString('zh-CN');
}
function formatTime(t: string) {
  if (!t) return '';
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}
function toast(msg: string) {
  uni.showToast({ title: msg, icon: 'none' });
}
</script>

<style scoped>
.home { padding: 14px; background: #f4f6fa; min-height: 100vh; }
.greet { font-size: 18px; font-weight: 800; margin-bottom: 2px; }
.muted { color: #6b7486; font-size: 12px; }
.sec-title { font-size: 14px; font-weight: 700; margin: 14px 0 10px; }
.card { background: #fff; border-radius: 14px; padding: 14px; margin-bottom: 12px; border: 1px solid #e6eaf2; }
.row-between { display: flex; justify-content: space-between; align-items: baseline; }
.quota-line { display: flex; align-items: baseline; gap: 8px; margin-top: 6px; }
.quota-num { font-size: 26px; font-weight: 800; color: #1e4fc0; }
.progress { height: 8px; border-radius: 4px; background: #e9edf5; overflow: hidden; margin: 8px 0 4px; }
.progress-bar { height: 100%; background: linear-gradient(90deg, #2f6fed, #5b8cff); border-radius: 4px; transition: width .3s; }
.progress.low .progress-bar { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
.btn-row { margin-top: 10px; display: flex; gap: 8px; }
.btn { background: #2f6fed; color: #fff; border: none; border-radius: 20px; padding: 8px 18px; font-size: 13px; }
.btn.ghost { background: #fff; color: #2f6fed; border: 1px solid #2f6fed; }
.btn.small { padding: 5px 12px; font-size: 12px; }
.grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 12px; }
.grid-item { background: #fff; border: 1px solid #e6eaf2; border-radius: 12px; padding: 12px 4px; text-align: center; }
.grid-item .ico { font-size: 24px; }
.grid-item .name { font-size: 11px; margin-top: 4px; }
.expert-row { display: flex; align-items: center; gap: 10px; padding: 10px 0; cursor: pointer; }
.avatar { width: 42px; height: 42px; border-radius: 12px; background: linear-gradient(135deg, #eaf1ff, #d8e6ff); display: flex; align-items: center; justify-content: center; font-size: 22px; flex-shrink: 0; }
.expert-info { flex: 1; }
.expert-name { font-weight: 700; font-size: 13px; }
.divider { height: 1px; background: #e6eaf2; margin-top: 10px; }
.empty { background: #fff; padding: 24px; border-radius: 12px; text-align: center; color: #6b7486; font-size: 13px; }
.session-row { padding: 11px 0; cursor: pointer; }
.session-title { font-size: 13px; }
.session-time { margin-top: 4px; font-size: 11px; color: #6b7486; }
</style>
