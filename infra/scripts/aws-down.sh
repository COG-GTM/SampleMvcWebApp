#!/bin/bash
# make aws-down: terraform destroy, then prove nothing tagged Project=<project> is left in the account.
set -euo pipefail
cd "$(dirname "$0")/../aws"
REGION="${AWS_REGION:-us-east-1}"
TF="${TF:-terraform}"
PROJECT="${PROJECT:-modernization-demo}"
TRACK="${TRACK:-dotnet}"

$TF init -input=false >/dev/null
echo "[aws-down] terraform destroy"
$TF destroy -input=false -auto-approve

echo "[aws-down] verifying zero resources tagged Project=$PROJECT Track=$TRACK in $REGION"
for attempt in 1 2 3 4 5 6; do
  left=$(aws resourcegroupstaggingapi get-resources --region "$REGION" \
    --tag-filters "Key=Project,Values=$PROJECT" "Key=Track,Values=$TRACK" --query 'ResourceTagMappingList[].ResourceARN' --output text | tr '\t' '\n' | sed '/^$/d')
  if [ -z "$left" ]; then
    echo "[aws-down] OK: 0 tagged resources remain"
    exit 0
  fi
  echo "[aws-down] still tagged (tag index lags a few minutes after deletion), attempt $attempt:"; echo "$left"
  sleep 30
done
echo "[aws-down] FAIL: tagged resources remain — delete manually:"; echo "$left"
exit 1
