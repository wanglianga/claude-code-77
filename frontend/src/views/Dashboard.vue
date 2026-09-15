<template>
  <div v-loading="loading">
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col :span="6">
        <div class="stat-card c2">
          <div class="stat-label">进行中事件</div>
          <div class="stat-value">{{ stats.activeEvents ?? '—' }}</div>
          <div class="stat-extra">今日新增 {{ stats.todayEvents ?? 0 }} 起</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card c1">
          <div class="stat-label">累计困人事件</div>
          <div class="stat-value">{{ stats.totalEvents ?? '—' }}</div>
          <div class="stat-extra">维保迟到 {{ stats.lateCount ?? 0 }} 起 ｜ 索赔 {{ stats.compensationCount ?? 0 }} 起</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card c3">
          <div class="stat-label">平均救援时长</div>
          <div class="stat-value">{{ stats.avgRescueMinutes ?? '—' }}<span style="font-size: 14px"> 分钟</span></div>
          <div class="stat-extra">从报警到乘客释放</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card c4">
          <div class="stat-label">停梯电梯</div>
          <div class="stat-value">{{ stats.stoppedElevators ?? '—' }}<span style="font-size: 14px"> / {{ stats.totalElevators ?? 0 }} 台</span></div>
          <div class="stat-extra">反复故障 {{ stats.repeatCount ?? 0 }} 起事件</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="9">
        <el-card class="panel-card" shadow="never">
          <template #header>近 7 天困人事件趋势</template>
          <div ref="trendChart" style="height: 240px"></div>
        </el-card>
      </el-col>
      <el-col :span="7">
        <el-card class="panel-card" shadow="never">
          <template #header>事件状态分布</template>
          <div ref="statusChart" style="height: 240px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="panel-card" shadow="never">
          <template #header>维保单位考核对比</template>
          <div ref="companyChart" style="height: 240px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <!-- 最新事件 -->
      <el-col :span="14">
        <el-card class="panel-card" shadow="never">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span>最新事件</span>
              <el-button type="primary" size="small" @click="$router.push('/events/new')">
                <el-icon style="margin-right: 4px"><Plus /></el-icon>接警登记
              </el-button>
            </div>
          </template>
          <el-table :data="stats.recentEvents || []" size="small" @row-click="(row) => $router.push(`/events/${row.id}`)" style="cursor: pointer">
            <el-table-column prop="eventNo" label="事件编号" width="140">
              <template #default="{ row }"><span class="mono">{{ row.eventNo }}</span></template>
            </el-table-column>
            <el-table-column label="电梯 / 楼栋" width="150">
              <template #default="{ row }">{{ row.elevator?.code }} ｜ {{ row.building?.name?.slice(0, 2) }}</template>
            </el-table-column>
            <el-table-column label="报警来源" width="100">
              <template #default="{ row }">{{ ALARM_SOURCE[row.alarmSource] }}</template>
            </el-table-column>
            <el-table-column label="被困" width="70">
              <template #default="{ row }">{{ row.passengerCount }} 人</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="EVENT_STATUS[row.status]?.type" size="small">{{ EVENT_STATUS[row.status]?.label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="报警时间">
              <template #default="{ row }">{{ fmtTime(row.alarmTime) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 维保单位考核 + 反复故障电梯 -->
      <el-col :span="10">
        <el-card class="panel-card" shadow="never">
          <template #header>维保单位考核</template>
          <el-table :data="stats.companyStats || []" size="small">
            <el-table-column prop="name" label="维保单位" min-width="150" show-overflow-tooltip />
            <el-table-column prop="eventCount" label="事件" width="55" align="center" />
            <el-table-column prop="lateCount" label="迟到" width="55" align="center" />
            <el-table-column label="均时长" width="70" align="center">
              <template #default="{ row }">{{ row.avgRescueMinutes }}′</template>
            </el-table-column>
            <el-table-column prop="penaltyCount" label="处罚" width="55" align="center" />
            <el-table-column label="信用分" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.creditScore >= 90 ? 'success' : row.creditScore >= 80 ? 'warning' : 'danger'" size="small">
                  {{ row.creditScore }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="panel-card" shadow="never" style="margin-top: 16px">
          <template #header>⚠️ 反复故障电梯（90 天内 ≥2 次困人）</template>
          <el-table :data="stats.repeatFaultElevators || []" size="small" empty-text="暂无反复故障电梯">
            <el-table-column prop="code" label="电梯编号" width="110">
              <template #default="{ row }"><span class="mono">{{ row.code }}</span></template>
            </el-table-column>
            <el-table-column prop="building" label="楼栋" />
            <el-table-column prop="count" label="困人次数" width="90" align="center">
              <template #default="{ row }">
                <el-tag type="danger" size="small">{{ row.count }} 次</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="ELEVATOR_STATUS[row.status]?.type" size="small">{{ ELEVATOR_STATUS[row.status]?.label }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import * as echarts from 'echarts'
import api from '../api'
import { ALARM_SOURCE, EVENT_STATUS, ELEVATOR_STATUS } from '../utils/dict'
import { fmtTime } from '../utils/format'

const loading = ref(false)
const stats = ref({})
const trendChart = ref(null)
const statusChart = ref(null)
const companyChart = ref(null)
let charts = []

const STATUS_LABELS = {
  PENDING: '待调度', DISPATCHED: '已调度', ARRIVED: '已到场',
  RELEASED: '已救出', RESET: '已复位', CLOSED: '已关闭'
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/dashboard')
    stats.value = res.data
    renderCharts()
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  charts.forEach((c) => c.dispose())
  charts = []

  // 近 7 天趋势
  const trend = echarts.init(trendChart.value)
  trend.setOption({
    grid: { left: 40, right: 16, top: 20, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: (stats.value.eventsLast7Days || []).map((d) => d.date) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'line', smooth: true, data: (stats.value.eventsLast7Days || []).map((d) => d.count),
      areaStyle: { opacity: 0.15 }, itemStyle: { color: '#2563eb' }, symbolSize: 8
    }]
  })
  charts.push(trend)

  // 状态分布
  const status = echarts.init(statusChart.value)
  const statusData = Object.entries(stats.value.eventsByStatus || {}).map(([k, v]) => ({
    name: STATUS_LABELS[k] || k, value: v
  }))
  status.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'],
      label: { show: false }, data: statusData,
      color: ['#e64545', '#f59e0b', '#fbbf24', '#34d399', '#10b981', '#94a3b8']
    }]
  })
  charts.push(status)

  // 维保单位对比
  const company = echarts.init(companyChart.value)
  const companies = stats.value.companyStats || []
  company.setOption({
    grid: { left: 40, right: 16, top: 34, bottom: 28 },
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { fontSize: 11 } },
    xAxis: { type: 'category', data: companies.map((c) => c.name.slice(0, 4)), axisLabel: { fontSize: 11 } },
    yAxis: [{ type: 'value', minInterval: 1 }],
    series: [
      { name: '困人事件', type: 'bar', data: companies.map((c) => c.eventCount), itemStyle: { color: '#2563eb' }, barWidth: 22 },
      { name: '维保迟到', type: 'bar', data: companies.map((c) => c.lateCount), itemStyle: { color: '#e64545' }, barWidth: 22 }
    ]
  })
  charts.push(company)
}

function onResize() {
  charts.forEach((c) => c.resize())
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
})
</script>
