<template>
  <view class="member">
    <!-- 会员头部 -->
    <view class="card head" :class="levelClass">
      <view class="level-badge">{{ currentLevelName }}</view>
      <view class="quota-line">
        <text class="quota-num">{{ formatNum(quota.remainingQuota) }}</text>
        <text class="muted">/ {{ formatNum(quota.totalQuota) }} 词元</text>
      </view>
      <view class="muted">已用 {{ formatNum(quota.usedQuota) }} · 模型：{{ quota.preferredModel }}</view>
    </view>

    <!-- 三分支引导 -->
    <view class="card">
      <view class="sec-title">额度解决方案</view>
      <view class="branch-grid">
        <view class="branch" @click="scrollTo('plans')">
          <view class="b-ico">👑</view>
          <view class="b-name">升级会员</view>
          <view class="b-desc">更高额度 + 特权</view>
        </view>
        <view class="branch" @click="scrollTo('packs')">
          <view class="b-ico">💎</view>
          <view class="b-name">充值词元包</view>
          <view class="b-desc">按需购买即买即用</view>
        </view>
        <view class="branch" @click="switchModel">
          <view class="b-ico">🆓</view>
          <view class="b-name">切换免费模型</view>
          <view class="b-desc">每日 5000 免费额度</view>
        </view>
      </view>
    </view>

    <!-- 词元包 -->
    <view class="card" id="packs">
      <view class="sec-title">词元包</view>
      <view v-for="p in plans?.tokenPacks" :key="p.code" class="plan-row">
        <view class="p-info">
          <view class="p-name">{{ p.name }}</view>
          <view class="muted">{{ formatNum(p.tokens) }} 词元</view>
        </view>
        <view class="p-right">
          <view class="p-price">¥{{ p.price }}</view>
          <button class="btn small" @click="buyPack(p.code)">购买</button>
        </view>
      </view>
    </view>

    <!-- 会员套餐 -->
    <view class="card" id="plans">
      <view class="sec-title">会员套餐</view>
      <view v-for="p in plans?.memberPlans" :key="p.code" class="plan-row" :class="{ active: p.level === quota.memberLevel }">
        <view class="p-info">
          <view class="p-name">
            {{ p.name }}
            <text v-if="p.level === quota.memberLevel" class="current-tag">当前</text>
          </view>
          <view class="muted">{{ formatNum(p.tokens) }} 词元/月</view>
          <view class="benefits">
            <text v-for="(b, i) in p.benefits" :key="i" class="benefit">{{ b }}</text>
          </view>
        </view>
        <view class="p-right">
          <view class="p-price">¥{{ p.price }}</view>
          <button class="btn small" :disabled="p.level <= (quota.memberLevel || 1)" @click="upgrade(p.code)">
            {{ p.level <= (quota.memberLevel || 1) ? '已拥有' : '升级' }}
          </button>
        </view>
      </view>
    </view>

    <!-- 模型切换 -->
    <view class="card">
      <view class="sec-title">模型偏好</view>
      <view class="model-row">
        <view class="model-opt" :class="{ on: quota.preferredModel === 'deepseek-chat' }" @click="setModel('deepseek-chat')">
          <view class="m-name">DeepSeek Chat</view>
          <view class="muted">标准模型 · 扣额度</view>
        </view>
        <view class="model-opt" :class="{ on: quota.preferredModel === 'deepseek-free' }" @click="setModel('deepseek-free')">
          <view class="m-name">DeepSeek Free</view>
          <view class="muted">免费模型 · 每日 5000 词元</view>
        </view>
      </view>
    </view>

    <!-- 订单记录 -->
    <view class="card">
      <view class="sec-title">充值/升级记录</view>
      <view v-if="!orders.length" class="empty">暂无记录</view>
      <view v-for="o in orders" :key="o.id" class="order-row">
        <view class="o-info">
          <view class="o-name">{{ o.productName }}</view>
          <view class="muted">{{ formatTime(o.createdAt) }}</view>
        </view>
        <view class="o-right">
          <view class="o-tokens">+{{ formatNum(o.tokenAmount) }}</view>
          <view class="badge" :class="o.status === 2 ? 'ok' : 'wait'">{{ o.statusName }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { onShow, onLoad } from '@dcloudio/uni-app';
import { ledgerApi, membershipApi, type QuotaVo, type MemberPlanVo, type RechargeOrderVo } from '@/api';

const quota = ref<Partial<QuotaVo>>({ memberLevel: 1, preferredModel: 'deepseek-chat' });
const plans = ref<MemberPlanVo | null>(null);
const orders = ref<RechargeOrderVo[]>([]);

const currentLevelName = ref('铜牌会员');
const levelClass = ref('level-bronze');

function applyLevel(level?: number) {
  const lv = level || 1;
  currentLevelName.value = lv >= 3 ? '金牌会员' : lv === 2 ? '银牌会员' : '铜牌会员';
  levelClass.value = lv >= 3 ? 'level-gold' : lv === 2 ? 'level-silver' : 'level-bronze';
}

async function load() {
  try {
    const [q, p, o] = await Promise.all([
      ledgerApi.quota(),
      membershipApi.plans(),
      membershipApi.orders(1, 20)
    ]);
    if (q.code === 0 && q.data) {
      quota.value = q.data;
      applyLevel(q.data.memberLevel);
    }
    if (p.code === 0 && p.data) plans.value = p.data;
    if (o.code === 0 && o.data) orders.value = o.data;
  } catch { /* ignore */ }
}
onLoad((opts: any) => {
  if (opts?.model === 'free') {
    setModel('deepseek-free');
  }
});
onMounted(load);
onShow(load);

function buyPack(code: string) {
  uni.showModal({
    title: '确认购买',
    content: '将模拟支付完成购买，词元即时到账。',
    success: async (res) => {
      if (!res.confirm) return;
      const r = await membershipApi.recharge(code);
      if (r.code === 0) {
        uni.showToast({ title: '购买成功，词元已到账', icon: 'success' });
        load();
      } else {
        uni.showToast({ title: r.message || '购买失败', icon: 'none' });
      }
    }
  });
}

function upgrade(code: string) {
  uni.showModal({
    title: '确认升级',
    content: '升级后即时生效并赠送词元额度。',
    success: async (res) => {
      if (!res.confirm) return;
      const r = await membershipApi.upgrade(code);
      if (r.code === 0) {
        uni.showToast({ title: '升级成功', icon: 'success' });
        load();
      } else {
        uni.showToast({ title: r.message || '升级失败', icon: 'none' });
      }
    }
  });
}

function setModel(model: string) {
  membershipApi.switchModel(model).then(r => {
    if (r.code === 0) {
      uni.showToast({ title: model === 'deepseek-free' ? '已切换免费模型，发放5000额度' : '已切换标准模型', icon: 'none' });
      load();
    } else {
      uni.showToast({ title: r.message || '切换失败', icon: 'none' });
    }
  });
}

function switchModel() {
  setModel('deepseek-free');
}

function scrollTo(id: string) {
  uni.pageScrollTo({ selector: '#' + id, duration: 300 });
}

function formatNum(n?: number): string {
  if (n == null) return '0';
  return Number(n).toLocaleString('zh-CN');
}
function formatTime(t: string) {
  if (!t) return '';
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}
</script>

<style scoped>
.member { padding: 14px; background: #f4f6fa; min-height: 100vh; }
.card { background: #fff; border-radius: 14px; padding: 14px; margin-bottom: 12px; border: 1px solid #e6eaf2; }
.head { background: linear-gradient(135deg, #2f6fed, #5b8cff); color: #fff; border: none; }
.head.level-silver { background: linear-gradient(135deg, #6b7280, #9ca3af); }
.head.level-gold { background: linear-gradient(135deg, #d97706, #fbbf24); }
.head .muted { color: rgba(255,255,255,0.8); }
.level-badge { display: inline-block; font-size: 12px; font-weight: 700; background: rgba(255,255,255,0.25); border-radius: 12px; padding: 3px 12px; margin-bottom: 10px; }
.quota-line { display: flex; align-items: baseline; gap: 8px; }
.quota-num { font-size: 30px; font-weight: 800; }
.muted { color: #6b7486; font-size: 12px; }
.sec-title { font-size: 14px; font-weight: 700; margin-bottom: 12px; }

.branch-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.branch { text-align: center; padding: 12px 4px; background: #f6f8fc; border-radius: 10px; }
.branch .b-ico { font-size: 24px; }
.branch .b-name { font-size: 12px; font-weight: 700; margin-top: 4px; }
.branch .b-desc { font-size: 10px; color: #6b7486; margin-top: 2px; }

.plan-row { display: flex; justify-content: space-between; align-items: center; padding: 12px 0; border-bottom: 1px solid #eef0f5; }
.plan-row:last-child { border-bottom: none; }
.plan-row.active { background: #f4f8ff; border-radius: 8px; padding: 12px 8px; }
.p-info { flex: 1; }
.p-name { font-weight: 700; font-size: 14px; }
.current-tag { font-size: 10px; color: #12a56b; background: #e7f7f0; border-radius: 4px; padding: 1px 6px; margin-left: 6px; }
.benefits { margin-top: 4px; }
.benefit { display: inline-block; font-size: 10px; color: #2f6fed; background: #eaf1ff; border-radius: 4px; padding: 1px 6px; margin-right: 4px; margin-top: 2px; }
.p-right { text-align: right; }
.p-price { font-weight: 800; color: #d97706; font-size: 15px; }

.btn { background: #2f6fed; color: #fff; border: none; border-radius: 20px; padding: 8px 18px; font-size: 13px; margin-top: 6px; }
.btn.small { padding: 5px 14px; font-size: 12px; }
.btn[disabled] { opacity: 0.4; }

.model-row { display: flex; gap: 10px; }
.model-opt { flex: 1; padding: 12px; border: 1.5px solid #e6eaf2; border-radius: 10px; text-align: center; }
.model-opt.on { border-color: #2f6fed; background: #f4f8ff; }
.model-opt .m-name { font-weight: 700; font-size: 13px; }

.order-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid #eef0f5; }
.order-row:last-child { border-bottom: none; }
.o-name { font-size: 13px; font-weight: 600; }
.o-right { text-align: right; }
.o-tokens { font-weight: 700; color: #12a56b; font-size: 13px; }
.badge { font-size: 10px; border-radius: 4px; padding: 2px 6px; display: inline-block; margin-top: 2px; }
.badge.ok { color: #12a56b; background: #e7f7f0; }
.badge.wait { color: #f59e0b; background: #fef4e6; }
.empty { text-align: center; color: #6b7486; font-size: 13px; padding: 16px; }
</style>
