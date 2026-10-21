plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.cyberrick.phoneinspector"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.cyberrick.phoneinspector"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        warningsAsErrors = true
        abortOnError = true
        // Versions are pinned on purpose (CLAUDE.md). Upgrade notices are not defects.
        disable += listOf("AndroidGradlePluginVersion", "GradleDependency")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}

// PROJECT.md: no analytics, ads, Play Services or third party networking may enter the app, even transitively.
val dependencyGroupAllowlist =
    listOf(
        Regex("""androidx\..*"""),
        Regex("""org\.jetbrains(\..*)?"""),
        Regex("""com\.google\.guava"""),
        Regex("""com\.google\.errorprone"""),
        Regex("""com\.google\.code\.findbugs"""),
        Regex("""org\.checkerframework"""),
        Regex("""org\.jspecify"""),
    )

tasks.register("verifyDependencyAllowlist") {
    group = "verification"
    description = "Fails if any release runtime dependency group is outside the PROJECT.md allowlist."
    val root = configurations.named("releaseRuntimeClasspath").flatMap { it.incoming.resolutionResult.rootComponent }
    val allowlist = dependencyGroupAllowlist
    doLast {
        val seen = linkedSetOf<String>()
        val offenders = sortedSetOf<String>()

        fun walk(component: org.gradle.api.artifacts.result.ResolvedComponentResult) {
            val id = component.id.displayName
            if (!seen.add(id)) return
            val isExternal = component.id is org.gradle.api.artifacts.component.ModuleComponentIdentifier
            component.moduleVersion?.takeIf { isExternal }?.let { mv ->
                if (allowlist.none { it.matches(mv.group) }) {
                    offenders.add("${mv.group}:${mv.name}:${mv.version}")
                }
            }
            component.dependencies
                .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                .forEach { walk(it.selected) }
        }
        walk(root.get())
        if (offenders.isNotEmpty()) {
            throw GradleException("Dependencies outside the allowlist:\n" + offenders.joinToString("\n"))
        }
        logger.lifecycle("Dependency allowlist OK (${seen.size} components)")
    }
}
tasks.named("check") { dependsOn("verifyDependencyAllowlist") }
