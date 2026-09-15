<template>
  <el-card class="panel-card" shadow="never">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>用户管理</span>
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </div>
    </template>

    <el-table :data="users" v-loading="loading">
      <el-table-column prop="username" label="用户名" width="120">
        <template #default="{ row }"><span class="mono">{{ row.username }}</span></template>
      </el-table-column>
      <el-table-column prop="realName" label="姓名" width="100" />
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.role === 'ADMIN' ? 'danger' : 'primary'" effect="plain">{{ ROLE_LABEL[row.role] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="联系电话" width="130" />
      <el-table-column label="所属维保单位" width="180">
        <template #default="{ row }">{{ companyName(row.companyId) }}</template>
      </el-table-column>
      <el-table-column label="负责楼栋" width="140">
        <template #default="{ row }">{{ buildingName(row.buildingId) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" link :type="row.enabled ? 'danger' : 'success'" @click="toggle(row)">
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑用户' : '新增用户'" width="480px">
      <el-form label-width="100px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item :label="form.id ? '重置密码' : '密码'">
          <el-input v-model="form.password" type="password" show-password :placeholder="form.id ? '留空则不修改' : '默认 123456'" />
        </el-form-item>
        <el-form-item label="姓名" required><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-for="(v, k) in ROLE_LABEL" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.role === 'MAINTENANCE'" label="维保单位">
          <el-select v-model="form.companyId" style="width: 100%">
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.role === 'BUTLER' || form.role === 'OWNER'" label="负责楼栋">
          <el-select v-model="form.buildingId" style="width: 100%">
            <el-option v-for="b in buildings" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { ROLE_LABEL } from '../utils/dict'

const loading = ref(false)
const users = ref([])
const companies = ref([])
const buildings = ref([])
const dialog = ref(false)
const form = reactive({ id: null, username: '', password: '', realName: '', phone: '', role: 'DUTY', companyId: null, buildingId: null })

function companyName(id) {
  return companies.value.find((c) => c.id === id)?.name || '—'
}

function buildingName(id) {
  return buildings.value.find((b) => b.id === id)?.name || '—'
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/users')
    users.value = res.data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: null, username: '', password: '', realName: '', phone: '', role: 'DUTY', companyId: null, buildingId: null })
  dialog.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id, username: row.username, password: '', realName: row.realName,
    phone: row.phone, role: row.role, companyId: row.companyId, buildingId: row.buildingId
  })
  dialog.value = true
}

async function save() {
  if (!form.username || !form.realName || !form.role) {
    ElMessage.warning('请填写用户名、姓名和角色')
    return
  }
  if (form.id) {
    await api.put(`/users/${form.id}`, form)
  } else {
    await api.post('/users', form)
  }
  ElMessage.success('已保存')
  dialog.value = false
  load()
}

async function toggle(row) {
  await api.put(`/users/${row.id}/toggle`)
  ElMessage.success('状态已更新')
  load()
}

onMounted(async () => {
  load()
  const [c, b] = await Promise.all([api.get('/companies'), api.get('/buildings')])
  companies.value = c.data
  buildings.value = b.data
})
</script>
