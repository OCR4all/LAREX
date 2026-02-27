#!/bin/bash
#
# Synchronize VERSION file to all project package files
#
# Usage: ./scripts/version-sync.sh [version]
#   If version is provided, it will update the VERSION file first
#   Otherwise, reads the current VERSION file

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get version from argument or VERSION file
if [ -n "$1" ]; then
    VERSION="$1"
    echo "$VERSION" > "$ROOT_DIR/VERSION"
    echo -e "${YELLOW}Updated VERSION file to: $VERSION${NC}"
else
    if [ ! -f "$ROOT_DIR/VERSION" ]; then
        echo -e "${RED}Error: VERSION file not found at $ROOT_DIR/VERSION${NC}"
        exit 1
    fi
    VERSION=$(tr -d '[:space:]' < "$ROOT_DIR/VERSION")
fi

echo -e "${GREEN}Syncing version: $VERSION${NC}"
echo ""

# Validate semver format
if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9]+)?$ ]]; then
    echo -e "${RED}Error: Invalid version format. Expected semver (e.g., 1.0.0 or 1.0.0-SNAPSHOT)${NC}"
    exit 1
fi

# Update backend/build.gradle.kts
BACKEND_GRADLE="$ROOT_DIR/backend/build.gradle.kts"
if [ -f "$BACKEND_GRADLE" ]; then
    if grep -q '^version = ' "$BACKEND_GRADLE"; then
        sed -i.bak "s/^version = .*/version = \"$VERSION\"/" "$BACKEND_GRADLE"
        rm -f "$BACKEND_GRADLE.bak"
        echo -e "  ${GREEN}✓${NC} Updated backend/build.gradle.kts"
    else
        echo -e "  ${YELLOW}⚠${NC} No version line found in backend/build.gradle.kts"
    fi
else
    echo -e "  ${YELLOW}⚠${NC} backend/build.gradle.kts not found"
fi

# Update frontend/package.json
FRONTEND_PKG="$ROOT_DIR/frontend/package.json"
if [ -f "$FRONTEND_PKG" ]; then
    # Use a temporary file for cross-platform compatibility
    if command -v jq &> /dev/null; then
        jq --arg v "$VERSION" '.version = $v' "$FRONTEND_PKG" > "$FRONTEND_PKG.tmp" && mv "$FRONTEND_PKG.tmp" "$FRONTEND_PKG"
        echo -e "  ${GREEN}✓${NC} Updated frontend/package.json (using jq)"
    else
        # Fallback to sed if jq is not available
        if grep -q '"version"' "$FRONTEND_PKG"; then
            sed -i.bak "s/\"version\": \"[^\"]*\"/\"version\": \"$VERSION\"/" "$FRONTEND_PKG"
            rm -f "$FRONTEND_PKG.bak"
            echo -e "  ${GREEN}✓${NC} Updated frontend/package.json"
        else
            # Add version after name field if it doesn't exist
            sed -i.bak "s/\"name\": \"[^\"]*\"/&,\n  \"version\": \"$VERSION\"/" "$FRONTEND_PKG"
            rm -f "$FRONTEND_PKG.bak"
            echo -e "  ${GREEN}✓${NC} Added version to frontend/package.json"
        fi
    fi
else
    echo -e "  ${YELLOW}⚠${NC} frontend/package.json not found"
fi

# Update keycloak-theme/package.json
THEME_PKG="$ROOT_DIR/keycloak-theme/package.json"
if [ -f "$THEME_PKG" ]; then
    if command -v jq &> /dev/null; then
        jq --arg v "$VERSION" '.version = $v' "$THEME_PKG" > "$THEME_PKG.tmp" && mv "$THEME_PKG.tmp" "$THEME_PKG"
        echo -e "  ${GREEN}✓${NC} Updated keycloak-theme/package.json (using jq)"
    else
        sed -i.bak "s/\"version\": \"[^\"]*\"/\"version\": \"$VERSION\"/" "$THEME_PKG"
        rm -f "$THEME_PKG.bak"
        echo -e "  ${GREEN}✓${NC} Updated keycloak-theme/package.json"
    fi
else
    echo -e "  ${YELLOW}⚠${NC} keycloak-theme/package.json not found"
fi

echo ""
echo -e "${GREEN}Version sync complete!${NC}"
