import { ref } from "vue";
import { defineStore } from 'pinia';
import { loginApi,getUserInfoApi,logoutApi } from "@/api/system/auth";
import { resetRouter } from "@/router";
import { store } from "@/store";
import { setToken,removeToken } from "@/utils/auth";

interface UserState {
  roles: string[];
  perms: string[];
  intro: any;
  avatar: any;
  nickname: any;
  permissions: string[];
}

export const useUserStore = defineStore("user", () => {
  const user = ref({
    roles: [],
    intro: null,
    avatar: null,
    nickname: null,
    permissions: [],
    // 账号类型：0平台用户 1门店 2送货员 3消费者 4供应商 5ERP操作员（字典 account_type）
    accountType: undefined as number | undefined
  });

  /**
   * 登录
   *
   * @param {LoginData}
   * @returns
   */
  function login(loginData: any) {
    return new Promise<void>((resolve, reject) => {
      loginApi(loginData)
        .then((response) => {
          const { data } = response;
          setToken(data.token)
          // 登录响应里就带账号类型，先缓存一份；进首页时路由守卫会用 /api/auth/info 覆盖成最新值
          user.value.accountType = data.accountType
          resolve();
        })
        .catch((error) => {
          reject(error);
        });
    });
  }

  // 获取信息(用户昵称、头像、角色集合、权限集合)
  function getUserInfo() {
    return new Promise<any>((resolve, reject) => {
      getUserInfoApi()
        .then(({ data }) => {
          if (!data) {
            reject("Verification failed, please Login again.");
            return;
          }
          Object.assign(user.value, { ...data });
          resolve(data);
        })
        .catch((error) => {
          reject(error);
        });
    });
  }

  // user logout
  function logout() {
    return new Promise<void>((resolve, reject) => {
      logoutApi()
        .then(() => {
          removeToken()
          location.reload(); // 清空路由
          resolve();
        })
        .catch((error) => {
          reject(error);
        });
    });
  }

  // remove token
  function resetToken() {
    console.log("resetToken");
    return new Promise<void>((resolve) => {
      removeToken()
      resetRouter();
      resolve();
    });
  }

  return {
    user,
    login,
    getUserInfo,
    logout,
    resetToken,
  };
});

// 非setup
export function useUserStoreHook() {
  return useUserStore(store);
}
