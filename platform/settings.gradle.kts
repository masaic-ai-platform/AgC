plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "agc-platform"

include("open-responses-core")
include("open-responses-server")
include("agc-test-regression-server")
include("agc-usecases-server")
include("agc-platform-core")
include("agc-platform-server")
include("agc-platform-rest")
include("open-responses-rest")
include("temporal_client")
