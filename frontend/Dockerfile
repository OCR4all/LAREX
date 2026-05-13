FROM ghcr.io/pnpm/pnpm:11.1.1

RUN pnpm runtime set node 22 -g

WORKDIR /app

# Copy package files first for better layer caching
COPY package.json pnpm-lock.yaml* pnpm-workspace.yaml ./

# Install dependencies
RUN pnpm install --frozen-lockfile

# Copy source code
COPY . .
COPY .env /app/.env

# Expose port
EXPOSE 3000

# Start development server
CMD ["pnpm", "dev"]
