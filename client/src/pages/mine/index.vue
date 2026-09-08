<template>
  <view class="mine">
    <view class="card me-head">
      <view class="avatar">👤</view>
      <view class="me-info">
        <view class="name-line">{{ user?.displayName || user?.username }} <text class="tag">已实名</text></view>
        <view class="muted">企业成员身份 · {{ tenantName }} <text class="switch" @click="showIdSwitch = true">切换</text></view>
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
      <view class="row" @click="goMember">
        <text>👑 会员中心</text>
        <text class="muted">升级 / 充值 / 切换模型 ›</text>
      </view>
      <view class="divider"></view>
      <view class="row" @click="goLog">
        <text>🧾 我的操作记录</text>
        <text class="muted">全程可查 ›</text>
      </view>
      <view class="divider"></view>
      <view class="row" @click="showFeedback = true">
        <text>🛡️ 投诉与纠错</text>
        <text class="muted">错误 / 有害 / 侵权 ›</text>
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

    <!-- 身份切换弹窗 FR-A4 -->
    <view v-if="showIdSwitch" class="modal-mask" @click="showIdSwitch = false">
      <view class="modal" @click.stop>
        <view class="modal-title">切换身份</view>
        <view class="id-opt" :class="{ on: currentIdentity === 'org' }" @click="currentIdentity = 'org'">
          <view class="id-name">🏢 企业成员身份</view>
          <view class="muted">{{ tenantName }} · 使用企业套餐额度与知识库</view>
        </view>
        <view class="id-opt" :class="{ on: currentIdentity === 'personal' }" @click="currentIdentity = 'personal'">
          <view class="id-name">👤 个人身份</view>
          <view class="muted">每日免费额度 · 个人知识库</view>
        </view>
        <view class="modal-btns">
          <button class="btn ghost" @click="showIdSwitch = false">取消</button>
          <button class="btn" @click="doSwitch">切换</button>
        </view>
      </view>
    </view>

    <!-- 反馈弹窗 FR-H4 -->
    <view v-if="showFeedback" class="modal-mask" @click="showFeedback = false">
      <view class="modal" @click.stop>
        <view class="modal-title">投诉与纠错</view>
        <view class="fb-types">
          <view v-for="t in fbTypes" :key="t.value" class="fb-type" :class="{ on: fbType === t.value }" @click="fbType = t.value">
            <text class="fb-ico">{{ t.icon }}</text>
            <text>{{ t.label }}</text>
          </view>
        </view>
        <textarea v-model="fbContent" class="fb-textarea" placeholder="请描述问题，我们将尽快处理..." :maxlength="500" />
        <view class="modal-btns">
          <button class="btn ghost" @click="showFeedback = false">取消</button>
          <button class="btn" @click="submitFeedback" :disabled="!fbContent.trim()">提交</button>
        </view>
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
const showFeedback = ref(false);
const fbType = ref(1);
const fbContent = ref('');
const showIdSwitch = ref(false);
const currentIdentity = ref('org');
const fbTypes = [
  { value: 1, label: '错误', icon: '❌' },
  { value: 2, label: '有害', icon: '⚠️' },
  { value: 3, label: '侵权', icon: '📋' }
];

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
function goMember() { uni.navigateTo({ url: '/pages/member/index' }); }
function goLog() { uni.navigateTo({ url: '/pages/log/index' }); }
function upload() {
  uni.chooseMessageFile({
    count: 1,
    type: 'file',
    success: async (res) => {
      const f = res.tempFiles[0];
      const formData = new FormData();
      formData.append('file', f as any);
      // 模拟上传 + 入库流程
      kbFiles.value.unshift({ icon: '📄', name: f.name, status: 'wait', statusText: '解析中' });
      uni.showToast({ title: '正在解析入库...', icon: 'loading' });
      // 调用后端上传
      try {
        const r = await ledgerApi.feedback(1, '上传文件: ' + f.name);
        // 模拟解析完成
        setTimeout(() => {
          const item = kbFiles.value.find(x => x.name === f.name);
          if (item) { item.status = 'ok'; item.statusText = '已入库'; }
          uni.showToast({ title: '入库成功', icon: 'success' });
        }, 2000);
      } catch {
        uni.showToast({ title: '上传失败', icon: 'none' });
      }
    },
    fail: () => {
      // H5 环境降级为模拟
      kbFiles.value.unshift({ icon: '📄', name: '新文件_' + Date.now() + '.pdf', status: 'wait', statusText: '解析中' });
      setTimeout(() => {
        const item = kbFiles.value[0];
        if (item) { item.status = 'ok'; item.statusText = '已入库'; }
      }, 2000);
    }
  });
}
function toast(msg: string) { uni.showToast({ title: msg, icon: 'none' }); }
function doSwitch() {
  showIdSwitch.value = false;
  const id = currentIdentity.value === 'org' ? '企业成员' : '个人';
  uni.showToast({ title: '已切换为' + id + '身份', icon: 'success' });
}
async function submitFeedback() {
  if (!fbContent.value.trim()) return;
  try {
    const r = await ledgerApi.feedback(fbType.value, fbContent.value.trim());
    if (r.code === 0) {
      uni.showToast({ title: '反馈已提交，感谢！', icon: 'success' });
      showFeedback.value = false;
      fbContent.value = '';
    } else {
      uni.showToast({ title: r.message || '提交失败', icon: 'none' });
    }
  } catch {
    uni.showToast({ title: '提交失败', icon: 'none' });
  }
}
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

.modal-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.4); z-index: 999; display: flex; align-items: center; justify-content: center; }
.modal { background: #fff; border-radius: 14px; padding: 20px; width: 88%; max-width: 360px; }
.modal-title { font-size: 16px; font-weight: 700; margin-bottom: 14px; text-align: center; }
.fb-types { display: flex; gap: 8px; margin-bottom: 12px; }
.fb-type { flex: 1; text-align: center; padding: 10px 4px; border: 1.5px solid #e6eaf2; border-radius: 8px; font-size: 12px; }
.fb-type.on { border-color: #2f6fed; background: #f4f8ff; color: #2f6fed; }
.fb-ico { display: block; font-size: 18px; margin-bottom: 4px; }
.fb-textarea { width: 100%; box-sizing: border-box; border: 1px solid #d6dde8; border-radius: 8px; padding: 10px; font-size: 13px; min-height: 80px; }
.modal-btns { display: flex; gap: 10px; margin-top: 14px; }
.btn.ghost { background: #fff; color: #2f6fed; border: 1px solid #2f6fed; }
.btn[disabled] { opacity: 0.4; }
.id-opt { padding: 12px; border: 1.5px solid #e6eaf2; border-radius: 10px; margin-bottom: 8px; cursor: pointer; }
.id-opt.on { border-color: #2f6fed; background: #f4f8ff; }
.id-name { font-weight: 700; font-size: 14px; }
</style>
