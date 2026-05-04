<template>
  <div class="intraday-chart" ref="chartRef" style="width: 100%; height: 420px"></div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] },
  prevClose: { type: Number, default: null },
})

const chartRef = ref(null)
let chart = null

// 昨收价作为基准
const basePrice = computed(() => {
  if (props.prevClose != null) return props.prevClose
  if (props.data.length > 0) return props.data[0].open || props.data[0].price
  return 0
})

// 均价线：累计成交额 / 累计成交量
function calcAvgPrices(data) {
  const avgs = []
  let cumAmount = 0
  let cumVolume = 0
  for (const d of data) {
    cumAmount += d.amount || 0
    cumVolume += d.volume || 0
    avgs.push(cumVolume > 0 ? cumAmount / cumVolume : d.price)
  }
  return avgs
}

// 格式化时间轴标签（9:30/10:30/11:30/13:00/14:00/15:00）
function formatTimeLabel(t) {
  const hm = t.slice(11, 16)
  const h = hm.slice(0, 2)
  const m = hm.slice(3, 5)
  if (m === '00' || m === '30') return hm
  return ''
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  updateChart()
}

function updateChart() {
  if (!chart) return

  const data = props.data
  const base = basePrice.value
  if (!data || data.length === 0 || base === 0) {
    chart.setOption({
      title: { text: '暂无分时数据（非交易时段）', left: 'center', top: 'center', textStyle: { fontSize: 14, color: '#909399' } },
    })
    return
  }

  const times = data.map((d) => d.time.slice(11, 16))
  const prices = data.map((d) => d.price)
  const vols = data.map((d) => d.volume || 0)
  const avgPrices = calcAvgPrices(data)

  const lastPrice = prices[prices.length - 1]
  const isUp = lastPrice >= base

  // 涨跌幅百分比数据（用于右侧Y轴）
  const changePcts = prices.map((p) => ((p - base) / base * 100))
  const maxPct = Math.max(...changePcts.map(Math.abs), 0.5)

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params) => {
        const p = params.find((x) => x.seriesName === '价格')
        const a = params.find((x) => x.seriesName === '均价')
        const v = params.find((x) => x.seriesName === '成交量')
        if (!p) return ''
        const pct = (p.data - base) / base * 100
        return `<div style="font-size:13px;font-weight:bold">${p.axisValue}</div>
          <div><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#ef5350;margin-right:4px"></span>价格: <b>${p.data?.toFixed(2)}</b>  <span style="color:${pct >= 0 ? '#ef5350' : '#26a69a'}">${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%</span></div>
          ${a ? `<div><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:#f9a825;margin-right:4px"></span>均价: <b>${a.data?.toFixed(2)}</b></div>` : ''}
          <div>成交量: ${fmtVol(v?.data || 0)}</div>`
      },
    },
    grid: [
      { left: '6%', right: '6%', top: '6%', height: '60%' },
      { left: '6%', right: '6%', top: '76%', height: '18%' },
    ],
    xAxis: [
      {
        type: 'category',
        data: times,
        boundaryGap: false,
        axisLine: { onZero: false },
        axisLabel: {
          fontSize: 11,
          interval: 0,
          formatter: (v, i) => {
            if (i === 0 || i === times.length - 1) return v
            const m = v.slice(3, 5)
            return m === '00' || m === '30' ? v : ''
          },
        },
        splitLine: {
          show: true,
          lineStyle: { color: '#f0f0f0', type: 'dashed' },
        },
      },
      {
        type: 'category',
        data: times,
        gridIndex: 1,
        boundaryGap: false,
        axisLabel: { show: false },
        splitLine: { show: false },
      },
    ],
    yAxis: [
      {
        type: 'value',
        scale: true,
        splitNumber: 4,
        axisLabel: {
          formatter: (v) => v.toFixed(0),
        },
        splitLine: {
          lineStyle: { color: '#f0f0f0', type: 'dashed' },
        },
      },
      {
        type: 'value',
        scale: true,
        splitNumber: 4,
        position: 'right',
        axisLabel: {
          formatter: (v) => ((v - base) / base * 100).toFixed(1) + '%',
        },
        splitLine: { show: false },
      },
      {
        type: 'value',
        gridIndex: 1,
        splitNumber: 2,
        axisLabel: { formatter: (v) => fmtVol(v) },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '价格',
        type: 'line',
        data: prices,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1.5, color: isUp ? '#ef5350' : '#26a69a' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: isUp ? 'rgba(239,83,80,0.2)' : 'rgba(38,166,154,0.2)' },
            { offset: 0.5, color: isUp ? 'rgba(239,83,80,0.05)' : 'rgba(38,166,154,0.05)' },
            { offset: 1, color: 'rgba(0,0,0,0)' },
          ]),
        },
        markLine: {
          silent: true,
          symbol: 'none',
          data: [
            {
              yAxis: base,
              label: {
                formatter: `昨收 ${base.toFixed(2)}`,
                fontSize: 11,
                color: '#999',
                position: 'insideStartTop',
              },
            },
          ],
          lineStyle: { color: '#999', type: 'dashed', width: 1 },
        },
      },
      {
        name: '均价',
        type: 'line',
        data: avgPrices,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1, color: '#f9a825', type: 'solid' },
        yAxisIndex: 0,
      },
      {
        name: '成交量',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 2,
        data: vols,
        barMaxWidth: 2,
        itemStyle: {
          color: (p) => {
            const idx = p.dataIndex
            return prices[idx] >= base ? '#ef5350' : '#26a69a'
          },
        },
      },
    ],
  }

  chart.setOption(option, true)
}

function fmtVol(v) {
  if (!v) return '0'
  if (v >= 1e8) return (v / 1e8).toFixed(2) + '亿'
  if (v >= 1e4) return (v / 1e4).toFixed(1) + '万'
  return v.toString()
}

function handleResize() {
  chart?.resize()
}

watch(() => [props.data, props.prevClose], () => {
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
