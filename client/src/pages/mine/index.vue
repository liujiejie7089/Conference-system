<template>
  <view class="mine">
    <view class="card me-head">
      <view class="avatar">👤</view>
      <view class="me-info">
        <view class="name-line">{{ user?.displayName || user?.username }} <text class="tag">已实名</text></view>
        <view class="muted">企业成员身份 · {{ tenantName }} <text class="switch" @click="toast('已打开身份切换（演示）')">切换</text></view>
      </view>
    </view>

    <view class="card">
      <view class="sec-title">我的额度</view>
      <view class="progress" :class="{ low: usedPercent > 80 }">
        <view class="progress-bar" :style="{ width: usedPercent + '%' }"></view>
      </view>
      <view class="muted quota-text">已用 {{ formatNum(quota.usedQuota) }} / {{ formatNum(quota.totalQuota) }} （剩余 {{ formatNum(quota.remainingQuota) }}）</view>
    </view>

    <view class="card">
      <view class="sec-title">我的知识库</view>
      <view v-for="(f, i) in kbFiles" :key="i" class="row">
        <text>{{ f.icon }} {{ f.name }}</text>
        <text class="badge" :class="f.status">{{ f.statusText }}</text>
        <view v-if="i < kbFiles.length - 1" class="divider"></view>
      </view>
      <view class="btn-row">
        <button class="btn ghost small" @click="upload">＋ 上传资料入知识库</button>
      </view>
    </view>

    <view class="card">
      <view class="sec-title">账单与留痕</view>
      <view class="row" @click="goBill">
        <text>📒 用量账单</text>
        <text class="muted">本月 {{ formatNum(quota.usedQuota) }} 词元 ›</text>
      </view>
      <view class="divider"></view>
      <view class="row" @click="goLog">
        <text>🧾 我的操作记录</text>
        <text class="muted">全程可查 ›</text>
      </view>
      <view class="divider"></view>
      <view class="row" @click="toast('反馈问题入口：错误 / 有害 / 侵权（演示）')">
        <text>🛡️ 投诉与纠错</text>
        <text class="muted">›</text>
      </view>
    </view>

    <view class="card">
      <view class="row" @click="go('/pages/home/index')">
        <text>🏠 返回首页</text>
        <text class="muted">›</text>
      </view>
      <view class="divider"></view>
      <view class="row" @click="logout">
        <text>🚪 退出登录</text>
        <text class="muted">›</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useUserStore } from '@/stores/user';
import { ledgerApi, type QuotaVo } from '@/api';

const userStore = useUserStore();
const user = userStore.user;
const quota = ref<Partial<QuotaVo>>({});

const tenantName = computed(() => {
  const tid = user?.tenantId;
  if (tid === 1) return '德阳云创科技有限公司';
  return `租户 #${tid}`;
});

const kbFiles = ref([
  { icon: '📄', name: '2026年产业扶持政策汇编.pdf', status: 'ok', statusText: '已入库' },
  { icon: '📊', name: '产品销售明细.xlsx', status: 'ok', statusText: '已入库' },
  { icon: '📝', name: '会议纪要0905.docx', status: 'wait', statusText: '解析中' }
]);

const usedPercent = computed(() => {
  if (!quota.value.totalQuota) return 0;
  return Math.min(100, Math.round(((quota.value.usedQuota || 0) / quota.value.totalQuota) * 100));
});

async function load() {
  try {
    const r = await ledgerApi.quota();
    if (r.code === 0 && r.data) quota.value = r.data;
  } catch { /* ignore */ }
}
onMounted(load);
onShow(load);

function go(url: string) { uni.switchTab({ url }); }
function goBill() { uni.navigateTo({ url: '/pages/bill/index' }); }
function goLog() { uni.navigateTo({ url: '/pages/log/index' }); }
function upload() { uni.showToast({ title: '已打开文件上传（演示，支持 PDF/Word/Excel/图片）', icon: 'none' }); }
function toast(msg: string) { uni.showToast({ title: msg, icon: 'none' }); }
function logout() {
  uni.showModal({
    title: '提示',
    content: '确定退出登录？',
    success: (res) => { if (res.confirm) userStore.logout(); }
  });
}
function formatNum(n?: number): string {
  if (n == null) return '0';
  return Number(n).toLocaleString('zh-CN');
}
</script>

<style scoped>
.mine { padding: 14px; background: #f4f6fa; min-height: 100vh; }
.card { background: #fff; border-radius: 14px; padding: 14px; margin-bottom: 12px; border: 1px solid #e6eaf2; }
.me-head { display: flex; align-items: center; gap: 12px; }
.avatar { width: 54px; height: 54px; font-size: 28px; border-radius: 16px; background: linear-gradient(135deg, #eaf1ff, #d8e6ff); display: flex; align-items: center; justify-content: center; }
.me-info { flex: 1; }
.name-line { font-weight: 800; font-size: 16px; }
.tag { display: inline-block; font-size: 10px; color: #2f6fed; background: #eaf1ff; border-radius: 4px; padding: 2px 6px; margin-left: 4px; }
.muted { color: #6b7486; font-size: 12px; }
.switch { color: #2f6fed; cursor: pointer; margin-left: 4px; }
.sec-title { font-size: 14px; font-weight: 700; margin-bottom: 10px; }
.progress { height: 8px; border-radius: 4px; background: #e9edf5; overflow: hidden; margin-bottom: 8px; }
.progress-bar { height: 100%; background: linear-gradient(90deg, #2f6fed, #5b8cff); border-radius: 4px; transition: width .3s; }
.progress.low .progress-bar { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
.quota-text { margin-top: 4px; }
.row { display: flex; justify-content: space-between; align-items: center; padding: 11px 0; font-size: 13px; cursor: pointer; }
.badge { font-size: 10px; border-radius: 4px; padding: 2px 6px; }
.badge.ok { color: #12a56b; background: #e7f7f0; }
.badge.wait { color: #f59e0b; background: #fef4e6; }
.divider { height: 1px; background: #e6eaf2; margin: 0; }
.btn-row { margin-top: 10px; }
.btn { background: #2f6fed; color: #fff; border: none; border-radius: 20px; padding: 8px 18px; font-size: 13px; }
.btn.ghost { background: #fff; color: #2f6fed; border: 1px solid #2f6fed; }
.btn.small { padding: 5px 12px; font-size: 12px; }
</style>
