# ============================================================
# 前端镜像：多阶段构建
#   阶段 1 用 node 镜像装依赖并打包
#   阶段 2 用 nginx 托管静态文件，并反向代理 /api 到后端
# ============================================================
FROM node:20-alpine AS builder
WORKDIR /build

# 先拷 package.json 利用层缓存：改业务代码不会重新 npm install
COPY package.json ./
RUN npm install --registry=https://registry.npmmirror.com

COPY . .
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
