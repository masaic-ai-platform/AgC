# AgC Release Pipeline Documentation

## Overview

This repository uses a GitHub Actions workflow for automated publishing of JAR artifacts and Docker images. The pipeline is SOC-2 aligned with manual triggers, version control, security scanning, and audit logging.

## Workflow: `publish.yml`

### Trigger
- **Manual**: `workflow_dispatch` via GitHub Actions UI
- **Options**: 
  - `skip_tests`: Skip tests during build (default: false)

### What Gets Published

#### JAR Artifacts (GitHub Packages)
- `ai.masaic.agc:open-responses-core`
- `ai.masaic.agc:open-responses-rest`
- `ai.masaic.agc:open-responses-server`
- `ai.masaic.agc:agc-platform-core`
- `ai.masaic.agc:agc-platform-rest`
- `ai.masaic.agc:agc-platform-server`

#### Docker Images (Docker Hub)
- `masaicai/open-responses` (multi-arch: amd64, arm64)
- `masaicai/agc-platform-server` (multi-arch: amd64, arm64)
- `masaicai/platform-ui` (multi-arch: amd64, arm64)

## Version Management

### Version Sources
- **Kotlin/Java artifacts**: Version from `platform/build.gradle.kts`
- **UI artifact**: Version from `ui/package.json`

### Dev vs Stable Releases

#### Development Releases
**Version format**: `x.x.x-dev`, `x.x.x-alpha`, `x.x.x-beta`, `x.x.x-rc`, `x.x.x-SNAPSHOT`

**Docker tags**:
- `masaicai/open-responses:0.8.0-dev`
- `masaicai/open-responses:dev` ← always points to latest dev build
- `masaicai/agc-platform-server:0.8.0-dev`
- `masaicai/agc-platform-server:dev`
- `masaicai/platform-ui:0.8.0-dev`
- `masaicai/platform-ui:dev`

#### Stable Releases
**Version format**: `x.x.x` (semantic versioning, no suffix)

**Docker tags**:
- `masaicai/open-responses:0.8.0`
- `masaicai/open-responses:latest` ← always points to latest stable release
- `masaicai/agc-platform-server:0.8.0`
- `masaicai/agc-platform-server:latest`
- `masaicai/platform-ui:0.8.0`
- `masaicai/platform-ui:latest`

## How to Release

### Step 1: Update Version
Choose your release type:

#### For Development/Snapshot Release:
```bash
# Update platform/build.gradle.kts
version = "0.8.0-dev"

# Update ui/package.json
"version": "0.8.0-dev"
```

#### For Stable Release:
```bash
# Update platform/build.gradle.kts
version = "0.8.0"

# Update ui/package.json
"version": "0.8.0"
```

### Step 2: Commit and Push
```bash
git add platform/build.gradle.kts ui/package.json
git commit -m "chore: bump version to x.x.x"
git push origin main
```

### Step 3: Trigger Workflow
1. Go to **Actions** tab in GitHub
2. Select **Publish Release Pipeline**
3. Click **Run workflow**
4. Choose options:
   - `skip_tests`: Set to `true` if you want faster builds (not recommended for stable)
5. Click **Run workflow**

### Step 4: Monitor Progress
- Watch the workflow run in the Actions tab
- Review the summary for published artifacts
- Check security scan results

## Security & Compliance

### SOC-2 Controls

| Control | Implementation |
|---------|---------------|
| **Change Management** | Manual `workflow_dispatch` trigger only |
| **Build Provenance** | Fixed action versions, immutable builds |
| **Access Control** | GitHub secrets (DOCKERHUB_TOKEN) restricted to maintainers |
| **Integrity Verification** | Image digests logged in workflow summary |
| **Audit Evidence** | GitHub Actions logs retained permanently |
| **Security Validation** | Trivy vulnerability scanning on all images |

### Security Scanning
All Docker images are scanned with Trivy for vulnerabilities. Results are:
- Uploaded to GitHub Security tab (SARIF format)
- Available in workflow artifacts
- Non-blocking (warnings only)

## Required Secrets

Configure these in **Settings → Secrets and variables → Actions**:

| Secret | Description |
|--------|-------------|
| `DOCKERHUB_USERNAME` | Docker Hub username: `masaicai` |
| `DOCKERHUB_TOKEN` | Docker Hub access token |
| `GITHUB_TOKEN` | Auto-provided by GitHub Actions |

## Future: AWS ECR Integration

The workflow includes commented code for pushing images to AWS ECR. To enable:

1. Add AWS credentials to GitHub Secrets:
   - `AWS_ACCESS_KEY_ID`
   - `AWS_SECRET_ACCESS_KEY`

2. Uncomment the `push-ecr` job in `publish.yml`

3. Add workflow input for ECR push control

4. Ensure ECR repositories exist:
   - `open-responses`
   - `agc-platform-server`
   - `platform-ui`

## Troubleshooting

### Build Fails
- Check Java version (requires OpenJDK 21)
- Verify Gradle wrapper permissions
- Review test failures (if not skipped)

### Publishing Fails
- Verify `DOCKERHUB_TOKEN` is valid
- Check GitHub Packages permissions
- Ensure version numbers are valid

### Docker Build Fails
- Check Dockerfile paths
- Verify build context
- Review Docker Buildx setup

## Local Testing

### Test Gradle Build
```bash
cd platform
./gradlew build
```

### Test Docker Builds
```bash
# open-responses-server
cd platform
docker build -f Dockerfile -t masaicai/open-responses:test .

# agc-platform-server
docker build -f Dockerfile-platform -t masaicai/agc-platform-server:test .

# UI
cd ui
docker build -f Dockerfile -t masaicai/platform-ui:test .
```

### Test Multi-arch Builds
```bash
docker buildx build --platform linux/amd64,linux/arm64 \
  -f Dockerfile \
  -t masaicai/open-responses:test \
  .
```

## Consuming Published Artifacts

### Using JAR Artifacts
Add to your `build.gradle.kts`:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/masaic-ai-platform/AgC")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("ai.masaic.agc:agc-platform-core:0.8.0")
}
```

### Using Docker Images
```bash
# Pull stable release
docker pull masaicai/agc-platform-server:latest

# Pull specific version
docker pull masaicai/agc-platform-server:0.8.0

# Pull development version
docker pull masaicai/agc-platform-server:dev
```

## Best Practices

1. **Always update versions** before triggering workflow
2. **Use development versions** for testing and pre-release
3. **Use stable versions** for production deployments
4. **Review security scans** before considering stable release
5. **Test locally** before pushing changes
6. **Document breaking changes** in release notes
7. **Follow semantic versioning** for stable releases

## Support

For issues or questions:
- Create an issue in the repository
- Contact the maintainers
- Check GitHub Actions logs for detailed error messages

