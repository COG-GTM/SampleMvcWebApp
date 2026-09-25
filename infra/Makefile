# SampleWebApp infra — run from repo root with `make -C infra <target>` or from infra/ with `make <target>`.
SHELL := /bin/bash
COMPOSE ?= docker compose --env-file secrets/.env -f docker-compose.yml
LB_PORT ?= 8000
LB_URL  ?= http://localhost:$(LB_PORT)
PROM_PORT ?= 9091
K6_UID ?= $(shell id -u)
export LB_PORT PROM_PORT K6_UID
DEMO_DIR := ../demo
OUT_DIR  := ../load/out

.PHONY: help up down reset build logs ps baseline loadtest verify seed render-acl secrets clean

help:
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

secrets: ## create secrets/.env from the example if missing
	@test -f secrets/.env || { cp secrets/.env.example secrets/.env; echo "created secrets/.env from example"; }

render-acl: ## render firewall allowlist into nginx allow/deny
	@mkdir -p firewall/generated
	@sh firewall/render-nginx-acl.sh firewall/allowlist.rules > firewall/generated/lb-access.conf

build: secrets ## build the app image (APP_DOCKERFILE / APP_VERSION override)
	$(COMPOSE) build app

up: secrets render-acl ## build + start the whole stack, wait for LB health, seed the DB
	$(COMPOSE) up -d --build --force-recreate --wait --wait-timeout 300 || { $(COMPOSE) ps; $(COMPOSE) logs --tail=40 app lb db-init; exit 1; }
	@$(MAKE) --no-print-directory seed
	@echo "LB: $(LB_URL)   Grafana: http://localhost:3000 (admin/admin)   Prometheus: http://localhost:$(PROM_PORT)"

seed: ## (re)seed the sample data through the app (deterministic Medium data set)
	@bash scripts/seed.sh "$(LB_URL)"

down: ## stop the stack (keeps DB volume)
	$(COMPOSE) --profile tools down --remove-orphans

reset: ## stop the stack and delete all state (DB, logs, Grafana, k6 output)
	$(COMPOSE) --profile tools down -v --remove-orphans
	rm -rf $(OUT_DIR)/*.json firewall/generated

ps: ; $(COMPOSE) ps
logs: ; $(COMPOSE) logs -f --tail=100

baseline: ## capture the pre-migration NFR baseline -> demo/BASELINE.json
	@mkdir -p $(OUT_DIR)
	$(COMPOSE) run --rm -e LABEL=baseline -e VUS=$(or $(VUS),20) -e DURATION=$(or $(DURATION),60s) \
	  -e P95_MAX_MS=100000 -e P99_MAX_MS=100000 -e ERR_RATE_MAX=0.01 k6 run /scripts/k6-main.js
	@python3 scripts/annotate.py $(OUT_DIR)/baseline.json > $(DEMO_DIR)/BASELINE.json
	@echo "wrote $(DEMO_DIR)/BASELINE.json"

loadtest: ## run the load test against the current deployment, gated by thresholds derived from demo/BASELINE.json
	@mkdir -p $(OUT_DIR)
	@bash scripts/loadtest.sh "$(or $(LABEL),loadtest)" "$(or $(VUS),20)" "$(or $(DURATION),60s)"

verify: ## runtime-contract checks + health via LB + smoke + reporting consumer + gated load test
	@bash scripts/verify.sh "$(LB_URL)"

clean: reset

# ---- AWS "real infra" path (infra/aws, used by the Assess act) --------------------------------------
TF        ?= terraform
AWS_DIR   := aws
AWS_REGION ?= us-east-1
TRACK ?= dotnet
export AWS_REGION TRACK

.PHONY: aws-up aws-down aws-baseline aws-discover aws-status

aws-up: ## build + push legacy image to ECR, terraform apply the ALB/SG/EC2/Route53/Secrets footprint
	@bash scripts/aws-up.sh

aws-down: ## terraform destroy + assert zero resources tagged Project=modernization-demo remain
	@bash scripts/aws-down.sh

aws-status: ## list tagged resources currently in the account
	@aws resourcegroupstaggingapi get-resources --tag-filters Key=Project,Values=modernization-demo Key=Track,Values=$(TRACK) \
	  --query 'ResourceTagMappingList[].ResourceARN' --output text | tr '\t' '\n' | sort

aws-baseline: ## k6 against the ALB DNS name -> demo/BASELINE.aws.json
	@mkdir -p $(OUT_DIR)
	$(eval ALB_URL := $(shell cd $(AWS_DIR) && $(TF) output -raw app_url))
	@test -n "$(ALB_URL)" || { echo "no ALB (run make aws-up first)"; exit 1; }
	$(COMPOSE) run --rm -e BASE_URL=$(ALB_URL) -e HEALTH_PATH=/Home/Health -e LABEL=aws-baseline -e VUS=$(or $(VUS),20) -e DURATION=$(or $(DURATION),60s) \
	  -e P95_MAX_MS=100000 -e P99_MAX_MS=100000 -e ERR_RATE_MAX=0.01 k6 run /scripts/k6-main.js
	@python3 scripts/annotate.py $(OUT_DIR)/aws-baseline.json > $(DEMO_DIR)/BASELINE.aws.json
	@echo "wrote $(DEMO_DIR)/BASELINE.aws.json"

aws-discover: ## walk the tagged footprint -> demo/dependency-graph.{json,md,png}; then diff vs code
	@python3 ../tools/discover/discover.py --project modernization-demo --track $(TRACK) --out $(DEMO_DIR) --read-secret-values
	@python3 ../tools/discover/diff.py --graph $(DEMO_DIR)/dependency-graph.json --repo ..
