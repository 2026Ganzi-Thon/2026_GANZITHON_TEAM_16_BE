# MOWOO Elastic Beanstalk 배포

## 실제 배포 확인 (2026-11-15)

- 리전: 시드니 `ap-southeast-2`, 프로젝트: GANZITHON.
- 환경: `mowoo-prod`, 플랫폼: Corretto 21 / AL2023 4.14.3, 단일 EC2 `t3.small`.
- [HTTPS 상태 확인](https://15.135.13.89/health), [HTTPS Swagger](https://15.135.13.89/swagger-ui/index.html).
- Beanstalk Ready / Green, CloudFormation 스택 생성 완료.
- 실제 `/health`·OpenAPI 200, 비로그인 내 정보·캐릭터 조회 401, 빈 카카오 인가 코드 400.
- localhost 프론트 출처 허용, 다른 출처 CORS 403 확인.
- RDS 비공개·암호화·백업 1일 확인. 앱 계정 생성 및 인증서를 검증하는 TLS 접속 성공.
- DB 초기화용 임시 EC2와 IAM 역할/프로필은 작업 후 스택과 함께 삭제 완료.
- 외부 HTTPS는 고정 공인 IP의 무료 Let’s Encrypt 인증서로 구성한다. 프론트/API 기본 주소는 **https://15.135.13.89**를 사용한다. 기존 Beanstalk 도메인에는 이 IP 인증서가 적용되지 않는다.
- 로컬에도 Upstage 키가 없어 AI 호출은 비활성화 상태다. 첨부 저장 S3도 별도 연동 전이다.
- GitHub 테스트·빌드·artifact 업로드는 통과했다. OIDC 공급자 관리가 계정 SCP로 거부되어 AWS 자동 배포는 미완료이며 `EB_DEPLOY_ENABLED=false`로 유지한다. 장기 액세스 키로 우회하지 않는다.

### 비용 기준

AWS Price List API에서 확인한 시드니 온디맨드 단가는 EC2 Linux t3.small **0.0264 USD/시간**, RDS MySQL Single-AZ db.t4g.micro **0.025 USD/시간**이다. 730시간 기준 인스턴스 합계는 약 **37.5 USD/월**이며 디스크·공인 IPv4·로그·Secrets Manager·전송량·CPU 추가 크레딧·세금은 제외한다. 무료 크레딧 적용 후 실청구액은 별도로 계정에서 확인한다. ALB와 NAT Gateway는 생성하지 않았다.

## 구성과 현재 범위

시드니 리전(`ap-southeast-2`)에 다음 스택을 순서대로 배포한다.

1. `infra/network.yaml`: VPC, public subnet 2개, private DB subnet 2개, Internet Gateway, 라우팅, 앱·DB 보안 그룹 2개, 비공개 RDS MySQL.
2. `infra/github-oidc.yaml`: 비공개 배포 ZIP 저장 버킷, GitHub OIDC 공급자 또는 기존 공급자 재사용, develop 전용 배포 역할.
3. `infra/environment.yaml`: Corretto 21 / AL2023 Beanstalk 단일 인스턴스, EC2 1대, 인스턴스·서비스 역할, CloudWatch 로그.

EC2 nginx:80 → Spring:5000 → RDS:3306 순서다. ALB는 생성하지 않는다. EC2에는 public IP를 부여해 외부 OAuth·AI API와 AWS API에 접근한다. EC2 웹 포트 80·443만 외부에서 허용하고 SSH·Java 포트 5000·DB 포트 3306은 열지 않는다. RDS에는 인터넷 경로와 public IP가 없고 앱 보안 그룹만 접근한다. NAT Gateway는 만들지 않는다.

서버는 `t3.small`, DB는 `db.t4g.micro`와 gp3 20 GiB부터 시작한다. EC2·RDS·스토리지·public IPv4·로그·Secrets Manager는 사용량에 따라 과금되며, 크레딧 적용과 잔액은 계정에서 확인한다. 현재 EC2/RDS 모두 1대이고 Multi-AZ 고가용성 구성은 아니다. 배포 정책은 AllAtOnce여서 짧은 중단이 있을 수 있다.

이 브랜치는 최신 develop을 기준으로 한다. 아직 병합되지 않은 소셜 로그인 PR #18과 기록 관리 후속 PR #19는 포함하지 않는다. GitHub 자동 배포는 기본적으로 비활성화되어 있고 아래 설정을 완료한 뒤 켠다.

## 1. 계정 확인

브라우저에서 로그인한 것과 로컬 AWS CLI 로그인은 별개다.

```powershell
aws login --profile mowoo
aws sts get-caller-identity --profile mowoo --output json
```

**초대받은 GANZITHON 프로젝트의 계정 ID인지 먼저 확인한다.** 이전 Contextory 계정이나 본인 AWS 계정을 사용하지 않는다. 이후 모든 관리 명령에는 `--profile mowoo --region ap-southeast-2`를 붙인다. 아래 예시의 계정 ID·서브넷·ARN·버킷은 실제 출력으로 채워야 한다.

## 2. 네트워크와 DB

계정에서 사용할 수 있는 AZ 두 개를 확인한다. 새 AWS 경험의 프로젝트 리전이 시드니인지도 확인한다.

```powershell
aws ec2 describe-availability-zones --profile mowoo --region ap-southeast-2 --query "AvailabilityZones[?State=='available'].ZoneName"
aws cloudformation validate-template --template-body file://infra/network.yaml --profile mowoo --region ap-southeast-2
aws cloudformation deploy --stack-name mowoo-network --template-file infra/network.yaml --parameter-overrides AvailabilityZones=ap-southeast-2a,ap-southeast-2b --profile mowoo --region ap-southeast-2
aws cloudformation describe-stacks --stack-name mowoo-network --query "Stacks[0].Outputs" --profile mowoo --region ap-southeast-2
```

기존 VPC와 연결할 계획이면 CIDR `10.42.0.0/16`이 겹치지 않는지 확인한다. DB 관리자 암호는 RDS가 Secrets Manager에 생성하며 템플릿에 암호를 넣지 않는다. 삭제 보호가 켜져 있고 스택에서 제거할 때 스냅샷을 남긴다. DB 스택 삭제와 암호 변경은 별도 운영 작업이다.

## 3. 배포 버킷과 GitHub OIDC 제한

```powershell
# 현재 계정에서는 아래 조회도 SCP로 거부됨. 제한 해제 후 사용
aws iam list-open-id-connect-providers --profile mowoo
```

**현재 GANZITHON 계정은 SCP에서 IAM OIDC 공급자 관리를 거부한다. 이 제한을 우회하지 않고 `EnableOidc=false`로 배포 버킷만 생성한다. GitHub CI는 가능하지만 AWS 자동 배포는 아직 활성화하지 않는다.** 계정 소유자가 정식으로 제한을 해제한 뒤 `EnableOidc=true`를 설정해야 한다.

이 계정에 `token.actions.githubusercontent.com` 공급자가 이미 있으면 `ExistingOidcProviderArn`에 그 ARN을 전달한다. 정식으로 OIDC 관리가 허용된 계정에서만 새로 생성한다.

```powershell
aws cloudformation deploy --stack-name mowoo-github --template-file infra/github-oidc.yaml --parameter-overrides EnableOidc=false --capabilities CAPABILITY_IAM --profile mowoo --region ap-southeast-2
aws cloudformation describe-stacks --stack-name mowoo-github --query "Stacks[0].Outputs" --profile mowoo --region ap-southeast-2
```

배포 역할은 이 저장소의 `refs/heads/develop` OIDC subject만 신뢰한다. PR과 다른 브랜치에서는 역할을 사용할 수 없다. 저장소 OIDC subject 형식을 조직에서 커스터마이즈했다면 실제 subject에 맞춰 신뢰 정책을 조정한다. 이 역할에는 인프라 생성·삭제나 앱 비밀 조회 권한을 부여하지 않는다.

## 4. 운영 비밀과 DB 전용 사용자

운영 비밀은 AWS Secrets Manager에 별도로 만든다. 각 SecretString은 JSON 객체가 아닌 **값 하나**를 저장한다.

| 비밀 | Beanstalk 템플릿 파라미터 |
| --- | --- |
| mowoo_app DB 비밀번호 | DatabasePasswordSecretArn |
| 32바이트 이상 무작위 JWT 비밀키 | JwtSecretArn |
| 카카오 클라이언트 시크릿 | KakaoSecretArn |

실제 비밀은 Git, CloudFormation 파라미터, ZIP, Actions 로그에 넣지 않는다. 초기 AWS CLI 세션에서도 비밀 값을 화면에 출력하지 않는다. 로컬 개발 .env를 그대로 배포하지 않는다.

RDS 관리자 계정 `mowoo_admin`은 초기 DB 사용자 설정에만 사용한다. `infra/database-bootstrap.yaml`로 같은 VPC 안에 임시 관리 호스트를 만들어, 앱 전용 계정 생성과 TLS 인증서 검증을 수행한다. 성공 신호를 확인한 뒤 임시 스택을 삭제한다. 아래는 수행하는 SQL의 예시다. 이 작업에는 Secrets Manager의 관리자 비밀번호가 필요하며, 앱 인스턴스 역할에는 관리자 비밀 접근 권한을 주지 않는다. 관리 호스트를 임시 생성했다면 작업 후 종료한다.

```sql
-- 비밀번호 자리에는 위 DB 앱 비밀과 같은 값을 사용한다.
CREATE USER 'mowoo_app'@'%' IDENTIFIED BY '<운영 앱 DB 비밀번호>' REQUIRE SSL;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP
ON mowoo.* TO 'mowoo_app'@'%';
```

DDL 권한은 현재 `ddl-auto: update`가 필요로 하므로 포함한다. 운영 스키마 변경 방식을 바꾸면 DDL 권한을 줄인다.

`DatabaseUrl`은 `jdbc:mysql://<RDS endpoint>:3306/mowoo?sslMode=VERIFY_IDENTITY&connectionTimeZone=UTC`로 설정한다. predeploy hook이 AWS 공식 시드니 RDS CA를 다운로드해 기존 JVM 신뢰 저장소에 추가하고, Procfile이 이 저장소를 사용한다. 외부 HTTPS 서비스용 기존 신뢰 루트도 유지한다.

Secrets Manager의 사용자 관리 KMS 키로 암호화했다면 인스턴스 역할에 해당 키의 `kms:Decrypt`도 추가해야 한다. 비밀 값 변경만으로 실행 중 프로세스가 갱신되지는 않으므로 Beanstalk 환경 비밀 새로고침/재배포를 수행한다.

## 5. 첫 배포

먼저 테스트 전용 MySQL에서 테스트를 실행하고 실행 JAR를 만든다. 운영 DB에서 테스트하지 않는다.

```powershell
.\gradlew.bat test bootJar
python scripts/package-beanstalk.py
```

`build/beanstalk.zip`에는 `application.jar`, `Procfile`, `.platform`, `scripts/configure-https.sh`만 들어간다. .env와 소스, 테스트 결과는 포함되지 않는다. ZIP을 `ReleaseBucket` 출력 버킷의 `releases/<고유 버전>.zip`으로 업로드한다.

```powershell
aws elasticbeanstalk list-platform-versions --profile mowoo --region ap-southeast-2
```

사용 가능한 최신 Ready 플랫폼 ARN을 선택한다. `infra/environment.yaml`의 모든 필수 파라미터를 실제 네트워크 출력, 비밀 ARN, 카카오 설정, 업로드한 번들 정보로 채운다. 파라미터에는 비밀 값 자체가 들어가지 않는다. CloudFormation으로 `mowoo-environment` 스택을 만들고 IAM 생성을 허용한다. CLI에서는 `create-stack --parameters file://<로컬 파라미터 JSON> --capabilities CAPABILITY_IAM`을 사용할 수 있다.

### IP 주소 HTTPS와 자동 갱신

`EnableIpHttps=true`가 기본값이다. Beanstalk 앱 배포와 환경 설정 변경의 postdeploy hook이 `scripts/configure-https.sh`를 실행한다. 도메인 구매·ALB·CloudFront 추가 없이 기존 EIP에서 nginx가 TLS를 종료하고, localhost의 Java 5000 포트로 전달한다.

- Certbot 5.10.0과 Python 3.11 전용 가상 환경을 사용한다.
- IMDSv2로 현재 공인 IP를 읽고 HTTP-01 webroot 검증으로 shortlived IP 인증서를 발급한다.
- 인증서·개인 키는 서버의 `/etc/letsencrypt`에만 보관하며 Git·ZIP·로그로 복사하지 않는다.
- TLS 1.2/1.3과 `X-Forwarded-Proto: https`를 설정해 Swagger도 HTTPS 서버 주소를 사용한다.
- 일반 HTTP 요청은 HTTPS IP로 308 이동한다. `/health`와 `/.well-known/acme-challenge/`만 HTTP를 유지한다.
- 프론트는 처음부터 HTTPS 기본 주소를 사용해야 한다. HTTP로 보낸 요청 본문이 리디렉션으로 소급해서 암호화되는 것은 아니다.
- 인증서 유효기간은 약 6일(160시간)이다. `mowoo-certbot-renew.timer`가 부팅 후 및 하루 두 번 갱신 필요 여부를 확인하고, 실제 갱신 시 nginx를 reload한다.
- 앱 재배포와 환경 설정 변경에서도 HTTPS 구성을 다시 적용한다. 인스턴스 교체 시 새 인증서를 발급한다. EIP 자체가 바뀌면 프론트 기본 주소와 GitHub health URL도 갱신해야 한다.
- CA 계정은 연락처 이메일 없이 등록한다. 별도의 실패 이메일 알림은 구성하지 않았으며, 갱신 상태는 systemd journal에서 확인한다.

서버에서 확인할 명령:

```bash
systemctl status mowoo-certbot-renew.timer
systemctl list-timers mowoo-certbot-renew.timer
journalctl -u mowoo-certbot-renew.service
/opt/mowoo-certbot/bin/certbot renew --cert-name mowoo-ip --dry-run
```

[Let’s Encrypt IP 인증서 안내](https://letsencrypt.org/2026/03/11/shorter-certs-certbot)

DB 백업 보존은 현재 무료 플랜 제약에 맞춰 **1일**이다. 처음 7일 설정은 AWS에서 거절되었으며, 실제 데이터 보존 요구에 따라 계정 플랜과 백업 정책을 다시 검토한다.

Beanstalk 기본 환경은 AI·S3 기능을 비활성화한다. 실제 기능을 켜려면 운영 Upstage 키/설정과 첨부 저장 권한을 별도로 연결한다. 아직 그 기능을 검증하지 않았다면 배포 완료와 AI·첨부 연동 완료를 구분해 보고한다.

## 6. 자동 배포 켜기

GitHub 저장소 Settings → Secrets and variables → Actions → Variables에 다음을 등록한다.

| 변수 | 값 |
| --- | --- |
| EB_RELEASE_BUCKET | mowoo-github 스택 ReleaseBucket 출력 |
| EB_DEPLOY_ROLE_ARN | mowoo-github 스택 DeployRoleArn 출력 |
| EB_HEALTHCHECK_URL | https://15.135.13.89/health |
| EB_DEPLOY_ENABLED | 계정의 OIDC 제한 해제·배포 역할 생성·첫 환경 검증 완료 후 true |

PR은 MySQL 8.4에서 테스트만 수행한다. develop push/수동 실행은 테스트에서 만든 동일 ZIP을 다운로드해 배포한다. 배포 후 버전과 Green 상태, `/health` 응답을 확인한다. 실패 시 이전 버전으로 롤백을 시도하고 워크플로를 실패 처리한다. 환경이 Ready가 아니거나 롤백에도 실패하면 수동 복구가 필요하다.

앱 설정을 CloudFormation으로 변경할 때 `Environment.VersionLabel`은 첫 배포 버전으로 남아 있다. CI에서 배포한 최신 버전을 보존하려면 스택 변경 전에 템플릿의 버전 설정/드리프트를 확인한다. 인프라 변경과 앱 배포를 동시에 실행하지 않는다.

브랜치 보호에서 테스트 job 성공을 병합 조건으로 설정할 수 있다. develop 자동 배포 활성화는 PR을 임의로 병합해도 된다는 의미가 아니다.

## 확인할 것

- `/health` 200, 보호 API 비로그인 401
- 로그인 후 access token과 HTTPS refresh cookie, 재발급·로그아웃
- 프론트 CORS 출처와 OAuth 제공자에 등록한 redirect URI 일치
- 웹 포트 외 공개 차단, RDS 외부 접근 차단, 실제 DB 기록 저장
- 로그에 토큰·일기 내용·비밀 값이 노출되지 않는지 확인
- 실패 배포 롤백, RDS 백업/복구, 비용·크레딧 잔액 확인

## 공식 자료

- https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/vpc.html
- https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/java-se-procfile.html
- https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/AWSHowTo.secrets.env-vars.html
- https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws
