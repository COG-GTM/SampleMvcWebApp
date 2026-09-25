#!/bin/bash
# make aws-up: (1) identity check, (2) ECR repo, (3) build+push legacy image, (4) full apply, (5) wait for ALB health.
set -euo pipefail
cd "$(dirname "$0")/../aws"
REGION="${AWS_REGION:-us-east-1}"
TF="${TF:-terraform}"
TAG="${APP_TAG:-$(git -C ../.. rev-parse --short HEAD 2>/dev/null || echo local)}"

echo "[aws-up] identity:"
aws sts get-caller-identity --output table || { echo "[aws-up] FAIL: aws sts get-caller-identity (credentials missing or invalid)"; exit 1; }

$TF init -input=false >/dev/null
echo "[aws-up] phase 1: ECR repository"
$TF apply -input=false -auto-approve -target=aws_ecr_repository.app
REPO="$($TF output -raw ecr_repository_url)"
IMAGE="$REPO:$TAG"

echo "[aws-up] phase 2: build + push $IMAGE (Linux stand-in for the legacy .NET Framework app)"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "${REPO%%/*}"
docker build -f ../legacy/Dockerfile.mono -t "$IMAGE" ../..
docker push "$IMAGE"

echo "[aws-up] phase 3: full footprint"
$TF apply -input=false -auto-approve -var "app_image=$IMAGE"
URL="$($TF output -raw app_url)"

echo "[aws-up] phase 4: waiting for $URL/Home/Health (EC2 bootstrap pulls SQL Server + app; typically 4-7 min)"
for i in $(seq 1 90); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$URL/Home/Health" || true)
  [ "$code" = "200" ] && { echo "[aws-up] healthy: $URL"; exit 0; }
  sleep 10
done
echo "[aws-up] FAIL: ALB never became healthy. Check: aws elbv2 describe-target-health --target-group-arn $($TF output -raw target_group_arn)"
exit 1
