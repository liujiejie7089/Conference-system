import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { authApi, type LoginResponse } from '@/api';

const TOKEN_KEY = 'aioa_token';
const USER_KEY = 'aioa_user';

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('');
  const user = ref<LoginResponse['user'] | null>(null);

  const isLoggedIn = computed(() => !!token.value);

  function restore() {
    const t = uni.getStorageSync(TOKEN_KEY);
    const u = uni.getStorageSync(USER_KEY);
    if (t) token.value = t;
    if (u) user.value = u;
  }

  async function login(username: string, password: string) {
    const resp = await authApi.sso({ username, password });
    if (resp.code !== 0) {
      throw new Error(resp.message || '登录失败');
    }
    token.value = resp.data!.token;
    user.value = resp.data!.user;
    uni.setStorageSync(TOKEN_KEY, token.value);
    uni.setStorageSync(USER_KEY, user.value);
    return resp.data!;
  }

  function logout() {
    token.value = '';
    user.value = null;
    uni.removeStorageSync(TOKEN_KEY);
    uni.removeStorageSync(USER_KEY);
    uni.reLaunch({ url: '/pages/login/index' });
  }

  return { token, user, isLoggedIn, restore, login, logout };
});
