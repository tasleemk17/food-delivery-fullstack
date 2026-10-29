#!/usr/bin/env bash
# Like deploy.sh, but serves the project over HTTPS on your own names. Run it on the server:
#   bash deploy/deploy-https.sh
# deploy.sh is untouched: run it any time to go back to plain HTTP on the server's IP address.
#
# Before the first run (details in deploy/HTTPS.md):
#   - the server has a fixed (Elastic) IP, and ports 80 and 443 are open in the security group
#   - three names point at that IP, and are written in .env next to docker-compose.yml:
#       SHOP_DOMAIN=tomato-shop.duckdns.org
#       ADMIN_DOMAIN=tomato-admin.duckdns.org
#       API_DOMAIN=tomato-api.duckdns.org
set -euo pipefail

cd "$(dirname "$0")/.."

fail() { echo "ERROR: $*" >&2; exit 1; }

[ -f backend/.env ] || fail "backend/.env is missing. Copy deploy/backend.env.example to backend/.env and fill it in."
grep -q '^MONGODB_URI=.\+' backend/.env || fail "backend/.env has no MONGODB_URI."
grep -q '^JWT_SECRET=.\{16,\}' backend/.env || fail "backend/.env needs a JWT_SECRET of at least 16 characters."
grep -q '^INTERNAL_API_KEY=.\{16,\}' backend/.env || fail "backend/.env needs an INTERNAL_API_KEY of at least 16 characters."

# The three names: a value already in the environment wins, then .env
for name in SHOP_DOMAIN ADMIN_DOMAIN API_DOMAIN; do
  if [ -z "${!name:-}" ] && [ -f .env ]; then
    printf -v "$name" '%s' "$(grep -E "^${name}=" .env | tail -n1 | cut -d= -f2- | tr -d '"' | tr -d "'" | tr -d '[:space:]' || true)"
  fi
  [ -n "${!name:-}" ] || fail "$name is not set. Put $name=<your-name>.duckdns.org in .env (see deploy/HTTPS.md)."
  export "$name"
done
# docker-compose.prod.yml insists on PUBLIC_HOST; the shop name serves
export PUBLIC_HOST="$SHOP_DOMAIN"

# This server's public address, from the instance itself
token="$(curl -fsS -m 3 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' 2>/dev/null || true)"
server_ip=""
if [ -n "$token" ]; then
  server_ip="$(curl -fsS -m 3 -H "X-aws-ec2-metadata-token: $token" http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || true)"
fi

# Certificates can only be issued when each name already points at this server. Asking too early fails and
# counts against Let's Encrypt's limits, so stop here with a clear message instead.
echo "==> Checking that the names point at this server${server_ip:+ ($server_ip)}"
for name in "$SHOP_DOMAIN" "$ADMIN_DOMAIN" "$API_DOMAIN"; do
  resolved="$(getent ahostsv4 "$name" 2>/dev/null | awk 'NR==1{print $1}' || true)"
  [ -n "$resolved" ] || fail "$name does not resolve yet. Set it on duckdns.org to this server's IP and wait a minute."
  if [ -n "$server_ip" ] && [ "$resolved" != "$server_ip" ]; then
    fail "$name points to $resolved but this server is $server_ip. Fix it on duckdns.org (and use an Elastic IP so it never changes)."
  fi
  echo "    ok    $name -> $resolved"
done

echo "==> Getting the latest code"
git fetch --quiet origin
git pull --ff-only --quiet || fail "git pull failed: the server copy has local changes. Fix or discard them first."
echo "    now at $(git log -1 --format='%h %s')"

COMPOSE=(docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml -f deploy/docker-compose.https.yml)

echo "==> Building and starting (the websites are rebuilt so they call the HTTPS address)"
"${COMPOSE[@]}" up -d --build --remove-orphans

echo "==> Checking the stack through the public HTTPS names"
# The Java services need up to a minute, and the first visit to each name makes Caddy fetch its certificate,
# so every check is retried for about 3 minutes before it counts as failed.
check() {
  for _ in $(seq 1 36); do
    if curl -fsS -m 10 "$2" >/dev/null 2>&1; then echo "    ok    $1"; return; fi
    sleep 5
  done
  echo "    FAIL  $1  ($2)"; FAILED=1
}
FAILED=0
check "gateway health"       "https://$API_DOMAIN/actuator/health"
check "menu (Node backend)"  "https://$API_DOMAIN/api/food/list"
check "stock (inventory)"    "https://$API_DOMAIN/api/inventory/availability"
check "recommendations (AI)" "https://$API_DOMAIN/api/ai/recommendations/popular"
check "customer website"     "https://$SHOP_DOMAIN/"
check "admin website"        "https://$ADMIN_DOMAIN/"
[ "$FAILED" -eq 0 ] || { "${COMPOSE[@]}" ps; echo "Caddy log (certificate problems show here):"; "${COMPOSE[@]}" logs --tail 30 caddy; fail "some checks failed (see above)."; }

docker image prune -f >/dev/null

echo
echo "Deployed over HTTPS.  Customer app: https://$SHOP_DOMAIN   Admin: https://$ADMIN_DOMAIN"
echo "Stripe webhook address (test mode):  https://$API_DOMAIN/api/order/webhook"
