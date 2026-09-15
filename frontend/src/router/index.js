import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../store/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layout/Layout.vue'),
    children: [
      { path: '', name: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '应急指挥工作台' } },
      { path: 'events', name: 'events', component: () => import('../views/EventList.vue'), meta: { title: '困人事件管理' } },
      { path: 'events/new', name: 'event-new', component: () => import('../views/EventCreate.vue'), meta: { title: '接警登记' } },
      { path: 'events/:id', name: 'event-detail', component: () => import('../views/EventDetail.vue'), meta: { title: '事件处置' } },
      { path: 'elevators', name: 'elevators', component: () => import('../views/ElevatorManage.vue'), meta: { title: '电梯与维保档案' } },
      { path: 'rectification', name: 'rectification', component: () => import('../views/Rectification.vue'), meta: { title: '停梯整改与老人帮扶' } },
      { path: 'complaints', name: 'complaints', component: () => import('../views/Complaints.vue'), meta: { title: '业主投诉' } },
      { path: 'duty', name: 'duty', component: () => import('../views/DutySchedule.vue'), meta: { title: '物业值班表' } },
      { path: 'notices', name: 'notices', component: () => import('../views/Notices.vue'), meta: { title: '楼栋公告' } },
      { path: 'users', name: 'users', component: () => import('../views/UserManage.vue'), meta: { title: '用户管理', admin: true } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.token) {
    return { name: 'login' }
  }
  if (to.meta.admin && auth.user?.role !== 'ADMIN') {
    return { name: 'dashboard' }
  }
  if (to.name === 'login' && auth.token) {
    return { name: 'dashboard' }
  }
})

export default router
