plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt)
}

detekt {
    source.setFrom(files("app/src/main/kotlin", "app/src/test/kotlin", "app/src/debug/kotlin"))
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    // Records the oversized Compose screens that predate detekt adoption. New violations
    // still fail the build; these are tracked debt, not an exemption. Decomposing
    // ProtectionLevelScreen / TriggersScreen / ConfigSheet should shrink this file.
    baseline = file("$rootDir/config/detekt/baseline.xml")
    buildUponDefaultConfig = true
    // A static-analysis finding in this codebase is a potential wipe bug, so the build
    // stops rather than filing a warning nobody reads.
    ignoreFailures = false
    parallel = true
}
