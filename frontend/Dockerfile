# --- Build stage ---
FROM node:20-alpine AS build
WORKDIR /app

# Cache dependencies separately from source changes
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Empty means "same origin": nginx proxies /api through to the backend, so the
# browser never makes a cross-origin request and the backend's CORS allow-list
# is irrelevant to the deployed stack.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# --- Runtime stage ---
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost/healthz >/dev/null 2>&1 || exit 1
