#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu 24.04 server (an EC2 instance): installs Docker and Git, adds swap,
# and downloads the project. Run it on the server, as the normal user:
#
#   curl -O https://raw.githubusercontent.com/tasleemk17/food-delivery-fullstack/main/deploy/ec2-bootstrap.sh
#   bash ec2-bootstrap.sh
#
# Safe to run again: every step checks first.
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/tasleemk17/food-delivery-fullstack.git}"
APP_DIR="${APP_DIR:-$HOME/food-delivery}"
SWAP_SIZE_GB="${SWAP_SIZE_GB:-2}"

echo "==> Installing Docker, Compose and Git"
sudo apt-get update -y
sudo apt-get install -y docker.io docker-compose-v2 git curl
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

# A little swap keeps the server alive if the Java services and the image builds peak together
if ! swapon --show | grep -q '/swapfile'; then
  echo "==> Adding ${SWAP_SIZE_GB} GB of swap"
  sudo fallocate -l "${SWAP_SIZE_GB}G" /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

if [ ! -d "$APP_DIR/.git" ]; then
  echo "==> Downloading the project into $APP_DIR"
  git clone "$REPO_URL" "$APP_DIR"
fi

cat <<EOF

Done. Next steps (see deploy/README.md for the details):
  1. Log out and log in again, so the docker group applies to you.
  2. Create the two secret files. They are never stored in Git:
       $APP_DIR/backend/.env     (copy of deploy/backend.env.example, filled in)
       $APP_DIR/.env             (optional: ANTHROPIC_API_KEY and passwords)
  3. Start everything:   bash $APP_DIR/deploy/deploy.sh
EOF
