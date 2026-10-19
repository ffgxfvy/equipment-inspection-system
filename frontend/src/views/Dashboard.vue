<template>
  <div v-loading="loading">
    <!-- 四个数字卡片 -->
    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.label" :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon" :style="{ background: card.color }">
              <el-icon :size="22" color="#fff"><component :is="card.icon" /></el-icon>
            </div>
            <div>
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <template #header>设备状态分布</template>
          <div ref="statusChartRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <template #header>设备类目分布</template>
          <div ref="categoryChartRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="chart-card" style="margin-top: 16px">
      <template #header>近 7 天巡检趋势</template>
      <div ref="trendChartRef" class="chart chart-wide"></div>
    </el-card>

    <!-- 未处理异常 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>待处理异常</span>
          <el-tag v-if="unhandled.length" type="danger" size="small">
            {{ unhandled.length }} 条
          </el-tag>
          <el-tag v-else type="success" size="small">全部已处理</el-tag>
        </div>
      </template>

      <el-table :data="unhandled" stripe size="small">
        <el-table-column prop="equipmentCode" label="设备编号" width="110" />
        <el-table-column prop="equipmentName" label="设备名称" min-width="150" />
        <el-table-column prop="abnormalDesc" label="异常描述" min-width="280" show-overflow-tooltip />
        <el-table-column prop="inspectorName" label="巡检员" width="90" />
        <el-table-column prop="inspectTime" label="发现时间" width="150" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.handleStatus === 'HANDLING' ? 'warning' : 'danger'" size="small">
              {{ row.handleStatus === 'HANDLING' ? '处理中' : '待处理' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDashboard } from '@/api/common'

const loading = ref(false)
const summary = ref({})
const unhandled = ref([])

const statusChartRef = ref(null)
const categoryChartRef = ref(null)
const trendChartRef = ref(null)
let charts = []

const cards = computed(() => [
  { label: '设备总数', value: summary.value.equipmentTotal ?? 0, icon: 'Files', color: '#409eff' },
  { label: '异常未处理', value: summary.value.abnormalUnhandled ?? 0, icon: 'Warning', color: '#f56c6c' },
  { label: '未结束任务', value: summary.value.taskOpen ?? 0, icon: 'Calendar', color: '#e6a23c' },
  { label: '逾期任务', value: summary.value.taskOverdue ?? 0, icon: 'AlarmClock', color: '#909399' }
])

/** echarts 实例需要在 DOM 尺寸确定后初始化，所以统一放这里 */
const initChart = (el, option) => {
  if (!el) return null
  const chart = echarts.init(el)
  chart.setOption(option)
  charts.push(chart)
  return chart
}

const render = (data) => {
  summary.value = data.summary || {}
  unhandled.value = data.unhandledAbnormal || []

  initChart(statusChartRef.value, {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 12 } },
    color: ['#67c23a', '#909399', '#f56c6c', '#e6a23c'],
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: true,
      label: { formatter: '{b}\n{c}', fontSize: 12 },
      data: (data.equipmentStatus || []).map((i) => ({ name: i.name, value: i.value }))
    }]
  })

  initChart(categoryChartRef.value, {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 12, right: 20, top: 20, bottom: 10, containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: {
      type: 'category',
      data: (data.equipmentCategory || []).map((i) => i.name).reverse()
    },
    series: [{
      type: 'bar',
      barMaxWidth: 22,
      itemStyle: { color: '#409eff', borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right' },
      data: (data.equipmentCategory || []).map((i) => i.value).reverse()
    }]
  })

  const trend = data.inspectTrend || []
  initChart(trendChartRef.value, {
    tooltip: { trigger: 'axis' },
    legend: { data: ['巡检次数', '发现异常'], right: 10, top: 0, textStyle: { fontSize: 12 } },
    grid: { left: 12, right: 20, top: 40, bottom: 10, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: trend.map((i) => i.date.slice(5)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '巡检次数',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.15 },
        itemStyle: { color: '#409eff' },
        data: trend.map((i) => i.total)
      },
      {
        name: '发现异常',
        type: 'line',
        smooth: true,
        itemStyle: { color: '#f56c6c' },
        data: trend.map((i) => i.abnormal)
      }
    ]
  })
}

const load = async () => {
  loading.value = true
  try {
    const data = await getDashboard()
    await nextTick()
    render(data)
  } finally {
    loading.value = false
  }
}

/** 窗口尺寸变化时图表要跟着重算，否则会挤压变形 */
const onResize = () => charts.forEach((c) => c.resize())

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
  charts = []
})
</script>

<style scoped>
.stat-card { margin-bottom: 16px; }

.stat-inner { display: flex; align-items: center; gap: 14px; }

.stat-icon {
  width: 46px; height: 46px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}

.stat-value { font-size: 24px; font-weight: 650; line-height: 1.2; }
.stat-label { font-size: 13px; color: #909399; margin-top: 2px; }

.chart-card { margin-bottom: 0; }
.chart { height: 260px; }
.chart-wide { height: 240px; }

.card-header { display: flex; align-items: center; gap: 10px; }
</style>
