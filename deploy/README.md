# Deploying to AWS (one EC2 server)

The whole project (14 containers) runs on **one EC2 server** with Docker Compose, exactly as it does on a laptop. MongoDB stays on Atlas. This is the simplest way to put the project online, and the cheapest: no load balancer, no managed database, nothing that bills while idle except the server itself.

```
 browser ──► EC2 server (Ubuntu 24.04, Docker Compose)
              ├─ :5173  customer website   ├─ gateway ─► backend, order, inventory, ai ...
              ├─ :5174  admin panel        ├─ Kafka, 4 x PostgreSQL, notification-service
              └─ :8090  gateway (API)      └─ MailHog (only on the server itself, not public)
                                   MongoDB Atlas (outside AWS)
```

What this setup is, and is not:

- **HTTP only, no HTTPS.** Fine for a demo with Stripe *test* keys. Real payments need HTTPS and a domain (a later step). An optional add-on gives a fixed IP and free HTTPS names: see [HTTPS.md](HTTPS.md).
- **One server, one Kafka node.** It shows the architecture; it is not highly available.
- **Emails go to MailHog**, a test inbox, not to real mailboxes. Read them over an SSH tunnel (step 9).

## Cost (check the current prices on aws.amazon.com/ec2/pricing)

| Item | About |
|---|---|
| `t3.large` server (2 CPU, 8 GB) | roughly $0.08 per hour, about $2 for a day, about $60 for a month if left on |
| Disk, 30 GB | roughly $2.50 per month, even while the server is stopped |
| Public IPv4 address | roughly $3.60 per month, also when the server is stopped (an Elastic IP is billed too) |

**The server is not free-tier size**: the project needs about 2.5 GB of memory, a free `t3.micro` has 1 GB. The way to keep it cheap is to **run the server only while you need it** (for a demo or an interview) and **terminate it when you are done** (step 10). Set a budget alert first (step 1).

If your new account shows credits, they pay for this; look at *Billing > Credits*.

## Step by step

### 1. Protect your wallet (5 minutes, do this first)

AWS console > **Billing and Cost Management > Budgets > Create budget** > *Monthly cost budget*, amount `5` (dollars), add your email for alerts. Never create an access key for the root account, and turn on MFA for it.

### 2. Launch the server

AWS console > **EC2 > Launch instance** (pick the region closest to you, for example Mumbai `ap-south-1`):

| Setting | Value |
|---|---|
| Name | `food-delivery` |
| Image | **Ubuntu Server 24.04 LTS** |
| Instance type | **t3.large** (a `t3.medium` also works but the first build is slow) |
| Key pair | *Create new key pair*, type RSA, format `.pem`. Keep the downloaded file safe; it is the only way in. |
| Storage | **30 GiB** gp3 |
| Network | *Auto-assign public IP*: enabled |

**Security group** (the firewall). Create one with these rules:

| Port | Source | For |
|---|---|---|
| 22 | **My IP** | SSH (you) |
| 5173 | Anywhere (`0.0.0.0/0`) | customer website |
| 8090 | Anywhere (`0.0.0.0/0`) | gateway: the websites call it from the visitor's browser |
| 5174 | **My IP** | admin panel (only you) |

Nothing else is opened: Kafka, the databases, MailHog and the services have no public port. (If your home IP changes, edit the rules for 22 and 5174.)

### 3. Let the server reach MongoDB Atlas

Atlas > **Network Access > Add IP address** > the server's public IPv4 address (EC2 console > the instance > *Public IPv4 address*). Without it the backend cannot start.

### 4. Set up the server (once)

```bash
ssh -i path/to/your-key.pem ubuntu@<server-ip>
curl -O https://raw.githubusercontent.com/tasleemk17/food-delivery-fullstack/main/deploy/ec2-bootstrap.sh
bash ec2-bootstrap.sh
exit
```

The script installs Docker and Git, adds 2 GB of swap and downloads the project into `~/food-delivery`. Log in again afterwards, so the `docker` group applies.

### 5. Add the secret files (they are never in Git)

On the server:

```bash
cd ~/food-delivery
cp deploy/backend.env.example backend/.env
nano backend/.env          # fill in MONGODB_URI, JWT_SECRET, INTERNAL_API_KEY, Stripe test keys
```

Make the two secrets with `openssl rand -hex 32`. Use **new** values for the server, not the ones from your laptop. Optionally also `cp deploy/root.env.example .env` for the chatbot key and your own database passwords.

### 6. Copy the dish images

The menu is in Atlas, but the pictures are files on the backend's disk, and not all of them are in Git. From your laptop (in the project folder):

```bash
scp -i path/to/your-key.pem -r backend/uploads/. ubuntu@<server-ip>:~/food-delivery/backend/uploads/
```

### 7. Start everything

```bash
bash ~/food-delivery/deploy/deploy.sh
```

The first run builds all images and takes about 10 to 15 minutes. At the end it checks the gateway, the menu, the stock, the recommendations and both websites, and prints the addresses. Open `http://<server-ip>:5173`.

### 8. Stripe (test mode)

Stripe dashboard (test mode) > **Developers > Webhooks > Add endpoint**: URL `http://<server-ip>:8090/api/order/webhook`, event `checkout.session.completed` (and the failed-payment events your order-service handles). Copy the signing secret (`whsec_...`) into `backend/.env` as `STRIPE_WEBHOOK_SECRET`, then run `deploy.sh` again. Use only test keys and the Stripe test cards.

### 9. Check it from outside, and read the emails

From your laptop, the end-to-end tests can run against the server (the order email lives in MailHog, which is private, so open a tunnel first):

```bash
ssh -i path/to/your-key.pem -L 8025:localhost:8025 ubuntu@<server-ip>      # leave it open, and in a second terminal:
cd e2e
E2E_APP_URL=http://<server-ip>:5173 E2E_API_URL=http://<server-ip>:8090 npm test
```

The inbox itself is then at http://localhost:8025 on your laptop.

### 10. Continuous deployment (GitHub Actions)

The workflow **Deploy** (`.github/workflows/deploy.yml`) updates the server with one click. It is started by hand, never by a push, so it cannot spend money by surprise.

1. On your laptop make a key only for this: `ssh-keygen -t ed25519 -f deploy_key -N ""`. Append `deploy_key.pub` to `~/.ssh/authorized_keys` on the server. Then delete both local files after step 3.
2. GitHub > repository **Settings > Environments > New environment** named `production`. Add these *environment secrets*:

   | Secret | Value |
   |---|---|
   | `EC2_HOST` | the server's public IP (or domain) |
   | `EC2_USER` | `ubuntu` |
   | `EC2_SSH_KEY` | the whole content of `deploy_key` (the private one) |

3. **Actions > Deploy > Run workflow.** It runs `deploy/deploy.sh` on the server and then checks the website from the internet.

Tip: in the environment settings you can add yourself as a *required reviewer*, so every deploy needs a click of approval.

### 11. Stop paying

| You do | Effect |
|---|---|
| **Stop** the instance | no server charge; the disk and the IP address are still billed. After starting again the public IP is **new**: add it to Atlas Network Access and the security group is unchanged; then run `deploy.sh` (it finds the new IP and rebuilds the websites with it). |
| **Terminate** the instance | everything on the server is deleted (order history in its PostgreSQL databases too). Billing stops. Also release any Elastic IP and delete leftover volumes. |

For a demo: launch, run steps 4 to 7 (about 30 minutes), show it, terminate. A fixed address (Elastic IP) is only worth it if the server stays for weeks.

## What is in this folder

| File | Purpose |
|---|---|
| `ec2-bootstrap.sh` | one-time server setup |
| `deploy.sh` | update + start + health checks (also used by the workflow) |
| `docker-compose.prod.yml` | the few settings that differ from a laptop (public address, no public backend port) |
| `backend.env.example`, `root.env.example` | what to put in the two secret files |

## Later steps (not done yet)

- A domain name and HTTPS (a reverse proxy such as Caddy, or an AWS load balancer with a certificate), needed for real Stripe payments.
- Images built once in GitHub Actions and stored in a registry (ECR), so the server only pulls.
- Managed PostgreSQL (RDS) and Kafka (MSK) instead of containers. Better operations, but they bill every hour.
