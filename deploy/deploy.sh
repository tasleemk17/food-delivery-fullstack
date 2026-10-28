#!/usr/bin/env bash
# Updates the project on the server and (re)starts every container. Run it on the server:
#   bash deploy/deploy.sh
# The GitHub Actions workflow "Deploy" runs exactly this over SSH.
#
# What it does: checks the secret files exist, pulls the latest code, rebuilds, starts, waits until the
# gateway is healthy, and checks a few real URLs. It stops with a clear message if something is missing.
set -euo pipefail

cd "$(dirname "$0")/.."

fail() { echo "ERROR: $*" >&2; exit 1; }

[ -f backend/.env ] || fail "backend/.env is missing. Copy deploy/backend.env.example to backend/.env and fill it in."
grep -q '^MONGODB_URI=.\+' backend/.env || fail "backend/.env has no MONGODB_URI."
grep -q '^JWT_SECRET=.\{16,\}' backend/.env || fail "backend/.env needs a JWT_SECRET of at least 16 characters."
grep -q '^INTERNAL_API_KEY=.\{16,\}' backend/.env || fail "backend/.env needs an INTERNAL_API_KEY of at least 16 characters."

# The address people use. A value already in the environment or in .env wins; on EC2 it is looked up,
# which is why a server without a fixed (Elastic) IP still works after a restart.
if [ -z "${PUBLIC_HOST:-}" ] && [ -f .env ]; then
  PUBLIC_HOST="$(grep -E '^PUBLIC_HOST=' .env | tail -n1 | cut -d= -f2- | tr -d '"' | tr -d "'" || true)"
fi
if [ -z "${PUBLIC_HOST:-}" ]; then
  token="$(curl -fsS -m 3 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' 2>/dev/null || true)"
  if [ -n "$token" ]; then
    PUBLIC_HOST="$(curl -fsS -m 3 -H "X-aws-ec2-metadata-token: $token" http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || true)"
  fi
fi
[ -n "${PUBLIC_HOST:-}" ] || fail "PUBLIC_HOST is unknown. Put PUBLIC_HOST=<server ip or domain> in .env."
export PUBLIC_HOST
echo "==> Public address: http://$PUBLIC_HOST:5173"

echo "==> Getting the latest code"
git fetch --quiet origin
git pull --ff-only --quiet || fail "git pull failed: the server copy has local changes. Fix or discard them first."
echo "    now at $(git log -1 --format='%h %s')"

COMPOSE=(docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml)

echo "==> Building and starting (the first time takes several minutes)"
"${COMPOSE[@]}" up -d --build --remove-orphans

echo "==> Waiting for the gateway"
for i in $(seq 1 60); do
  if curl -fsS -m 3 http://localhost:8090/actuator/health >/dev/null 2>&1; then break; fi
  [ "$i" -eq 60 ] && { "${COMPOSE[@]}" ps; fail "the gateway did not become healthy in 5 minutes. Look at: docker compose logs gateway"; }
  sleep 5
done

echo "==> Checking the stack"
# The Java services need up to a minute to start after the containers are "Up", so each check is
# tried again for about 2 minutes before it counts as failed.
check() {
  for _ in $(seq 1 24); do
    if curl -fsS -m 10 "$2" >/dev/null 2>&1; then echo "    ok    $1"; return; fi
    sleep 5
  done
  echo "    FAIL  $1  ($2)"; FAILED=1
}
FAILED=0
check "gateway health"      http://localhost:8090/actuator/health
check "menu (Node backend)" http://localhost:8090/api/food/list
check "stock (inventory)"   http://localhost:8090/api/inventory/availability
check "recommendations (AI)" http://localhost:8090/api/ai/recommendations/popular
check "customer website"    http://localhost:5173/
check "admin website"       http://localhost:5174/
[ "$FAILED" -eq 0 ] || { "${COMPOSE[@]}" ps; fail "some checks failed (see above)."; }

# Old image layers pile up with every deploy and the disk is small
docker image prune -f >/dev/null

echo
echo "Deployed.  Customer app: http://$PUBLIC_HOST:5173   Admin: http://$PUBLIC_HOST:5174"
