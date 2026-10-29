# Optional: a fixed address and HTTPS (and running it only for demos)

This is an add-on to [README.md](README.md). **The normal HTTP setup is not changed.** `deploy.sh` and `docker-compose.prod.yml` work exactly as before, and you can go back to them at any time (see "Going back"). The add-on is three new files: `docker-compose.https.yml`, `Caddyfile` and `deploy-https.sh`.

What you get:

- **A fixed address.** An Elastic IP stays the same when the server is stopped and started, so a link never breaks.
- **HTTPS on free names** like `https://tomato-shop.duckdns.org`. Caddy gets and renews the certificates by itself.
- **A demo routine**: start the server before an interview, stop it afterwards. Every container has `restart: unless-stopped`, so everything comes back by itself.

```
 browser ──► https://tomato-shop.duckdns.org   ─┐
             https://tomato-admin.duckdns.org   ├─► Caddy (ports 80, 443) ─► customer site / admin site / gateway ─► ...
             https://tomato-api.duckdns.org    ─┘
```

## Setup (about 20 minutes, once)

### 1. Fixed IP address

EC2 console > **Elastic IPs > Allocate Elastic IP address > Allocate**, then **Actions > Associate Elastic IP address** and choose your instance. Do this while the instance is running. From now on connect to the server (SSH) with this address; the old public address is gone.

### 2. Three free names (DuckDNS)

1. Sign in at https://www.duckdns.org (GitHub or Google).
2. Add three names, for example `tomato-shop`, `tomato-admin` and `tomato-api`.
3. For each, put the Elastic IP in the **current ip** box and press **update ip**.

### 3. Open ports 80 and 443

EC2 console > the instance > **Security** > the security group > **Edit inbound rules**. Add:

| Type | Port | Source |
|---|---|---|
| HTTP | 80 | Anywhere-IPv4 |
| HTTPS | 443 | Anywhere-IPv4 |

Keep SSH (22) on **My IP**. In this mode ports 5173, 5174 and 8090 are not used any more and can be removed.

### 4. Tell the server the names

On the server, in the project folder:

```bash
cd ~/food-delivery
nano .env
```

Add three lines (use your own names):

```
SHOP_DOMAIN=tomato-shop.duckdns.org
ADMIN_DOMAIN=tomato-admin.duckdns.org
API_DOMAIN=tomato-api.duckdns.org
```

### 5. Deploy

```bash
git pull
bash deploy/deploy-https.sh
```

The script first checks that each name points at this server (it stops with a clear message if not), rebuilds the websites so they call the HTTPS address, starts everything, and tests the real `https://` addresses. The first run takes several minutes.

Open **https://tomato-shop.duckdns.org**. The admin panel is at `https://tomato-admin.duckdns.org`.

### 6. Stripe (only if you use Stripe webhooks)

In the Stripe dashboard (test mode) the webhook address becomes `https://tomato-api.duckdns.org/api/order/webhook`. The return address after paying is set automatically.

## Demo routine

| When | Do |
|---|---|
| About 10 minutes before | EC2 console > the instance > **Instance state > Start instance**. |
| Wait about 3 minutes | The Java services take a while. Everything starts by itself. |
| Open the link | `https://tomato-shop.duckdns.org` (same link every time). |
| After the demo | **Instance state > Stop instance**. |

## What it costs

A stopped server does not bill for compute. What keeps billing is small: the 30 GB disk (about $2.50 a month) and the public IPv4 address (about $3.60 a month, also while stopped). Together roughly **$6 a month while stopped**, plus about $0.08 to $0.10 an hour only while the server runs. Check current prices on the AWS pricing pages. If your account has credits, they pay for all of this.

## Going back to plain HTTP

```bash
bash deploy/deploy.sh
```

`--remove-orphans` removes Caddy, and the websites are rebuilt for the IP address. (Release the Elastic IP only if you stop using the server for good, otherwise it keeps billing.)

## If something is off

| What you see | Cause and fix |
|---|---|
| The script stops with "does not resolve yet" or "points to ..." | The name on duckdns.org does not have the Elastic IP yet. Fix it, wait a minute, run again. |
| `FAIL` on the https checks, and the Caddy log mentions a challenge or timeout | Ports 80 and 443 are not open in the security group, or the name points elsewhere. |
| The shop opens but menu and login fail with "Mixed Content" or CORS in the browser console | The websites were built before the names were set. Run `bash deploy/deploy-https.sh` again so they are rebuilt. |
| The menu is empty | MongoDB Atlas does not allow the server. In Atlas > Network Access, allow the Elastic IP. |
| Let's Encrypt says "too many certificates" | Too many attempts in a short time. Wait an hour; do not delete the `caddy-data` volume, it keeps the certificates. |
