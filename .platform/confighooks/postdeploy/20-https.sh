#!/bin/bash
set -euo pipefail
if [[ "${ENABLE_IP_HTTPS:-false}" == true ]]; then
  /bin/bash /var/app/current/scripts/configure-https.sh
fi
