# asset-frontend 运行镜像：dist 由 CI（GitLab 本机 runner）构建后 rsync 到沙箱
# /srv/app/asset-frontend/dist/ 再由本 Dockerfile 单阶段打包（秒级，无需容器内 npm 构建）
FROM nginx:1.26-alpine
COPY nginx.main.conf /etc/nginx/nginx.conf
COPY dist/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
