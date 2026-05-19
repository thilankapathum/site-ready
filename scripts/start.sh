#!/usr/bin/env bash
# =============================================================================
# SSV DMS — First-time setup and startup script
# Run: chmod +x scripts/start.sh && ./scripts/start.sh
# =============================================================================
set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
info()    { echo -e "${BLUE}[INFO]${NC}  $1"; }
success() { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

echo ""
echo "╔═══════════════════════════════════════════════╗"
echo "║       SSV Document Management System          ║"
echo "║            First-Time Setup                   ║"
echo "╚═══════════════════════════════════════════════╝"
echo ""

# 1. Check prerequisites
info "Checking prerequisites…"
command -v docker  >/dev/null 2>&1 || error "Docker is not installed. Install from https://docs.docker.com/get-docker/"
command -v keytool >/dev/null 2>&1 || error "keytool not found. Install a JDK (java.com) and add it to PATH."
success "Docker and keytool found."

# 2. Create .env if missing
if [ ! -f ".env" ]; then
  warn ".env not found. Creating from .env.example…"
  cp .env.example .env

  # Auto-generate JWT secret
  JWT=$(openssl rand -base64 64 | tr -d '\n')
  sed -i.bak "s|change_me_generate_with_openssl_rand_base64_64|$JWT|" .env && rm -f .env.bak
  success ".env created with auto-generated JWT secret."
  warn "Review .env and set strong passwords before continuing in production."
else
  success ".env already exists."
fi

# 3. Generate signing keystore
if [ ! -f "keystore/ssv-signing.p12" ]; then
  info "Generating PDF signing keystore…"
  bash scripts/generate-keystore.sh
  success "Keystore generated at keystore/ssv-signing.p12"
else
  success "Keystore already exists."
fi

# 4. Build and start
info "Building Docker images (this may take a few minutes on first run)…"
docker compose build --parallel

info "Starting all services…"
docker compose up -d

# 5. Wait for API health
info "Waiting for API to become healthy…"
MAX_WAIT=90; WAITED=0
until curl -sf http://localhost/health > /dev/null 2>&1; do
  if [ $WAITED -ge $MAX_WAIT ]; then
    warn "API did not become healthy within ${MAX_WAIT}s."
    warn "Check logs with: docker compose logs api"
    break
  fi
  sleep 3; WAITED=$((WAITED+3))
  echo -n "."
done
echo ""
success "All services are up!"

echo ""
echo "╔═══════════════════════════════════════════════╗"
echo "║               Access Points                   ║"
echo "╠═══════════════════════════════════════════════╣"
echo "║  Web UI     →  http://localhost               ║"
echo "║  API        →  http://localhost/api           ║"
echo "║  MinIO UI   →  http://localhost:9001          ║"
echo "╠═══════════════════════════════════════════════╣"
echo "║  Default Admin Credentials:                   ║"
echo "║    Email:    admin@ssv-dms.local              ║"
echo "║    Password: Admin@123                        ║"
echo "║                                               ║"
echo "║  ⚠  CHANGE THE ADMIN PASSWORD IMMEDIATELY    ║"
echo "╚═══════════════════════════════════════════════╝"
echo ""