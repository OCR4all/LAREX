# Docs Development Dockerfile
FROM node:22-alpine

WORKDIR /workspace/docs

RUN corepack enable && corepack prepare pnpm@11.6.0 --activate

COPY docs/package.json docs/pnpm-lock.yaml docs/pnpm-workspace.yaml docs/.npmrc ./

RUN pnpm install --frozen-lockfile

COPY . /workspace

EXPOSE 3000

CMD ["pnpm", "run", "dev", "--host", "0.0.0.0", "--port", "3000"]
