<template>
  <el-container style="height: 100%">
    <el-aside width="216px" class="layout-aside">
      <div class="layout-logo">
        <div class="logo-icon">🛗</div>
        <span>电梯应急救援平台</span>
      </div>
      <el-menu class="layout-menu" :default-active="activeMenu" router>
        <el-menu-item index="/">
          <el-icon><Monitor /></el-icon>
          <span>应急指挥工作台</span>
        </el-menu-item>
        <el-menu-item index="/events">
          <el-icon><AlarmClock /></el-icon>
          <span>困人事件管理</span>
        </el-menu-item>
        <el-menu-item index="/elevators">
          <el-icon><OfficeBuilding /></el-icon>
          <span>电梯与维保档案</span>
        </el-menu-item>
        <el-menu-item index="/complaints">
          <el-icon><ChatDotSquare /></el-icon>
          <span>业主投诉</span>
        </el-menu-item>
        <el-menu-item index="/duty">
          <el-icon><Calendar /></el-icon>
          <span>物业值班表</span>
        </el-menu-item>
        <el-menu-item index="/notices">
          <el-icon><Bell /></el-icon>
          <span>楼栋公告</span>
        </el-menu-item>
        <el-menu-item v-if="auth.user?.role === 'ADMIN'" index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header" height="60px">
        <div class="page-title">{{ route.meta.title || '应急指挥工作台' }}</div>
        <div style="display: flex; align-items: center; gap: 14px">
          <el-tag v-if="activeCount > 0" type="danger" effect="dark" round style="cursor: pointer" @click="$router.push('/events?status=ACTIVE')">
            {{ activeCount }} 起事件进行中
          </el-tag>
          <el-dropdown @command="onCommand">
            <span style="cursor: pointer; display: flex; align-items: center; gap: 6px; color: #1f2d4d">
              <el-icon><Avatar /></el-icon>
              {{ auth.user?.realName }}（{{ auth.roleLabel }}）
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'
import api from '../api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const activeCount = ref(0)

const activeMenu = computed(() => {
  if (route.path.startsWith('/events')) return '/events'
  return route.path
})

async function loadActiveCount() {
  try {
    const res = await api.get('/events', { params: { status: 'ACTIVE' } })
    activeCount.value = res.data.length
  } catch {
    // 忽略，角标不阻塞页面
  }
}

function onCommand(command) {
  if (command === 'logout') {
    auth.logout()
    router.push('/login')
  }
}

onMounted(loadActiveCount)
</script>
