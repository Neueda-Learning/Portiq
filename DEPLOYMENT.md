# Deployment

Portiq deploys as three containers on a Linux host: nginx serving the built SPA
and proxying `/api` to Spring Boot, and MySQL behind both. Only the frontend
publishes a host port.

```text
browser ──> :8090  frontend (nginx)  ──/api/*──> backend (Spring Boot :4001)
                   serves /assets, SPA fallback              │
                                                             v
                                                       mysql (:3306)
                                          all internal; no published port
```

Serving the API through nginx on the same origin means the browser issues no
preflight, so the CORS allow-list in `SecurityConfig` plays no part in a
deployed stack. It still governs `npm run dev` against a local backend.

## Split of responsibilities

| | Runs where | Does what |
|---|---|---|
| **GitHub Actions** | GitHub runners | CI: backend tests, frontend build, both images build |
| **Jenkins** | your Linux host | CD: build images, deploy the stack, smoke test |

Actions never deploys. Jenkins never gates a pull request.

---

## Part 1 — Deploy manually first

Get this working before adding Jenkins. If it fails here it will fail there too,
with more moving parts in the way.

**1. Install Docker and the Compose plugin.**

Amazon Linux 2023 (EC2):

```bash
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
newgrp docker
```

AL2023's `docker` package does **not** include Compose v2, and this project needs
it — `docker compose` (plugin), not the old `docker-compose` script. Check, and
install it if missing:

```bash
docker compose version   # if this errors, run the block below
```

```bash
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL \
  https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
docker compose version   # must now print v2.x
```

Ubuntu / Debian / WSL:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"
newgrp docker
docker compose version
```

**Memory.** Building the backend image runs Maven inside the container, which
wants roughly 2GB. On a 1GB instance (`t2.micro`, `t3.micro`) it will be killed
mid-build. Check with `free -h`; if you have under 2GB, add swap once:

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

You do not need Maven, Java or Node on the host for the application — every
build happens inside a container. Jenkins needs Java 17, covered below.

**2. Configure:**

```bash
cp .env.prod.example .env.prod
openssl rand -base64 48   # -> JWT_SECRET
openssl rand -base64 32   # -> DB_ENCRYPTION_KEY
```

Fill in `DB_PASSWORD`, `DB_ROOT_PASSWORD`, and `OWNER_PASSWORD` too. Compose
refuses to start if any required value is blank, rather than booting a stack
with an empty secret.

> Keep `DB_ENCRYPTION_KEY` stable once you have real data. Holdings, prices and
> dates are encrypted at rest with it; changing it makes existing rows
> unreadable.

**3. Run:**

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

**4. Check:**

```bash
curl -fsS http://localhost:8090/actuator/health   # {"status":"UP"}
curl -fsSI http://localhost:8090/                 # 200
```

Open <http://localhost:8090> and log in with `OWNER_USERNAME` / `OWNER_PASSWORD`.

Useful afterwards:

```bash
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml down        # stop, keep the database
docker compose -f docker-compose.prod.yml down -v     # stop and delete the database
```

---

## Part 2 — Jenkins

**1. Install** (Jenkins needs Java 17+).

Amazon Linux 2023:

```bash
sudo dnf install -y java-17-amazon-corretto
sudo wget -O /etc/yum.repos.d/jenkins.repo https://pkg.jenkins.io/redhat-stable/jenkins.repo
sudo rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io-2023.key
sudo dnf install -y jenkins
```

Ubuntu / Debian:

```bash
sudo apt update && sudo apt install -y openjdk-17-jre
curl -fsSL https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key \
  | sudo tee /usr/share/keyrings/jenkins-keyring.asc > /dev/null
echo "deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] \
  https://pkg.jenkins.io/debian-stable binary/" \
  | sudo tee /etc/apt/sources.list.d/jenkins.list > /dev/null
sudo apt update && sudo apt install -y jenkins
```

**Check whether Jenkins is already running before installing anything** — a
shared or pre-provisioned VM often has it already:

```bash
sudo systemctl status jenkins --no-pager | head -3
sudo ss -tlnp | grep ':8080'
```

If it is running, skip the install and go straight to step 2. The app deliberately
uses 8090 so it coexists with a Jenkins on its default 8080.

Only if you are installing fresh and something else holds 8080, move Jenkins:

```bash
sudo mkdir -p /etc/systemd/system/jenkins.service.d
sudo tee /etc/systemd/system/jenkins.service.d/override.conf > /dev/null <<'EOF'
[Service]
Environment="JENKINS_PORT=8081"
EOF
sudo systemctl daemon-reload
sudo systemctl enable --now jenkins
```

On WSL, `systemctl` only works if systemd is enabled — add this to
`/etc/wsl.conf` and run `wsl --shutdown` from PowerShell:

```ini
[boot]
systemd=true
```

Without systemd, run it directly instead: `java -jar jenkins.war --httpPort=8081`.

**2. Let Jenkins use Docker.** The pipeline shells out to `docker`, so the
`jenkins` user needs the docker group:

```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

Verify: `sudo -u jenkins docker ps` should list containers, not permission-deny.

**3. Unlock** at <http://localhost:8081>:

```bash
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
```

Install suggested plugins, then add **Pipeline**, **Git**, and
**Docker Pipeline** if they are not already present.

## Running on EC2

Two extra things apply when the host is an EC2 instance.

**Reaching the UIs.** If you have no AWS console access, or would rather not open
inbound ports at all, forward them over the SSH session instead. Run this from
your own machine, not the VM, and leave it open:

```bash
ssh -L 8080:localhost:8080 -L 8090:localhost:8090 ec2-user@<vm-host>
```

Then `http://localhost:8080` is Jenkins and `http://localhost:8090` is the app.
Reaching the app on `localhost` this way also makes biometric login work, which
it would not over a public IP.

Otherwise, open the ports in the instance's security group, sourced to your own
IP rather than `0.0.0.0/0`:

| Port | For |
|---|---|
| 8090 | the application |
| 8080 | the Jenkins UI |

**Origin.** Set `WEBAUTHN_ORIGIN` in `.env.prod` to the URL you actually type,
for example `http://<public-ip>:8090`, and `WEBAUTHN_RP_ID` to the bare host.
Note that biometric login will still not work over plain HTTP to a public IP —
WebAuthn requires a secure context, which only `localhost` and HTTPS satisfy.
Password login is unaffected. To demo biometrics, either use an SSH tunnel so
the browser sees `localhost`:

```bash
ssh -L 8090:localhost:8090 ec2-user@<public-ip>
```

or put the app behind HTTPS with a real domain.

**4. Add credentials.** Manage Jenkins → Credentials → System → Global →
Add Credentials. Each is kind **Secret text**, and the ID must match exactly:

| ID | Value |
|---|---|
| `portiq-db-password` | application DB password |
| `portiq-db-root-password` | MySQL root password |
| `portiq-jwt-secret` | `openssl rand -base64 48` |
| `portiq-encryption-key` | `openssl rand -base64 32` |
| `portiq-owner-password` | password for the seeded login |

Use the same values as the manual `.env.prod` run, or the existing database
volume will reject the new credentials.

**5. Create the job.** New Item → **Pipeline** → name it `portiq-cd`.

- Under **Pipeline**, choose *Pipeline script from SCM*
- SCM: Git, Repository URL: `https://github.com/Neueda-Learning/Portiq.git`
- Branch: `*/main`
- Script Path: `Jenkinsfile`

For a private repo, add a GitHub token as a *Username with password* credential
and select it here.

**6. Trigger it.** Simplest is Build Triggers → *Poll SCM* with `H/5 * * * *`,
which checks GitHub every five minutes. A webhook is faster but needs Jenkins to
be reachable from GitHub — not the case on a local VM without a tunnel.

**7. Build Now**, and watch the stage view.

---

## What the pipeline does

| Stage | Purpose |
|---|---|
| Checkout | Pull `main` |
| Verify tooling | Fail early if Docker is missing or unreachable |
| Validate compose | `docker compose config` — catches a bad file or missing secret before touching the running stack |
| Build images | `docker compose build --pull` |
| Deploy | `docker compose up -d --remove-orphans` |
| Smoke test | Waits for `/actuator/health` to report UP, then checks the SPA and its routing fallback |

On failure it dumps `docker compose ps` and the last 200 log lines, which is what
you actually need to diagnose it. `disableConcurrentBuilds()` stops two deploys
racing over the same compose project.

---

## Troubleshooting

**`permission denied` on `/var/run/docker.sock`** — step 2 was missed or Jenkins
was not restarted after it.

**Compose errors with `DB_PASSWORD must be set`** — a credential ID does not
match the table above. The `Jenkinsfile` environment block names them exactly.

**Backend restarts in a loop** — check `docker compose logs backend`. Usually
MySQL credentials that disagree with the existing volume. Either restore the
original values or `docker compose -f docker-compose.prod.yml down -v` to discard
the database and start clean.

**Biometric login fails after deploying** — `WEBAUTHN_ORIGIN` must exactly match
the URL in the address bar, and WebAuthn requires a secure context.
`http://localhost:8090` qualifies; `http://192.168.x.x:8090` does not.

**Smoke test times out but the app works in a browser** — the backend takes
longer than 150s to boot on a cold first run while Hibernate creates the schema.
Re-run; raise the loop bound in the `Jenkinsfile` if it persists.
