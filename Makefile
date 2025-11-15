# Makefile for LAREX - Convenient commands for development and deployment

.PHONY: help build run test clean docker-build docker-dev docker-prod docker-stop docker-clean

# Default target
help:
	@echo "LAREX - Available commands:"
	@echo ""
	@echo "Local Development:"
	@echo "  make build          - Build the application"
	@echo "  make run            - Run the application locally"
	@echo "  make test           - Run tests"
	@echo "  make clean          - Clean build artifacts"
	@echo ""
	@echo "Docker - Production:"
	@echo "  make docker-prod    - Start LAREX in production mode"
	@echo "  make docker-build   - Build production Docker image"
	@echo "  make docker-logs    - View production logs"
	@echo ""
	@echo "Docker - Development:"
	@echo "  make docker-dev     - Start LAREX in development mode"
	@echo "  make docker-dev-logs - View development logs"
	@echo ""
	@echo "Docker - Management:"
	@echo "  make docker-stop    - Stop all Docker containers"
	@echo "  make docker-clean   - Remove containers and volumes"
	@echo "  make docker-restart - Restart Docker containers"
	@echo ""
	@echo "Setup:"
	@echo "  make setup          - Create required directories and config"

# Local development commands
build:
	./gradlew build

run:
	./gradlew bootRun

test:
	./gradlew test

clean:
	./gradlew clean

# Docker production commands
docker-prod:
	@echo "Starting LAREX in production mode..."
	docker-compose --profile prod up -d
	@echo "LAREX is running at http://localhost:8080"

docker-build:
	@echo "Building production Docker image..."
	docker-compose --profile prod build

docker-logs:
	docker-compose logs -f larex

# Docker development commands
docker-dev:
	@echo "Starting LAREX in development mode..."
	docker-compose --profile dev up

docker-dev-logs:
	docker-compose logs -f larex-dev

# Docker management commands
docker-stop:
	@echo "Stopping all LAREX containers..."
	docker-compose --profile prod down
	docker-compose --profile dev down

docker-clean:
	@echo "Removing all LAREX containers, networks, and volumes..."
	docker-compose --profile prod down -v
	docker-compose --profile dev down -v
	@echo "Cleaning up Docker images..."
	docker image prune -f

docker-restart:
	@echo "Restarting LAREX..."
	docker-compose restart

# Setup command
setup:
	@echo "Creating required directories..."
	@mkdir -p config books savedir
	@if [ ! -f config/larex.yml ]; then \
		echo "Creating default configuration..."; \
		cp config/larex.yml.example config/larex.yml 2>/dev/null || \
		echo "# LAREX Configuration - See DOCKER.md for options\nlarex:\n  bookpath: /home/books\n  localsave: savedir\n  savedir: /home/savedir\n  websave: false" > config/larex.yml; \
	fi
	@echo "Setup complete! Directories and configuration created."
	@echo "Add your books to the 'books/' directory and run 'make docker-prod'"

# Install Git hooks
install-hooks:
	@echo "Installing Git hooks..."
	@cp -f .githooks/pre-commit .git/hooks/pre-commit 2>/dev/null || true
	@chmod +x .git/hooks/pre-commit 2>/dev/null || true
	@echo "Git hooks installed."
