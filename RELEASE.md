# AgC Release Process

Quick reference for releasing AgC artifacts.

## 📦 What Gets Published

- **6 JAR artifacts** → GitHub Packages (all platform modules)
- **3 Docker images** → Docker Hub (multi-arch: amd64, arm64)

## 🚀 Quick Release

### 1. Update Version Files

**For Development Release**:
```bash
# platform/build.gradle.kts
version = "0.8.0-dev"

# ui/package.json
"version": "0.8.0-dev"
```

**For Stable Release**:
```bash
# platform/build.gradle.kts
version = "0.8.0"

# ui/package.json
"version": "0.8.0"
```

### 2. Commit and Push
```bash
git add platform/build.gradle.kts ui/package.json
git commit -m "chore: bump version to x.x.x"
git push origin main
```

### 3. Trigger Workflow
1. Go to **Actions** tab in GitHub
2. Click **Publish Release Pipeline**
3. Click **Run workflow**
4. Monitor progress

## 📊 Published Artifacts

### Development (`x.x.x-dev`)
```
Docker: masaicai/agc-platform-server:dev
Docker: masaicai/open-responses:dev
Docker: masaicai/platform-ui:dev
```

### Stable (`x.x.x`)
```
Docker: masaicai/agc-platform-server:latest
Docker: masaicai/open-responses:latest
Docker: masaicai/platform-ui:latest
```

## 📚 Documentation

- **Setup**: `.github/SETUP.md` - Configure secrets and permissions
- **Workflow**: `.github/workflows/README.md` - Detailed pipeline documentation
- **Artifacts**: `.github/ARTIFACTS.md` - Complete artifact reference
- **Design**: See original design document for SOC-2 compliance details

## 🔒 Security

All images are scanned with Trivy. Review security alerts before stable releases.

## 🆘 Need Help?

Check the detailed documentation in `.github/` or create an issue.

