<template>
  <view class="session">
    <view class="page-title">会话历史</view>
    <view v-if="!list.length && !loading" class="empty">
      暂无会话，去专家页发起一个吧
    </view>
    <view v-for="s in list" :key="s.id" class="row" @click="go(s)">
      <view class="row-main">
        <view class="title">{{ s.title }}</view>
        <view class="meta">{{ formatTime(s.lastMessageAt || s.createdAt) }}</view>
      </view>
      <view class="row-actions" @click.stop>
        <text class="act rename" @click="startRename(s)">✏️</text>
        <text class="act del" @click="confirmDelete(s)">🗑️</text>
      </view>
    </view>

    <!-- 重命名弹窗 -->
    <view v-if="renaming" class="modal-mask" @click="renaming = false">
      <view class="modal" @click.stop>
        <view class="modal-title">重命名会话</view>
        <input v-model="renameTitle" class="modal-input" placeholder="输入新名称" :maxlength="30" />
        <view class="modal-btns">
          <button class="btn ghost" @click="renaming = false">取消</button>
          <button class="btn" @click="doRename">确认</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { sessionApi, type SessionVo } from '@/api';

const list = ref<SessionVo[]>([]);
const loading = ref(true);
const renaming = ref(false);
const renameTarget = ref<SessionVo | null>(null);
const renameTitle = ref('');

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
  uni.navigateTo({ url: `/pages/chat/index?sessionId=${s.id}&title=${encodeURIComponent(s.title)}` });
}

function startRename(s: SessionVo) {
  renameTarget.value = s;
  renameTitle.value = s.title;
  renaming.value = true;
}

async function doRename() {
  if (!renameTarget.value || !renameTitle.value.trim()) return;
  const r = await sessionApi.rename(renameTarget.value.id, renameTitle.value.trim());
  if (r.code === 0 && r.data) {
    const idx = list.value.findIndex(x => x.id === renameTarget.value!.id);
    if (idx >= 0) list.value[idx] = r.data;
    uni.showToast({ title: '已重命名', icon: 'success' });
  } else {
    uni.showToast({ title: r.message || '重命名失败', icon: 'none' });
  }
  renaming.value = false;
}

function confirmDelete(s: SessionVo) {
  uni.showModal({
    title: '删除会话',
    content: `确定删除「${s.title}」？此操作为逻辑删除，会留痕记录。`,
    confirmColor: '#e74c3c',
    success: async (res) => {
      if (!res.confirm) return;
      const r = await sessionApi.remove(s.id);
      if (r.code === 0) {
        list.value = list.value.filter(x => x.id !== s.id);
        uni.showToast({ title: '已删除', icon: 'success' });
      } else {
        uni.showToast({ title: r.message || '删除失败', icon: 'none' });
      }
    }
  });
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
  background: #fff; padding: 14px 16px; border-radius: 10px; margin-bottom: 8px;
  display: flex; align-items: center; justify-content: space-between;
  border: 1px solid #e6eaf2;
}
.row-main { flex: 1; }
.title { font-size: 14px; font-weight: 500; }
.meta { margin-top: 4px; font-size: 11px; color: #999; }
.row-actions { display: flex; gap: 12px; }
.act { font-size: 16px; padding: 4px; cursor: pointer; }
.act.rename { color: #2f6fed; }
.act.del { color: #e74c3c; }

.modal-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.4); z-index: 999; display: flex; align-items: center; justify-content: center; }
.modal { background: #fff; border-radius: 14px; padding: 20px; width: 80%; max-width: 320px; }
.modal-title { font-size: 16px; font-weight: 700; margin-bottom: 14px; text-align: center; }
.modal-input { border: 1px solid #d6dde8; border-radius: 8px; padding: 10px 12px; font-size: 14px; width: 100%; box-sizing: border-box; }
.modal-btns { display: flex; gap: 10px; margin-top: 16px; }
.btn { background: #2f6fed; color: #fff; border: none; border-radius: 20px; padding: 8px 18px; font-size: 14px; flex: 1; }
.btn.ghost { background: #fff; color: #2f6fed; border: 1px solid #2f6fed; }
</style>
