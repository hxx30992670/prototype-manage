FROM node:24-alpine AS builder

RUN corepack enable && corepack prepare pnpm@10.26.0 --activate
WORKDIR /app

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY apps/web/package.json ./apps/web/
# overlayfs on older kernels cannot hardlink/clone pnpm packages
RUN printf 'package-import-method=copy\nstore-dir=/pnpm/store\n' > .npmrc \
  && pnpm install --frozen-lockfile

COPY apps/web ./apps/web
RUN pnpm --filter @prototype/web build

FROM alpine:3.21
WORKDIR /app
COPY --from=builder /app/apps/web/dist /dist
VOLUME /usr/share/nginx/html/web

CMD ["sh", "-c", "mkdir -p /usr/share/nginx/html/web && cp -rf /dist/* /usr/share/nginx/html/web/ && tail -f /dev/null"]
