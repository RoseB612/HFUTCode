FROM mirror.ccs.tencentyun.com/library/node:22-alpine AS deps

WORKDIR /workspace

COPY frontend/package.json frontend/package-lock.json ./
COPY frontend/apps ./apps
COPY frontend/packages ./packages
COPY frontend/tsconfig.base.json ./tsconfig.base.json

RUN npm ci

FROM deps AS builder

ARG APP_NAME
ARG APP_URL=http://app:3000
ARG ADMIN_URL=http://admin:3000
ARG NEXT_PUBLIC_BACKEND_BASE_URL=http://127.0.0.1:8080
ARG SYNCODE_BACKEND_BASE_URL=http://friend:9202
ARG NEXT_PUBLIC_BACKEND_SERVICE_PREFIX=/friend
ARG SYNCODE_BACKEND_SERVICE_PREFIX=/friend

ENV APP_URL=${APP_URL}
ENV ADMIN_URL=${ADMIN_URL}
ENV NEXT_PUBLIC_BACKEND_BASE_URL=${NEXT_PUBLIC_BACKEND_BASE_URL}
ENV SYNCODE_BACKEND_BASE_URL=${SYNCODE_BACKEND_BASE_URL}
ENV NEXT_PUBLIC_BACKEND_SERVICE_PREFIX=${NEXT_PUBLIC_BACKEND_SERVICE_PREFIX}
ENV SYNCODE_BACKEND_SERVICE_PREFIX=${SYNCODE_BACKEND_SERVICE_PREFIX}

WORKDIR /workspace

RUN npm run build -w @aioj/${APP_NAME}

FROM mirror.ccs.tencentyun.com/library/node:22-alpine AS runner

ARG APP_NAME
ENV APP_NAME=${APP_NAME}

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
ENV APP_URL=http://app:3000
ENV ADMIN_URL=http://admin:3000

WORKDIR /workspace

COPY --from=builder /workspace /workspace

EXPOSE 3000

CMD ["sh", "-c", "npm run start -w @aioj/${APP_NAME} -- --hostname 0.0.0.0 --port 3000"]
