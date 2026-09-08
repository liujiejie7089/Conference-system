<template>
  <view class="bill">
    <view class="page-title">用量账单（{{ monthStr }}）</view>

    <view class="card">
      <view class="summary">
        <view class="sum-item">
          <view class="sum-label">本月消耗</view>
          <view class="sum-value">{{ formatNum(totalUsed) }} 词元</view>
        </view>
        <view class="sum-item">
          <view class="sum-label">剩余额度</view>
          <view class="sum-value">{{ formatNum(quota.remainingQuota) }} 词元</view>
        </view>
      </view>
    </view>

    <view class="card">
      <view v-for="(b, i) in bills" :key="i" class="row">
        <view class="bill-info">
          <view class="bill-time">{{ formatTime(b.createdAt) }}</view>
          <view class="bill-source">{{ b.model }} · 会话 {{ b.sessionId }}</view>
        </view>
        <view class="bill-tokens">+{{ formatNum(b.totalTokens) }} 词元</view>
        <view v-if="i < bills.length - 1" class="divider"></view>
      </view>
      <view class="muted tip">输入 / 输出词元分列计价，流水与平台账本一致，明细可导出对账</view>
    </view>

    <button class="btn ghost small" @click="exportBill">导出明细</button>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { ledgerApi, type QuotaVo, type BillVo } from '@/api';

const quota = ref<Partial<QuotaVo>>({});
const bills = ref<BillVo[]>([]);

const totalUsed = computed(() => bills.value.reduce((s, b) => s + b.totalTokens, 0));
const monthStr = computed(() => {
  const d = new Date();
  return `${d.getMonth() + 1} 月`;
});

async function load() {
  try {
    const [qr, br] = await Promise.all([
      ledgerApi.quota(),
      ledgerApi.bills(1, 50),
    ]);
    if (qr.code === 0 && qr.data) quota.value = qr.data;
    if (br.code === 0 && br.data) bills.value = br.data;
  } catch { /* ignore */ }
}
onMounted(load);

function exportBill() {
  uni.showToast({ title: '账单明细已导出（演示）', icon: 'none' });
}
function formatNum(n?: number): string {
  if (n == null) return '0';
  return Number(n).toLocaleString('zh-CN');
}
function formatTime(t: string): string {
  if (!t) return '';
  const d = new Date(t);
  return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}
</script>

<style scoped>
.bill { padding: 14px; background: #f4f6fa; min-height: 100vh; }
.page-title { font-size: 18px; font-weight: 700; margin-bottom: 12px; }
.card { background: #fff; border-radius: 14px; padding: 14px; margin-bottom: 12px; border: 1px solid #e6eaf2; }
.summary { display: flex; gap: 16px; }
.sum-item { flex: 1; }
.sum-label { color: #6b7486; font-size: 12px; }
.sum-value { font-size: 20px; font-weight: 800; color: #1e4fc0; margin-top: 4px; }
.row { display: flex; justify-content: space-between; align-items: center; padding: 11px 0; font-size: 13px; }
.bill-info { flex: 1; }
.bill-time { font-size: 13px; color: #1a2233; }
.bill-source { font-size: 11px; color: #6b7486; margin-top: 2px; }
.bill-tokens { color: #1e4fc0; font-weight: 700; font-size: 13px; }
.divider { height: 1px; background: #e6eaf2; margin-top: 11px; }
.muted { color: #6b7486; font-size: 12px; }
.tip { margin-top: 8px; }
.btn { background: #2f6fed; color: #fff; border: none; border-radius: 20px; padding: 8px 18px; font-size: 13px; margin-top: 12px; }
.btn.ghost { background: #fff; color: #2f6fed; border: 1px solid #2f6fed; }
.btn.small { padding: 5px 12px; font-size: 12px; }
</style>
