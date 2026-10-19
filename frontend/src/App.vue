<template>
  <router-view />
</template>

<script setup>
// 根组件只做路由出口，布局在 layout 里
</script>

<style>
* { box-sizing: border-box; }

html, body, #app {
  height: 100%;
  margin: 0;
  padding: 0;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei',
               'PingFang SC', Roboto, Helvetica, Arial, sans-serif;
  background: #f0f2f5;
  color: #303133;
}

/* 列表页的统一卡片样式，避免每个页面重复写 */
.page-card {
  background: #fff;
  border-radius: 6px;
  padding: 16px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
  align-items: center;
}

.toolbar .spacer { flex: 1; }

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
