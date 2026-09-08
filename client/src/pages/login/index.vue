<template>
  <view class="login">
    <view class="logo">AIOA</view>
    <view class="subtitle">Agent-era Office Automation</view>

    <view class="form">
      <input v-model="form.username" class="input" placeholder="用户名" />
      <input v-model="form.password" class="input" type="password" placeholder="密码" />
      <button class="btn" :disabled="loading" @click="onLogin">{{ loading ? '登录中…' : '登录' }}</button>
      <view class="tip">默认账号：admin / admin123</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const loading = ref(false);
const form = reactive({ username: 'admin', password: 'admin123' });

async function onLogin() {
  if (!form.username || !form.password) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' });
    return;
  }
  loading.value = true;
  try {
    await userStore.login(form.username, form.password);
    uni.showToast({ title: '登录成功', icon: 'success' });
    setTimeout(() => uni.switchTab({ url: '/pages/home/index' }), 300);
  } catch (e: any) {
    uni.showToast({ title: e.message || '登录失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: linear-gradient(135deg, #1677ff, #4096ff);
  color: #fff;
}
.logo {
  font-size: 48px;
  font-weight: bold;
}
.subtitle {
  margin-top: 8px;
  opacity: 0.85;
}
.form {
  margin-top: 48px;
  width: 100%;
  max-width: 360px;
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  color: #1a1a1a;
}
.input {
  width: 100%;
  height: 44px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  padding: 0 12px;
  margin-bottom: 16px;
  box-sizing: border-box;
  font-size: 14px;
}
.btn {
  width: 100%;
  height: 44px;
  background: #1677ff;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 16px;
}
.tip {
  margin-top: 16px;
  font-size: 12px;
  color: #999;
  text-align: center;
}
</style>
