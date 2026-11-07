import java.io.File

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// ---------------------------------------------------------------------------------------------
// Luau sources
//
// The official Luau repository is cloned at build time; no copy of it is stored in this project.
// The release tag is read from luau/LUAU_VERSION and can be overridden with
//     -Pbytecode.luauTag=<tag>
// If the pinned tag cannot be cloned, the newest numeric release tag is used instead and a
// warning is printed. The tag that was actually used is written to
// native/third_party/luau/.bytecode-tag and shown in the app's About dialog.
// ---------------------------------------------------------------------------------------------
val luauRepositoryUrl = "https://github.com/luau-lang/luau.git"
val luauSourceDir: File = layout.projectDirectory.dir("native/third_party/luau").asFile
val requestedLuauTag: String = providers.gradleProperty("bytecode.luauTag").orNull
    ?: layout.projectDirectory.file("LUAU_VERSION").asFile.readText().trim()
val nativeAbis: List<String> = (providers.gradleProperty("bytecode.abis").orNull ?: "arm64-v8a,armeabi-v7a,x86_64")
    .split(",")
    .map { it.trim() }
    .filter { it.isNotEmpty() }

fun runProcess(workingDir: File, vararg command: String): Pair<Int, String> {
    val process = ProcessBuilder(*command)
        .directory(workingDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    return process.waitFor() to output
}

fun newestLuauTag(workingDir: File): String? {
    val (exitCode, output) = runProcess(workingDir, "git", "ls-remote", "--tags", "--refs", luauRepositoryUrl)
    if (exitCode != 0) return null
    val tagPattern = Regex("refs/tags/(0\\.\\d+)$")
    return output.lineSequence()
        .mapNotNull { line -> tagPattern.find(line.trim())?.groupValues?.get(1) }
        .maxByOrNull { tag -> tag.substringAfter('.').toInt() }
}

val fetchLuau = tasks.register("fetchLuau") {
    group = "bytecode"
    description = "Clones the official Luau sources into luau/native/third_party/luau"
    onlyIf { !File(luauSourceDir, "CMakeLists.txt").exists() }
    doLast {
        val parent = luauSourceDir.parentFile
        parent.mkdirs()

        fun clone(tag: String): Pair<Int, String> {
            luauSourceDir.deleteRecursively()
            return runProcess(
                parent, "git", "clone", "--quiet", "--depth", "1", "--branch", tag,
                luauRepositoryUrl, luauSourceDir.name
            )
        }

        var usedTag = requestedLuauTag
        var attempt = try {
            clone(usedTag)
        } catch (e: java.io.IOException) {
            throw GradleException(
                "Could not start 'git'. Install Git (https://git-scm.com) or clone " +
                    "$luauRepositoryUrl manually into ${luauSourceDir.path}.", e
            )
        }
        if (attempt.first != 0) {
            logger.warn("Luau tag '$usedTag' could not be cloned:\n${attempt.second}")
            val fallback = newestLuauTag(parent)
                ?: throw GradleException(
                    "Could not download Luau (needs network access to github.com).\n" +
                        "Clone $luauRepositoryUrl into ${luauSourceDir.path} manually and re-run the build."
                )
            logger.warn("Falling back to the newest Luau release tag: $fallback")
            usedTag = fallback
            attempt = clone(usedTag)
            if (attempt.first != 0) {
                throw GradleException("Cloning Luau tag '$usedTag' failed:\n${attempt.second}")
            }
        }
        File(luauSourceDir, ".bytecode-tag").writeText(usedTag + "\n")
        logger.lifecycle("Luau $usedTag cloned into ${luauSourceDir.path}")
    }
}

// CMake must never run before the sources exist (this also covers IDE sync).
tasks.configureEach {
    val isNativeTask = name.startsWith("configureCMake") ||
        name.startsWith("buildCMake") ||
        name.startsWith("generateJsonModel") ||
        name.startsWith("externalNativeBuild")
    if (isNativeTask) {
        dependsOn(fetchLuau)
    }
}

android {
    namespace = "dev.bytecode.luau"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += nativeAbis
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("native/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
