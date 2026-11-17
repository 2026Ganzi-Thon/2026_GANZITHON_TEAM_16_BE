#!/bin/bash
set -euo pipefail

region="${AWS_REGION:-ap-southeast-2}"
temporary_dir=$(mktemp -d)
trap 'rm -f "$temporary_dir"/*; rmdir "$temporary_dir"' EXIT
curl --fail --silent --show-error --retry 3 --proto '=https' \
  "https://truststore.pki.rds.amazonaws.com/${region}/${region}-bundle.pem" \
  -o "$temporary_dir/bundle.pem"
awk -v target="$temporary_dir" '
  /-----BEGIN CERTIFICATE-----/ { count++; file=target "/ca-" count ".pem" }
  file { print > file }
  /-----END CERTIFICATE-----/ { close(file); file="" }
' "$temporary_dir/bundle.pem"

java_home=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")
cp -L "$java_home/lib/security/cacerts" /var/app/staging/rds-cacerts
count=0
for certificate in "$temporary_dir"/ca-*.pem; do
  test -f "$certificate"
  count=$((count + 1))
  "$java_home/bin/keytool" -importcert -noprompt -trustcacerts \
    -alias "mowoo-rds-${count}" -file "$certificate" \
    -keystore /var/app/staging/rds-cacerts -storepass changeit
done
chmod 644 /var/app/staging/rds-cacerts
