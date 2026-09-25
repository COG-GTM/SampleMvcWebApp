# Real (small, destroyable) AWS footprint for the legacy SampleWebApp. Used by the Assess act:
# tools/discover walks these resources by tag and diffs them against the migrated code.
#
#   internet -> ALB(:80, sg-alb) -> target group(:80, health /Home/Health) -> EC2 (sg-app, :80 from ALB only)
#                                                                              |- app container (legacy image)
#                                                                              |- sqlserver container (:1433, host-local)
#   Route53 private zone demo.internal:  samplewebapp -> ALB,  db -> EC2 private IP
#   Secrets Manager: <project>/<track>/SAMPLEWEBAPP_DB_CONNECTION  (legacy key name)

locals {
  name          = "${var.name}-${var.track}"
  db_name       = "SampleWebAppDb"
  db_host       = "db.${var.dns_zone}"
  secret_name   = "${var.project}/${var.track}/${var.app_db_connection_env}"
  db_connection = "Server=${local.db_host},1433;Database=${local.db_name};User Id=sa;Password=${random_password.db.result};MultipleActiveResultSets=True"
  deploy_app    = var.app_image != ""
  runtime_labels = {
    "runtime.family" = var.app_runtime_family
    "runtime.image"  = var.app_runtime_image
    "app.port"       = tostring(var.app_port)
    "app.health"     = var.app_health_path
    "app.secret_key" = var.app_db_connection_env
  }
}

# ---- network (default VPC, two public subnets) ---------------------------------------------------
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "public" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
  filter {
    name   = "default-for-az"
    values = ["true"]
  }
}

locals {
  subnet_ids = slice(sort(data.aws_subnets.public.ids), 0, 2)
}

# ---- container registry ---------------------------------------------------------------------------
resource "aws_ecr_repository" "app" {
  name                 = "${var.project}/${var.name}-legacy"
  image_tag_mutability = "MUTABLE"
  force_delete         = true
  image_scanning_configuration {
    scan_on_push = false
  }
}

# ---- secrets ---------------------------------------------------------------------------------------
resource "random_password" "db" {
  length           = 20
  special          = true
  override_special = "!#%^*-_=+"
  min_upper        = 2
  min_lower        = 2
  min_numeric      = 2
  min_special      = 1
}

resource "aws_secretsmanager_secret" "db_connection" {
  name                    = local.secret_name
  description             = "DB connection string read by ${var.name} as env ${var.app_db_connection_env} (legacy Web.config key)"
  recovery_window_in_days = 0
  tags = {
    ConsumerEnv = var.app_db_connection_env
    DbHost      = local.db_host
    DbEngine    = "sqlserver-2022"
  }
}

resource "aws_secretsmanager_secret_version" "db_connection" {
  secret_id     = aws_secretsmanager_secret.db_connection.id
  secret_string = local.db_connection
}

# ---- security groups -------------------------------------------------------------------------------
resource "aws_security_group" "alb" {
  name        = "${local.name}-alb"
  description = "ALB: HTTP from anywhere"
  vpc_id      = data.aws_vpc.default.id
  tags        = { Name = "${local.name}-alb" }
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id
  description       = "public http"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "alb_all" {
  security_group_id = aws_security_group.alb.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_security_group" "app" {
  name        = "${local.name}-app"
  description = "App host: legacy app port from the ALB only"
  vpc_id      = data.aws_vpc.default.id
  tags        = { Name = "${local.name}-app" }
}

# Planted drift: only the LEGACY app port is opened from the ALB. Kestrel's :8080 has no rule.
resource "aws_vpc_security_group_ingress_rule" "app_from_alb" {
  security_group_id            = aws_security_group.app.id
  description                  = "legacy app port (IIS) from ALB"
  ip_protocol                  = "tcp"
  from_port                    = var.app_port
  to_port                      = var.app_port
  referenced_security_group_id = aws_security_group.alb.id
}

resource "aws_vpc_security_group_ingress_rule" "db_from_app" {
  security_group_id            = aws_security_group.app.id
  description                  = "sqlserver from app tier (host-local db container)"
  ip_protocol                  = "tcp"
  from_port                    = 1433
  to_port                      = 1433
  referenced_security_group_id = aws_security_group.app.id
}

resource "aws_vpc_security_group_egress_rule" "app_all" {
  security_group_id = aws_security_group.app.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

# ---- load balancer ---------------------------------------------------------------------------------
resource "aws_lb" "app" {
  name               = local.name
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = local.subnet_ids
  idle_timeout       = 60
}

# Planted drift: health check path + port are the IIS-era contract (/Home/Health on :80).
resource "aws_lb_target_group" "app" {
  name        = local.name
  port        = var.app_port
  protocol    = "HTTP"
  target_type = "instance"
  vpc_id      = data.aws_vpc.default.id

  health_check {
    path                = var.app_health_path
    port                = "traffic-port"
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
  deregistration_delay = 10
  tags                 = local.runtime_labels
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

# ---- compute (EC2 running the app + sqlserver containers) -----------------------------------------
data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

data "aws_iam_policy_document" "assume_ec2" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app" {
  name               = "${local.name}-app"
  assume_role_policy = data.aws_iam_policy_document.assume_ec2.json
}

# The reads_secret edge: this role is the only principal allowed to read the app's DB secret.
data "aws_iam_policy_document" "app" {
  statement {
    sid       = "ReadAppDbSecret"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.db_connection.arn]
  }
  statement {
    sid       = "PullAppImage"
    actions   = ["ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage", "ecr:BatchCheckLayerAvailability"]
    resources = [aws_ecr_repository.app.arn]
  }
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "app" {
  name   = "${local.name}-app"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.app.json
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "app" {
  name = "${local.name}-app"
  role = aws_iam_role.app.name
}

resource "aws_instance" "app" {
  count                       = local.deploy_app ? 1 : 0
  ami                         = data.aws_ssm_parameter.al2023.value
  instance_type               = var.instance_type
  subnet_id                   = local.subnet_ids[0]
  vpc_security_group_ids      = [aws_security_group.app.id]
  iam_instance_profile        = aws_iam_instance_profile.app.name
  associate_public_ip_address = true # egress to ECR/Secrets Manager without a NAT gateway; no ingress except via ALB SG

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
  }

  metadata_options {
    http_tokens = "required"
  }

  user_data_replace_on_change = true
  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    region        = var.region
    app_image     = var.app_image
    app_port      = var.app_port
    health_path   = var.app_health_path
    secret_id     = aws_secretsmanager_secret.db_connection.arn
    secret_env    = var.app_db_connection_env
    db_name       = local.db_name
    sa_password   = random_password.db.result
    schema_sql    = file("${path.module}/../db/01-schema.sql")
    reporting_pwd = random_password.reporting.result
  })

  tags = merge(local.runtime_labels, {
    Name          = "${local.name}-app"
    "app.image"   = var.app_image
    "app.db_host" = local.db_host
    "app.secret"  = local.secret_name
  })
}

resource "random_password" "reporting" {
  length  = 20
  special = false
}

resource "aws_lb_target_group_attachment" "app" {
  count            = local.deploy_app ? 1 : 0
  target_group_arn = aws_lb_target_group.app.arn
  target_id        = aws_instance.app[0].id
  port             = var.app_port
}

# ---- dns -------------------------------------------------------------------------------------------
resource "aws_route53_zone" "private" {
  name    = var.dns_zone
  comment = "${var.project} private service names"
  vpc {
    vpc_id = data.aws_vpc.default.id
  }
}

resource "aws_route53_record" "app" {
  zone_id = aws_route53_zone.private.zone_id
  name    = "${var.name}.${var.dns_zone}"
  type    = "A"
  alias {
    name                   = aws_lb.app.dns_name
    zone_id                = aws_lb.app.zone_id
    evaluate_target_health = true
  }
}

# Planted drift: only the legacy service name exists; nothing for the renamed/new modern service.
resource "aws_route53_record" "db" {
  count   = local.deploy_app ? 1 : 0
  zone_id = aws_route53_zone.private.zone_id
  name    = local.db_host
  type    = "A"
  ttl     = 60
  records = [aws_instance.app[0].private_ip]
}
