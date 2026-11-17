#!/bin/bash
# Run as root on the Beanstalk instance. Private keys remain under /etc/letsencrypt.
set -euo pipefail
https_domain="${HTTPS_DOMAIN:-}"
if [[ -n "$https_domain" ]]; then
  [[ "$https_domain" =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$ && "$https_domain" == *.* && "$https_domain" != *..* ]]
fi

install -d -m 755 /var/lib/mowoo-acme /etc/mowoo
if ! test -x /opt/mowoo-certbot/bin/certbot; then
  dnf install -y python3.11 python3.11-pip
  python3.11 -m venv /opt/mowoo-certbot
  /opt/mowoo-certbot/bin/pip install 'certbot==5.10.0'
fi

# Keep HTTP-01 validation reachable without forwarding challenge tokens to Spring.
cat > /etc/nginx/conf.d/elasticbeanstalk/10-mowoo-acme.conf <<'NGINX'
location ^~ /.well-known/acme-challenge/ {
    root /var/lib/mowoo-acme;
    default_type text/plain;
    try_files $uri =404;
}
NGINX
nginx -t
systemctl reload nginx

cat > /usr/local/sbin/mowoo-renew-ip-certificate <<'RENEW'
#!/bin/bash
set -euo pipefail
public_ip=$(cat /etc/mowoo/https-ip)
[[ "$public_ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]
for certificate in mowoo-ip mowoo-domain; do
  if test -f "/etc/letsencrypt/renewal/$certificate.conf"; then
    /opt/mowoo-certbot/bin/certbot renew --cert-name "$certificate" --quiet \
      --no-random-sleep-on-renew \
      --deploy-hook '/usr/sbin/nginx -t && /usr/bin/systemctl reload nginx'
  fi
done
RENEW
chmod 750 /usr/local/sbin/mowoo-renew-ip-certificate

metadata_token=$(curl --fail --silent --show-error --noproxy '*' --max-time 5 -X PUT \
  -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' http://169.254.169.254/latest/api/token)
public_ip=$(curl --fail --silent --show-error --noproxy '*' --max-time 5 \
  -H "X-aws-ec2-metadata-token: $metadata_token" http://169.254.169.254/latest/meta-data/public-ipv4)
[[ "$public_ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]
additional_args=()
if test -f /etc/letsencrypt/live/mowoo-ip/fullchain.pem && \
   test "$(cat /etc/mowoo/https-ip 2>/dev/null || true)" != "$public_ip"; then
  additional_args+=(--force-renewal)
fi

/opt/mowoo-certbot/bin/certbot certonly --non-interactive --agree-tos \
  --register-unsafely-without-email --cert-name mowoo-ip \
  --preferred-profile shortlived --webroot --webroot-path /var/lib/mowoo-acme \
  --ip-address "$public_ip" --keep-until-expiring "${additional_args[@]}"
printf '%s\n' "$public_ip" > /etc/mowoo/https-ip
chmod 644 /etc/mowoo/https-ip

if [[ -n "$https_domain" ]]; then
  /opt/mowoo-certbot/bin/certbot certonly --non-interactive --agree-tos \
    --register-unsafely-without-email --cert-name mowoo-domain \
    --webroot --webroot-path /var/lib/mowoo-acme --domain "$https_domain" \
    --keep-until-expiring --renew-with-new-domains
fi
canonical_host="${https_domain:-$public_ip}"

cat > /etc/nginx/mowoo-https-proxy.inc <<'NGINX'
location / {
    proxy_pass http://127.0.0.1:5000;
    proxy_http_version 1.1;
    proxy_set_header Connection $connection_upgrade;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Forwarded-Port 443;
}
NGINX

cat > /etc/nginx/conf.d/50-mowoo-https.conf <<NGINX
map \$uri \$mowoo_https_redirect {
    default 1;
    /health 0;
    ~^/\\.well-known/acme-challenge/ 0;
}
server {
    listen 443 ssl default_server;
    server_name $public_ip;
    ssl_certificate /etc/letsencrypt/live/mowoo-ip/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/mowoo-ip/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:MowooTLS:10m;
    ssl_session_timeout 1d;
    ssl_session_tickets off;
    access_log /var/log/nginx/access.log main;
    include /etc/nginx/mowoo-https-proxy.inc;
}
NGINX
if [[ -n "$https_domain" ]]; then
  cat > /etc/nginx/conf.d/51-mowoo-domain.conf <<NGINX
server {
    listen 443 ssl;
    server_name $https_domain;
    ssl_certificate /etc/letsencrypt/live/mowoo-domain/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/mowoo-domain/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:MowooTLS:10m;
    ssl_session_timeout 1d;
    ssl_session_tickets off;
    access_log /var/log/nginx/access.log main;
    include /etc/nginx/mowoo-https-proxy.inc;
}
NGINX
fi
cat > /etc/nginx/conf.d/elasticbeanstalk/90-mowoo-https-redirect.conf <<NGINX
if (\$mowoo_https_redirect) {
    return 308 https://$canonical_host\$request_uri;
}
NGINX
nginx -t
systemctl reload nginx

cat > /etc/systemd/system/mowoo-certbot-renew.service <<'SERVICE'
[Unit]
Description=Renew MOWOO HTTPS certificates
After=network-online.target nginx.service
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/mowoo-renew-ip-certificate
SERVICE
cat > /etc/systemd/system/mowoo-certbot-renew.timer <<'TIMER'
[Unit]
Description=Check MOWOO certificate renewal twice daily

[Timer]
OnBootSec=10min
OnCalendar=*-*-* 00,12:00:00 UTC
RandomizedDelaySec=15min
Persistent=true

[Install]
WantedBy=timers.target
TIMER
systemctl daemon-reload
systemctl enable --now mowoo-certbot-renew.timer
printf 'HTTPS configured for https://%s; certificate renewal timer enabled.\n' "$canonical_host"
