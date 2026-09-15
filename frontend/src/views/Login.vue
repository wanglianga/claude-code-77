<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-title">
        <div style="font-size: 40px">🛗</div>
        <h2>小区电梯困人救援与维保责任追踪平台</h2>
        <p>Elevator Entrapment Rescue &amp; Maintenance Accountability Platform</p>
      </div>
      <el-form :model="form" size="large" @keyup.enter="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading" @click="submit">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-demo">
        <b>演示账号</b>（密码均为 123456，管理员为 admin123）<br />
        管理员 admin ｜ 值班员 duty01 ｜ 维保 maint01 ｜ 保安 sec01<br />
        楼栋管家 butler01 ｜ 消防 fire01 ｜ 业主 owner01
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: 'duty01', password: '123456' })

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push('/')
  } catch {
    // 错误信息由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>
