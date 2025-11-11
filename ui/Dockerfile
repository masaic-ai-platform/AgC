# ─── BUILD STAGE ───────────────────────────────────────────────────────────────
FROM node:18-alpine AS build

# Accept version as build argument (default for development)
ARG UI_VERSION=0.8.0-dev

WORKDIR /app

# Set environment variables for build (baked into the built assets)
ENV VITE_APP_VERSION=${UI_VERSION}

# Install dependencies for build
COPY package*.json ./
RUN npm ci --legacy-peer-deps

# Copy source code
COPY . .

# Build the frontend
RUN npm run build -- --mode prod

# ─── PRODUCTION STAGE ──────────────────────────────────────────────────────────
FROM gcr.io/distroless/nodejs18-debian12 AS production

# Accept version as build argument
ARG UI_VERSION=0.8.0-dev

WORKDIR /app

# Set environment variables for runtime
ENV APP_VERSION=${UI_VERSION}

# Copy built frontend
COPY --from=build /app/dist ./dist

# Copy server entrypoint
COPY server.cjs .

# Expose port 80
EXPOSE 80

# Start the server
CMD ["server.cjs"]
