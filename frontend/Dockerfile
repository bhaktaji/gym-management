# Multi-stage Dockerfile for GymKhata Frontend (Vite + React SPA)
# Stage 1: Build static assets
FROM node:22-alpine AS builder
WORKDIR /workspace

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# Stage 2: Hardened Nginx runtime (non-root)
FROM nginxinc/nginx-unprivileged:alpine-slim

# Copy SPA nginx configuration
COPY <<'EOF' /etc/nginx/conf.d/default.conf
server {
    listen 8080;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    # Security headers
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # Cache static assets with immutable hashes
    location ~* \.(?:css|js|woff2?|svg|png|jpg|webp)$ {
        expires 1y;
        add_header Cache-Control "public, max-age=31536000, immutable";
    }

    # PWA service worker and manifest must not be cached aggressively
    location ~* (?:sw\.js|manifest\.json)$ {
        expires -1;
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }

    # Fallback to index.html for client-side routing
    location / {
        try_files $uri $uri/ /index.html;
    }
}
EOF

# Copy compiled frontend from builder
COPY --from=builder /workspace/dist /usr/share/nginx/html

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
