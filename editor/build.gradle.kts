import java.io.File

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// ---------------------------------------------------------------------------------------------
// CodeMirror 6 bundle
//
// The editor (editor/web) is bundled with esbuild into src/main/assets/editor/dist, which is
// packaged into the APK. At runtime nothing is downloaded: the editor works fully offline.
// Building needs Node.js 18+ and network access to the npm registry the first time.
// Skip the step with -Pbytecode.skipEditorBundle=true if dist/ already exists.
// Set BYTECODE_MINIFY=1 to minify the bundle (the release workflow does).
// ---------------------------------------------------------------------------------------------
val webDirectory: File = layout.projectDirectory.dir("web").asFile
val distDirectory: File = layout.projectDirectory.dir("src/main/assets/editor/dist").asFile
val isWindows = System.getProperty("os.name").lowercase().contains("windows")
val npmCommand = if (isWindows) "npm.cmd" else "npm"
val nodeCommand = if (isWindows) "node.exe" else "node"
val skipEditorBundle = providers.gradleProperty("bytecode.skipEditorBundle").orNull == "true"

fun requireTool(command: String) {
    val available = try {
        val process = ProcessBuilder(command, "--version").redirectErrorStream(true).start()
        process.inputStream.readBytes()
        process.waitFor() == 0
    } catch (error: java.io.IOException) {
        false
    }
    if (!available) {
        throw GradleException(
            "'$command' was not found. Install Node.js 18 or newer (https://nodejs.org), " +
                "or build with -Pbytecode.skipEditorBundle=true if editor/src/main/assets/editor/dist already exists."
        )
    }
}

val npmInstall = tasks.register<Exec>("npmInstall") {
    group = "bytecode"
    description = "Installs the editor's npm dependencies (CodeMirror 6 and esbuild)"
    onlyIf { !skipEditorBundle }
    workingDir = webDirectory
    commandLine(npmCommand, "install", "--no-audit", "--no-fund", "--loglevel=error")
    inputs.file(File(webDirectory, "package.json"))
    outputs.dir(File(webDirectory, "node_modules"))
    doFirst { requireTool(npmCommand) }
}

val bundleEditor = tasks.register<Exec>("bundleEditor") {
    group = "bytecode"
    description = "Bundles the CodeMirror 6 editor into src/main/assets/editor/dist"
    onlyIf { !skipEditorBundle }
    dependsOn(npmInstall)
    workingDir = webDirectory
    commandLine(nodeCommand, "build.mjs")
    inputs.dir(File(webDirectory, "src"))
    inputs.file(File(webDirectory, "package.json"))
    inputs.file(File(webDirectory, "build.mjs"))
    inputs.property("minify", providers.environmentVariable("BYTECODE_MINIFY").orElse("0"))
    outputs.dir(distDirectory)
    doLast {
        if (!File(distDirectory, "main.js").exists()) {
            throw GradleException("The editor bundle was not produced (expected $distDirectory/main.js).")
        }
    }
}

tasks.named("preBuild") {
    dependsOn(bundleEditor)
}

// The bundle lives inside the assets source set, so every task that reads assets waits for it.
tasks.configureEach {
    val readsAssets = (name.startsWith("merge") && name.endsWith("Assets")) || name.startsWith("lint")
    if (readsAssets) {
        dependsOn(bundleEditor)
    }
}

android {
    namespace = "dev.bytecode.editor"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
}
