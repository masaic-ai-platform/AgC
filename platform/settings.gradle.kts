plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "openresponses"

include("open-responses-core")
include("open-responses-onnx")
include("open-responses-server")
include("agc-test-regression-server")
include("agc-usecases-server")
include("agc-usecases-server")
