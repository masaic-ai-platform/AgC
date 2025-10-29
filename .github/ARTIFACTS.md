# AgC Release Artifacts Reference

## Artifact Matrix

### JAR Artifacts (GitHub Packages)

| Artifact | Group ID | Registry | Description |
|----------|----------|----------|-------------|
| `open-responses-core` | `ai.masaic.agc` | GitHub Packages | Legacy core library with response handling |
| `open-responses-rest` | `ai.masaic.agc` | GitHub Packages | REST endpoints for legacy system |
| `open-responses-server` | `ai.masaic.agc` | GitHub Packages | Spring Boot runtime for legacy system |
| `agc-platform-core` | `ai.masaic.agc` | GitHub Packages | AgC core logic and orchestration |
| `agc-platform-rest` | `ai.masaic.agc` | GitHub Packages | REST API module for AgC |
| `agc-platform-server` | `ai.masaic.agc` | GitHub Packages | AgC platform runtime service |

**Version source**: `platform/build.gradle.kts`

### Docker Images (Docker Hub)

| Image | Platforms | Registry | Description |
|-------|-----------|----------|-------------|
| `masaicai/open-responses` | linux/amd64, linux/arm64 | Docker Hub | Legacy open-responses runtime |
| `masaicai/agc-platform-server` | linux/amd64, linux/arm64 | Docker Hub | AgC platform server |
| `masaicai/platform-ui` | linux/amd64, linux/arm64 | Docker Hub | React-based frontend |

**Version sources**:
- `masaicai/open-responses`: `platform/build.gradle.kts`
- `masaicai/agc-platform-server`: `platform/build.gradle.kts`
- `masaicai/platform-ui`: `ui/package.json`

---

## Versioning Strategy

### Development Releases

**Version pattern**: `x.x.x-dev`, `x.x.x-alpha`, `x.x.x-beta`, `x.x.x-rc`, `x.x.x-SNAPSHOT`

**Example**: `0.8.0-dev`

#### JAR Artifacts
```
ai.masaic.agc:open-responses-core:0.8.0-dev
ai.masaic.agc:open-responses-rest:0.8.0-dev
ai.masaic.agc:open-responses-server:0.8.0-dev
ai.masaic.agc:agc-platform-core:0.8.0-dev
ai.masaic.agc:agc-platform-rest:0.8.0-dev
ai.masaic.agc:agc-platform-server:0.8.0-dev
```

#### Docker Images
```
masaicai/open-responses:0.8.0-dev
masaicai/open-responses:dev
masaicai/agc-platform-server:0.8.0-dev
masaicai/agc-platform-server:dev
masaicai/platform-ui:0.8.0-dev
masaicai/platform-ui:dev
```

**Purpose**: 
- Testing and development
- CI/CD integration testing
- Feature branches
- Pre-release validation

---

### Stable Releases

**Version pattern**: `x.x.x` (semantic versioning)

**Example**: `1.0.0`

#### JAR Artifacts
```
ai.masaic.agc:open-responses-core:1.0.0
ai.masaic.agc:open-responses-rest:1.0.0
ai.masaic.agc:open-responses-server:1.0.0
ai.masaic.agc:agc-platform-core:1.0.0
ai.masaic.agc:agc-platform-rest:1.0.0
ai.masaic.agc:agc-platform-server:1.0.0
```

#### Docker Images
```
masaicai/open-responses:1.0.0
masaicai/open-responses:latest
masaicai/agc-platform-server:1.0.0
masaicai/agc-platform-server:latest
masaicai/platform-ui:1.0.0
masaicai/platform-ui:latest
```

**Purpose**:
- Production deployments
- Public releases
- Long-term support versions
- External integrations

---

## Semantic Versioning

Follow [Semantic Versioning 2.0.0](https://semver.org/):

### Format: `MAJOR.MINOR.PATCH`

- **MAJOR**: Incompatible API changes
- **MINOR**: Backward-compatible functionality additions
- **PATCH**: Backward-compatible bug fixes

### Examples

| Version | Type | Description |
|---------|------|-------------|
| `1.0.0-dev` | Development | Pre-release development build |
| `1.0.0-beta.1` | Beta | Beta release candidate |
| `1.0.0-rc.1` | Release Candidate | Final testing before stable |
| `1.0.0` | Stable | Initial stable release |
| `1.0.1` | Patch | Bug fixes only |
| `1.1.0` | Minor | New features, backward compatible |
| `2.0.0` | Major | Breaking changes |

---

## Consuming Artifacts

### Maven/Gradle (JVM Projects)

#### Gradle Kotlin DSL (`build.gradle.kts`)

```kotlin
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/masaic-ai-platform/AgC")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    // Use stable version for production
    implementation("ai.masaic.agc:agc-platform-core:1.0.0")
    
    // Or use dev version for testing
    // implementation("ai.masaic.agc:agc-platform-core:0.8.0-dev")
}
```

#### Maven (`pom.xml`)

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/masaic-ai-platform/AgC</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>ai.masaic.agc</groupId>
        <artifactId>agc-platform-core</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

### Docker

#### Docker Compose (`docker-compose.yml`)

```yaml
version: '3.8'

services:
  # Use stable version for production
  agc-platform:
    image: masaicai/agc-platform-server:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=production

  # Or use specific version
  agc-platform-versioned:
    image: masaicai/agc-platform-server:1.0.0
    ports:
      - "8081:8080"

  # Or use dev version for testing
  agc-platform-dev:
    image: masaicai/agc-platform-server:dev
    ports:
      - "8082:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=development

  ui:
    image: masaicai/platform-ui:latest
    ports:
      - "3000:80"
```

#### Kubernetes (`deployment.yaml`)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: agc-platform-server
spec:
  replicas: 3
  selector:
    matchLabels:
      app: agc-platform
  template:
    metadata:
      labels:
        app: agc-platform
    spec:
      containers:
      - name: agc-platform
        # Pin to specific version for production
        image: masaicai/agc-platform-server:1.0.0
        # Never use :latest in production - use specific version!
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
```

---

## Dockerfile References

### open-responses-server
- **Dockerfile**: `platform/Dockerfile`
- **Build context**: `platform/`
- **Base**: OpenJDK 21
- **Port**: 8080

### agc-platform-server
- **Dockerfile**: `platform/Dockerfile-platform`
- **Build context**: `platform/`
- **Base**: OpenJDK 21
- **Port**: 8080

### platform-ui
- **Dockerfile**: `ui/Dockerfile`
- **Build context**: `ui/`
- **Base**: Node.js + Nginx
- **Port**: 80

---

## Migration Path

### From Development to Stable

1. **Development phase**: Use `x.x.x-dev` versions
   ```bash
   # In platform/build.gradle.kts
   version = "1.0.0-dev"
   
   # In ui/package.json
   "version": "1.0.0-dev"
   ```

2. **Beta testing**: Use `x.x.x-beta.N` versions
   ```bash
   version = "1.0.0-beta.1"
   "version": "1.0.0-beta.1"
   ```

3. **Release candidate**: Use `x.x.x-rc.N` versions
   ```bash
   version = "1.0.0-rc.1"
   "version": "1.0.0-rc.1"
   ```

4. **Stable release**: Use `x.x.x` versions
   ```bash
   version = "1.0.0"
   "version": "1.0.0"
   ```

5. **Trigger release workflow** after each version bump

---

## Best Practices

### For Developers
- 🔧 Use `:dev` tag for local development and testing
- 📦 Always specify exact versions in production
- 🔄 Pull latest dev images regularly: `docker pull masaicai/agc-platform-server:dev`
- 📝 Document version dependencies in your project

### For Release Managers
- ✅ Test thoroughly with `-dev`, `-beta`, `-rc` before stable
- 📋 Review Trivy security scans before stable releases
- 📖 Document breaking changes in release notes
- 🏷️ Tag Git repository when releasing stable versions
- 🔐 Never skip testing phases for production releases

### For Consumers
- 🎯 Pin to specific versions in production (`1.0.0`, not `latest`)
- 🔄 Regularly update to latest stable for security fixes
- 📦 Use version ranges carefully in development
- 🧪 Test with dev versions before adopting new stable releases

---

## Support

For questions about artifacts:
- Check release notes for version history
- Review GitHub Packages for available versions
- Visit Docker Hub for image tags and layers
- Create an issue for missing or broken artifacts

