import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

val versionProps =
    Properties().apply {
        val propsFile = rootProject.file("version.properties")
        if (propsFile.isFile) {
            propsFile.inputStream().use(::load)
        }
    }

val resolvedVersionName: String =
    ((project.findProperty("VERSION_NAME") as? String) ?: versionProps.getProperty("VERSION_NAME") ?: "2.0.0")
        .trim()
        .removePrefix("v")

val semverRegex = Regex("""^(\d+)\.(\d+)\.(\d+)$""")
val semverMatch =
    semverRegex.matchEntire(resolvedVersionName)
        ?: error("VERSION_NAME '$resolvedVersionName' must match SemVer MAJOR.MINOR.PATCH (e.g. 2.0.0)")

val derivedVersionCode: Int =
    semverMatch.groupValues[1].toInt() * 10_000 +
        semverMatch.groupValues[2].toInt() * 100 +
        semverMatch.groupValues[3].toInt()

val resolvedVersionCode: Int =
    (project.findProperty("VERSION_CODE") as? String)?.toIntOrNull() ?: derivedVersionCode

android {
    namespace = "com.warden.app"
    compileSdk = 34

    lint {
        lintConfig = file("lint.xml")
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
    }

    defaultConfig {
        applicationId = "com.warden.app"
        minSdk = 26
        targetSdk = 34

        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en")
    }

    splits {
        abi {
            isEnable = false
        }
    }

    signingConfigs {
        create("release") {
            storeFile = System.getenv("KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (System.getenv("KEYSTORE_PATH") != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            applicationVariants.all {
                val variant = this
                variant.outputs
                    .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
                    .forEach { output ->
                        val outputFileName = "Warden-${variant.name}.apk"
                        output.outputFileName = outputFileName
                    }
            }
        }
    }
    packaging {
        resources {
            excludes +=
                setOf(
                    "META-INF/LICENSE*",
                    "META-INF/**/LICENSE*",
                    "META-INF/NOTICE*",
                    "META-INF/AL2.0",
                    "META-INF/LGPL2.1",
                    "DebugProbesKt.bin",
                    "kotlin/*.kotlin_builtins",
                    "kotlin/**/*.kotlin_builtins",
                )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    parallel = true
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
    filter {
        exclude("**/generated/**")
    }
}

tasks.register("generateReleaseSbom") {
    group = "verification"
    description = "Generates a deterministic CycloneDX 1.5 JSON SBOM for releaseRuntimeClasspath."
    val outputFile = layout.buildDirectory.file("outputs/sbom/Warden-release.cdx.json")
    outputs.file(outputFile)

    doLast {
        val components =
            configurations.getByName("releaseRuntimeClasspath").incoming.resolutionResult.allComponents
                .mapNotNull { result: ResolvedComponentResult ->
                    val id = result.id as? ModuleComponentIdentifier ?: return@mapNotNull null
                    Triple(id.group, id.module, id.version)
                }
                .distinct()
                .sortedWith(compareBy({ it.first }, { it.second }, { it.third }))

        fun esc(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"bomFormat\": \"CycloneDX\",")
        sb.appendLine("  \"specVersion\": \"1.5\",")
        sb.appendLine("  \"version\": 1,")
        sb.appendLine("  \"metadata\": {")
        sb.appendLine("    \"component\": {")
        sb.appendLine("      \"type\": \"application\",")
        sb.appendLine("      \"group\": \"com.warden.app\",")
        sb.appendLine("      \"name\": \"Warden\",")
        sb.appendLine("      \"version\": \"${esc(resolvedVersionName)}\"")
        sb.appendLine("    }")
        sb.appendLine("  },")
        sb.appendLine("  \"components\": [")
        components.forEachIndexed { idx, (group, name, version) ->
            val comma = if (idx < components.lastIndex) "," else ""
            val purl = "pkg:maven/$group/$name@$version"
            sb.appendLine("    {")
            sb.appendLine("      \"type\": \"library\",")
            sb.appendLine("      \"group\": \"${esc(group)}\",")
            sb.appendLine("      \"name\": \"${esc(name)}\",")
            sb.appendLine("      \"version\": \"${esc(version)}\",")
            sb.appendLine("      \"purl\": \"${esc(purl)}\"")
            sb.appendLine("    }$comma")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")

        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(sb.toString())
        logger.lifecycle("Generated CycloneDX SBOM with ${components.size} components: ${target.path}")
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        tasks.register("installAndGrantAccessibility$variantName") {
            group = "install"
            description = "Installs the app, grants Accessibility permission, and launches it"
            dependsOn("install$variantName")

            doLast {
                val adbPath = sdkComponents.adb.get().asFile.absolutePath
                val appId = "com.warden.app"
                Thread.sleep(2000)
                // Grant Accessibility Permission
                exec {
                    val combinedServices = "$appId/$appId.services.WardenService"

                    commandLine(adbPath, "shell", "settings", "put", "secure", "enabled_accessibility_services", combinedServices)
                }

                // Launch MainActivity
                exec {
                    commandLine(adbPath, "shell", "am", "start", "-n", "$appId/$appId.ui.activity.FragmentActivity")
                }
            }
        }
    }
}
