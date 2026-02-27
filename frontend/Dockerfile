# Frontend Development Dockerfile
FROM node:22-alpine

# Set working directory (this should match the project root)
WORKDIR /app

# Install pnpm globally
RUN npm install -g pnpm@latest

# Copy package files first for better layer caching
COPY package.json pnpm-lock.yaml* ./

# Install dependencies
RUN pnpm install --frozen-lockfile

# Copy source code
COPY . .
COPY .env /app/.env

# Expose port
EXPOSE 3000

# Start development server
CMD ["pnpm", "dev"]
