# Staging & Production Deployment Runbook (Cloud Run + MongoDB Atlas)

> **Deployment Policy:** Do **NOT** execute the live deployment commands below until explicitly approved.

## Architecture Choice: Single Unified Container
GymKhata deploys as **one stateless container** (defined in `/Dockerfile`) where the Spring Boot 3.5.16 fat JAR serves both `/api/v1/**` and the compiled React 19 PWA (`classpath:/static/`, routed by `SpaWebMvcConfigurer.java`) on port `8080`.
- **Same Origin, Zero CORS Overhead:** Browser requests to `/api/v1/**` originate from the same Cloud Run domain as the PWA shell, eliminating preflight `OPTIONS` latency and cross-origin cookie issues (`gk_device_token`).
- **Atomic Releases:** Frontend and backend versions deploy and roll back together in a single Cloud Run revision.

---

## Step 1: Provision MongoDB Atlas (`asia-south1` Mumbai)

```bash
# 1. Authenticate Atlas CLI and create an M0/M10 cluster in GCP Mumbai (asia-south1)
atlas auth login
atlas clusters create gymkhata-staging \
  --provider GCP \
  --region CENTRAL_INDIA \
  --tier M10 \
  --mdbVersion 7.0 \
  --projectId "<ATLAS_PROJECT_ID>"

# 2. Create a least-privilege database user (readWrite on gymkhata_staging)
atlas dbusers create \
  --username gymkhata_app \
  --password "<STRONG_DB_PASSWORD>" \
  --role readWrite@gymkhata_staging \
  --projectId "<ATLAS_PROJECT_ID>"

# 3. Retrieve the SRV connection string
atlas clusters connectionStrings describe gymkhata-staging --projectId "<ATLAS_PROJECT_ID>"
# Format: mongodb+srv://gymkhata_app:<STRONG_DB_PASSWORD>@gymkhata-staging.xxxxx.mongodb.net/gymkhata_staging?retryWrites=true&w=majority
```

---

## Step 2: Store Secrets in Google Cloud Secret Manager

`ProdConfigValidator` refuses to boot under `SPRING_PROFILES_ACTIVE=prod` if any of these four secrets are missing, weak, or left at development defaults:

```bash
export PROJECT_ID="your-gcp-project-id"
export REGION="asia-south1"

gcloud config set project "${PROJECT_ID}"
gcloud services enable run.googleapis.com secretmanager.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com

# 1. JWT_SECRET (>= 32 bytes cryptographic random hex)
openssl rand -hex 32 | tr -d '\n' | gcloud secrets create JWT_SECRET --data-file=-

# 2. SUPER_ADMIN_PASSWORD (strong password, never ChangeMe@123 or DemoAdmin@2026)
printf '%s' 'ChangeThisToStrongProdPass#2026!' | gcloud secrets create SUPER_ADMIN_PASSWORD --data-file=-

# 3. MONGODB_URI (MongoDB Atlas SRV URI from Step 1)
printf '%s' 'mongodb+srv://gymkhata_app:<STRONG_DB_PASSWORD>@gymkhata-staging.xxxxx.mongodb.net/gymkhata_staging?retryWrites=true&w=majority' \
  | gcloud secrets create MONGODB_URI --data-file=-

# 4. CORS_ALLOWED_ORIGINS (exact HTTPS staging domain, no * or localhost)
printf '%s' 'https://gymkhata-staging-xxxxx-el.a.run.app' \
  | gcloud secrets create CORS_ALLOWED_ORIGINS --data-file=-
```

Grant the Cloud Run runtime service account access to read the secrets:
```bash
export RUNTIME_SA="gymkhata-runner@${PROJECT_ID}.iam.gserviceaccount.com"
gcloud iam service-accounts create gymkhata-runner --display-name="GymKhata Cloud Run Service Account"

for SECRET in JWT_SECRET SUPER_ADMIN_PASSWORD MONGODB_URI CORS_ALLOWED_ORIGINS; do
  gcloud secrets add-iam-policy-binding "${SECRET}" \
    --member="serviceAccount:${RUNTIME_SA}" \
    --role="roles/secretmanager.secretAccessor"
done
```

---

## Step 3: Build & Deploy the Single Container to Cloud Run (`asia-south1`)

```bash
gcloud run deploy gymkhata-staging \
  --source=. \
  --region="${REGION}" \
  --platform=managed \
  --service-account="${RUNTIME_SA}" \
  --allow-unauthenticated \
  --port=8080 \
  --cpu=1 \
  --memory=512Mi \
  --min-instances=0 \
  --max-instances=3 \
  --set-env-vars="SPRING_PROFILES_ACTIVE=prod,SUPER_ADMIN_PHONE=+919000000001,RATE_LIMIT_ENABLED=true,RATE_LIMIT_RPM=30,SWAGGER_ENABLED=false" \
  --set-secrets="JWT_SECRET=JWT_SECRET:latest,SUPER_ADMIN_PASSWORD=SUPER_ADMIN_PASSWORD:latest,MONGODB_URI=MONGODB_URI:latest,CORS_ALLOWED_ORIGINS=CORS_ALLOWED_ORIGINS:latest"
```

---

## Step 4: Post-Deploy Smoke Verification

```bash
export STAGING_URL=$(gcloud run services describe gymkhata-staging --region="${REGION}" --format='value(status.url)')

# 1. Verify health endpoint and security headers
curl -i "${STAGING_URL}/actuator/health"
# Expect: HTTP/2 200, X-Content-Type-Options: nosniff, X-Frame-Options: DENY, {"status":"UP"}

# 2. Verify Swagger is locked down in prod
curl -i "${STAGING_URL}/swagger-ui.html"
# Expect: HTTP/2 401 or 404

# 3. Verify PWA shell is served from the same origin
curl -s "${STAGING_URL}/" | grep "<title>"
```
