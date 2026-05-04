<template>
  <div class="kline-chart" ref="chartRef" style="width: 100%; height: 500px"></div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

const chartRef = ref(null)
let chart = null

// 颜色配置：涨-red 跌-green（A股习惯）
const upColor = '#ef5350'
const downColor = '#26a69a'

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  updateChart()
}

function updateChart() {
  if (!chart || props.data.length === 0) return

  const data = props.data
  const dates = data.map((d) => d.date)
  const volumes = data.map((d) => d.volume || 0)

  // K线数据: [date, open, close, low, high]
  const klineData = data.map((d) => [d.open, d.close, d.low, d.high])

  // 均线数据
  const ma5 = data.map((d) => d.ma5)
  const ma10 = data.map((d) => d.ma10)
  const ma20 = data.map((d) => d.ma20)

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
    },
    grid: [
      { left: '8%', right: '4%', top: '10%', height: '55%' },
      { left: '8%', right: '4%', top: '75%', height: '15%' },
    ],
    xAxis: [
      {
        type: 'category',
        data: dates,
        axisLine: { onZero: false },
        axisTick: { show: false },
        gridIndex: 0,
      },
      {
        type: 'category',
        data: dates,
        axisLine: { onZero: false },
        axisTick: { show: false },
        gridIndex: 1,
        axisLabel: { show: false },
      },
    ],
    yAxis: [
      {
        scale: true,
        gridIndex: 0,
        splitArea: { show: true },
      },
      {
        scale: true,
        gridIndex: 1,
        splitNumber: 2,
        axisLabel: { show: true },
        axisLine: { show: false },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: 'K线',
        type: 'candlestick',
        xAxisIndex: 0,
        yAxisIndex: 0,
        data: klineData,
        itemStyle: {
          color: upColor,
          color0: downColor,
          borderColor: upColor,
          borderColor0: downColor,
        },
      },
      {
        name: 'MA5',
        type: 'line',
        xAxisIndex: 0,
        yAxisIndex: 0,
        data: ma5,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1, color: '#ff9800' },
      },
      {
        name: 'MA10',
        type: 'line',
        xAxisIndex: 0,
        yAxisIndex: 0,
        data: ma10,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1, color: '#2196f3' },
      },
      {
        name: 'MA20',
        type: 'line',
        xAxisIndex: 0,
        yAxisIndex: 0,
        data: ma20,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1, color: '#9c27b0' },
      },
      {
        name: '成交量',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumes.map((v, i) => ({
          value: v,
          itemStyle: {
            color: data[i].close >= data[i].open ? upColor : downColor,
          },
        })),
      },
    ],
  }

  chart.setOption(option, true)
}

function handleResize() {
  chart?.resize()
}

watch(() => props.data, () => {
  nextTick(updateChart)
}, { deep: true })

onMounted(() => {
  nextTick(() => {
    initChart()
    window.addEventListener('resize', handleResize)
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})
</script>
