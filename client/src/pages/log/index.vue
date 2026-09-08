<template>
  <view class="log">
    <view class="page-title">我的操作记录</view>

    <view class="card">
      <view v-for="(r, i) in records" :key="i" class="row">
        <view class="log-info">
          <view class="log-time">{{ r.time }}</view>
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
import { ref } from 'vue';

const records = ref([
  { time: '09-05 14:20', action: '发起会话（政策咨询专家）', status: 'ok', statusText: '成功' },
  { time: '09-05 10:15', action: '上传资料（会议纪要0905.docx）', status: 'ok', statusText: '成功' },
  { time: '09-05 09:58', action: '购买词元包（10万词元）', status: 'ok', statusText: '已支付' },
  { time: '09-04 17:02', action: '删除会话（旧手机迁移）', status: 'ok', statusText: '逻辑删除' }
]);
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
</style>
