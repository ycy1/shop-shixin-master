<template>
  <div class="dashboard-container">
    <!-- 数据卡片 -->
    <el-row :gutter="20">
      <el-col :span="6" v-for="(item, index) in statistics" :key="item.title">
        <el-card 
          shadow="hover" 
          :body-style="{ padding: '20px' }"
          class="data-card"
          :style="{ animationDelay: `${index * 0.1}s` }"
        >
          <div class="card-content">
            <div class="icon-wrapper" :class="item.type">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <div class="data-wrapper">
              <count-to
                :start-val="0"
                :end-val="item.value"
                :duration="2000"
                class="card-value"
              />
              <div class="card-title">{{ item.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="24">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <span>系统概览</span>
          </template>
          <div ref="lineChartRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

  </div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import { 
  CaretTop, 
  CaretBottom,
  User,
  Monitor,
  Setting,
  View
} from '@element-plus/icons-vue'
import CountTo from '@/views/dashboard/components/CountTo.vue'
import { getDashboardDataApi } from '@/api/system'

const icons = {
  User: markRaw(User),
  Monitor: markRaw(Monitor),
  Setting: markRaw(Setting),
  View: markRaw(View),
  CaretTop: markRaw(CaretTop),
  CaretBottom: markRaw(CaretBottom)
}

const statistics = ref([
  { 
    title: '用户总数', 
    value: 0, 
    type: 'primary',
    icon: icons.User
  },
  { 
    title: '在线用户', 
    value: 0, 
    type: 'success',
    icon: icons.Monitor
  },
  { 
    title: '系统任务', 
    value: 0, 
    type: 'warning',
    icon: icons.Setting
  },
  { 
    title: '访问量', 
    value: 0, 
    type: 'info',
    icon: icons.View
  }
])

// 图表相关
const lineChartRef = ref<HTMLElement>()
const lineChart = shallowRef<echarts.ECharts | null>(null)

// 折线图配置
const getLineChartOption = (): EChartsOption => ({
  tooltip: {
    trigger: 'axis'
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    containLabel: true
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  },
  yAxis: {
    type: 'value'
  },
  series: [
    {
      name: '访问量',
      type: 'line',
      smooth: true,
      data: [0, 0, 0, 0, 0, 0, 0],
      itemStyle: {
        color: '#409EFF'
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
          { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
        ])
      }
    }
  ]
})

// 初始化图表
const initChart = () => {
  if (!lineChartRef.value) return
  lineChart.value = echarts.init(lineChartRef.value)
  lineChart.value.setOption(getLineChartOption())
}

// 获取数据
const loadData = async () => {
  try {
    const { data } = await getDashboardDataApi()
    statistics.value[0].value = data.userCount || 0
    statistics.value[3].value = data.visitCount || 0
  } catch (error) {
    console.error('获取仪表盘数据失败', error)
  }
}

// 窗口大小变化时重新调整图表
const handleResize = () => {
  lineChart.value?.resize()
}

onMounted(() => {
  loadData()
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  lineChart.value?.dispose()
})
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
}

.data-card {
  animation: slideUp 0.5s ease-out forwards;
  opacity: 0;
  transform: translateY(20px);
}

@keyframes slideUp {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.card-content {
  display: flex;
  align-items: center;
  gap: 20px;
}

.icon-wrapper {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.3s;
}

.icon-wrapper:hover {
  transform: scale(1.1);
}

.icon-wrapper .el-icon {
  font-size: 30px;
  color: #fff;
}

.icon-wrapper.primary {
  background: linear-gradient(135deg, #1890ff, #36a9ff);
}

.icon-wrapper.success {
  background: linear-gradient(135deg, #52c41a, #73d13d);
}

.icon-wrapper.warning {
  background: linear-gradient(135deg, #faad14, #ffc53d);
}

.icon-wrapper.info {
  background: linear-gradient(135deg, #13c2c2, #36cfc9);
}

.data-wrapper {
  flex: 1;
}

.card-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
  line-height: 1;
  margin-bottom: 8px;
}

.card-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 12px;
}

.chart-row {
  margin-top: 20px;
}

.chart {
  height: 350px;
  width: 100%;
}

.chart-card {
  height: auto;
  margin-bottom: 20px;
}

@media (prefers-color-scheme: dark) {
  .card-value {
    color: #e6e6e6;
  }
}
</style>
