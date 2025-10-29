# GitHub Actions Setup Guide

This guide will help you configure the required secrets and permissions for the release pipeline.

## Prerequisites

1. Admin access to the GitHub repository
2. Docker Hub account credentials
3. (Future) AWS credentials for ECR

## Step 1: Configure GitHub Secrets

Navigate to your repository: **Settings → Secrets and variables → Actions → New repository secret**

### Required Secrets

#### `DOCKERHUB_USERNAME`
- **Value**: `masaicai`
- **Purpose**: Docker Hub authentication for image pushes

#### `DOCKERHUB_TOKEN`
- **Value**: Your Docker Hub access token
- **How to get it**:
  1. Login to [Docker Hub](https://hub.docker.com/)
  2. Go to **Account Settings → Security → New Access Token**
  3. Name: `GitHub Actions - AgC`
  4. Permissions: **Read, Write, Delete**
  5. Copy the generated token
  6. Add as GitHub secret

#### `GITHUB_TOKEN`
- **Note**: Automatically provided by GitHub Actions
- **No action needed** - this is built-in

## Step 2: Configure GitHub Packages Permissions

Ensure GitHub Packages can write artifacts:

1. Go to **Settings → Actions → General**
2. Scroll to **Workflow permissions**
3. Select: **Read and write permissions**
4. Check: **Allow GitHub Actions to create and approve pull requests**
5. Click **Save**

## Step 3: Verify Docker Hub Repositories

Ensure these repositories exist on Docker Hub under `masaicai`:

- [ ] `masaicai/open-responses`
- [ ] `masaicai/agc-platform-server`
- [ ] `masaicai/platform-ui`

If they don't exist, they will be created automatically on first push (if you have permissions).

## Step 4: Test the Workflow

1. Update version in `platform/build.gradle.kts` to `0.8.0-dev` (or current dev version)
2. Update version in `ui/package.json` to `0.8.0-dev`
3. Commit and push changes
4. Go to **Actions** tab
5. Select **Publish Release Pipeline**
6. Click **Run workflow**
7. Choose branch: `main`
8. Click **Run workflow**

## Step 5: Verify Published Artifacts

### GitHub Packages
1. Go to repository main page
2. Click **Packages** on the right sidebar
3. Verify JAR artifacts are listed

### Docker Hub
1. Visit [hub.docker.com/u/masaicai](https://hub.docker.com/u/masaicai)
2. Verify images are pushed with correct tags

## Future: AWS ECR Setup (Optional)

When you're ready to push images to AWS ECR:

### Step 1: Create ECR Repositories

```bash
aws ecr create-repository --repository-name open-responses --region ap-south-1
aws ecr create-repository --repository-name agc-platform-server --region ap-south-1
aws ecr create-repository --repository-name platform-ui --region ap-south-1
```

### Step 2: Add AWS Secrets

#### `AWS_ACCESS_KEY_ID`
- IAM user access key with ECR permissions

#### `AWS_SECRET_ACCESS_KEY`
- IAM user secret key

### Step 3: Update Workflow

Uncomment the `push-ecr` job in `.github/workflows/publish.yml`

## Security Best Practices

1. **Rotate tokens regularly**: Refresh Docker Hub and AWS tokens every 90 days
2. **Use least privilege**: IAM roles should have minimum required permissions
3. **Monitor access logs**: Review who triggers workflows
4. **Review Trivy scans**: Check security vulnerabilities before stable releases
5. **Keep actions updated**: Periodically update action versions in workflow

## Troubleshooting

### "Permission denied" on GitHub Packages
- Check **Workflow permissions** are set to **Read and write**
- Verify `GITHUB_TOKEN` has packages write scope

### "Authentication failed" on Docker Hub
- Verify `DOCKERHUB_USERNAME` is exactly `masaicai`
- Regenerate Docker Hub token if expired
- Check token has write permissions

### "Repository does not exist" on Docker Hub
- Create repositories manually on Docker Hub
- Or ensure auto-create is enabled for your account

### Gradle publish fails
- Check Java version is 21
- Verify Gradle wrapper is executable (`chmod +x gradlew`)
- Review build logs for compilation errors

## Monitoring

### Workflow Runs
- **Actions tab**: View all workflow runs and their status
- **Summary**: Each run shows published artifacts and image digests
- **Logs**: Detailed logs available for 90 days (or per your org settings)

### Security Alerts
- **Security tab → Code scanning alerts**: Trivy scan results
- Review and address vulnerabilities before stable releases

## Support

For issues with setup:
1. Check workflow logs in Actions tab
2. Review [GitHub Actions documentation](https://docs.github.com/en/actions)
3. Consult [Docker Hub documentation](https://docs.docker.com/docker-hub/)
4. Create an issue in this repository

## Checklist

- [ ] Added `DOCKERHUB_USERNAME` secret
- [ ] Added `DOCKERHUB_TOKEN` secret
- [ ] Configured workflow permissions (read/write)
- [ ] Verified Docker Hub repositories exist
- [ ] Tested workflow with dev version
- [ ] Verified JAR artifacts in GitHub Packages
- [ ] Verified Docker images on Docker Hub
- [ ] Reviewed security scan results
- [ ] (Optional) Configured AWS ECR secrets
- [ ] (Optional) Enabled ECR push job

Once all items are checked, your release pipeline is ready! 🚀

