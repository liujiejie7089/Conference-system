<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();

onLaunch(() => {
  // 启动时尝试恢复登录态
  userStore.restore();
  console.log('[AIOA] App launched, login:', userStore.isLoggedIn);
});
</script>

<style>
/* 全局样式 */
page {
  background: #f7f8fa;
  color: #1a1a1a;
  font-size: 14px;
}
</style>
