import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.api.tasks.compile.JavaCompile
import java.util.jar.JarFile

plugins {
    id("java")
}

group = "dev.stoshe.aerowars"
val pluginVersion = (findProperty("version") as? String)
    ?.takeIf { it.isNotBlank() && it != "unspecified" }
    ?: "1.0.0"
version = pluginVersion

val javaVersion = (findProperty("javaVersion") as? String) ?: "25"
val javaLanguageVersion = javaVersion.substringBefore('.').toInt()
val patchline = (findProperty("patchline") as? String) ?: "release"
val includesPack = ((findProperty("includesPack") as? String)?.toBoolean()) ?: true
val loadUserMods = ((findProperty("loadUserMods") as? String)?.toBoolean()) ?: false

// Locate the Hytale installation so we can compile against HytaleServer.jar.
// Order: -Phytale_home override -> OS default install -> bundled libs/HytaleServer.jar fallback.
val hytaleHome: String by extra {
    if (project.hasProperty("hytale_home")) {
        project.findProperty("hytale_home") as String
    } else {
        val os = DefaultNativePlatform.getCurrentOperatingSystem()
        when {
            os.isWindows -> "${System.getProperty("user.home")}/AppData/Roaming/Hytale"
            os.isMacOsX -> "${System.getProperty("user.home")}/Library/Application Support/Hytale"
            os.isLinux -> {
                val flatpakPath = "${System.getProperty("user.home")}/.var/app/com.hypixel.HytaleLauncher/data/Hytale"
                if (file(flatpakPath).exists()) flatpakPath
                else "${System.getProperty("user.home")}/.local/share/Hytale"
            }
            else -> ""
        }
    }
}

val installedServerJar = "$hytaleHome/install/$patchline/package/game/latest/Server/HytaleServer.jar"
val installedAssets = "$hytaleHome/install/$patchline/package/game/latest/Assets.zip"
val bundledServerJar = "libs/HytaleServer.jar"

// Prefer the installed jar; otherwise fall back to a jar dropped into libs/.
val hytaleServerJar = if (file(installedServerJar).exists()) installedServerJar else bundledServerJar
val hytaleAssets = installedAssets

if (!file(hytaleServerJar).exists()) {
    throw GradleException(
        "HytaleServer.jar not found. Looked at:\n" +
        "  $installedServerJar\n" +
        "  $bundledServerJar\n" +
        "Set -Phytale_home=/path/to/Hytale or drop HytaleServer.jar into libs/."
    )
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaLanguageVersion))
    }
}

tasks.withType<JavaCompile> {
    options.release.set(javaLanguageVersion)
    options.compilerArgs.add("-Xlint:deprecation")
}

repositories {
    mavenCentral()
    flatDir { dirs("libs") }
}

dependencies {
    compileOnly(files(hytaleServerJar))
    // Soft dependency: HelpChat PlaceholderAPI (placeholderapi.com) — compile only, NOT bundled, guarded at runtime.
    compileOnly(files("libs/PlaceholderAPI-1.0.7-CurseForge.jar"))
    implementation("com.google.code.gson:gson:2.10.1")
    // Optional SQL persistence (like Plots). These are NOT shaded into the jar — the plugin
    // compiles against them and degrades gracefully to JSON at runtime if the driver/Hikari
    // classes aren't on the server classpath (see StatsRepositoryFactory).
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.46.0.1")
    implementation("com.mysql:mysql-connector-j:8.4.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.4.1")
    implementation("org.postgresql:postgresql:42.7.4")
}

/**
 * The Hytale release line this build compiles against — "0.6" for a server reporting 0.6.5.
 *
 * Read from the server jar's own manifest rather than configured by hand, so the name on the file cannot
 * drift from what the build actually used. The line, not the patch: one jar serves the whole 0.6.x, and
 * stamping 0.6.5 on it would imply it does not run on 0.6.2 when it does. -PhytaleVersion=0.6 overrides;
 * a server that reports a date instead of a version (older builds did) just leaves the suffix off.
 */
fun detectHytaleLine(): String? {
    (findProperty("hytaleVersion") as? String)?.takeIf { it.isNotBlank() }?.let { return it }

    val reported = try {
        JarFile(file(hytaleServerJar)).use { jar ->
            jar.manifest?.mainAttributes?.getValue("Implementation-Version")
        }
    } catch (e: Exception) {
        logger.warn("Could not read the Hytale version from $hytaleServerJar: ${e.message}")
        null
    }

    return reported?.let { Regex("^(\\d+\\.\\d+)").find(it)?.groupValues?.get(1) }
}

val hytaleLine = detectHytaleLine()

if (hytaleLine == null) {
    logger.warn("Hytale version not detected; the jar will be named without one. Pass -PhytaleVersion=0.6 to set it.")
}

/**
 * The server range the manifest declares — ">=0.6.0 <0.7.0" for the 0.6 line. A server on another line
 * refuses the plugin at load, which beats loading it and failing on a removed API mid-match. Unknown line
 * falls back to "*".
 */
val hytaleRange = hytaleLine?.let { line ->
    val next = line.substringBefore('.') + "." + (line.substringAfter('.').toInt() + 1)
    ">=$line.0 <$next.0"
} ?: "*"

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("hytaleRange", hytaleRange)
    filesMatching("manifest.json") {
        expand("version" to project.version, "hytaleRange" to hytaleRange)
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("AeroWars")
    archiveVersion.set(project.version.toString())

    // AeroWars-1.1.0-hytale-0.6.jar. The game line is on the file because that is the question someone
    // downloading a release is actually asking, and a release page listing several versions can't answer it.
    hytaleLine?.let { archiveClassifier.set("hytale-$it") }

    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
        hytaleLine?.let { attributes("Hytale-Version" to it) }
    }

    from("src/main/resources")
}

val syncAssets = tasks.register<Copy>("syncAssets") {
    group = "hytale"
    description = "Sync assets generated in build back to source resources."
    from(sourceSets.main.get().output.resourcesDir?.absolutePath)
    into(sourceSets.main.get().resources.srcDirs.first().absolutePath)
    exclude("manifest.json")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

fun createServerArgs(): List<String> {
    val args = mutableListOf(
        "--allow-op",
        "--disable-sentry",
        "--assets=\"$hytaleAssets\""
    )
    val modsPaths = mutableListOf<String>()
    if (loadUserMods) modsPaths.add("$hytaleHome/UserData/Mods")
    if (includesPack) modsPaths.add(sourceSets.main.get().output.resourcesDir?.parentFile?.absolutePath ?: "")
    if (modsPaths.isNotEmpty()) args.add("--mods=\"${modsPaths.joinToString(",")}\"")
    return args
}

tasks.register<JavaExec>("runServer") {
    group = "hytale"
    description = "Runs a local Hytale server using files from the game install."
    dependsOn("classes")
    finalizedBy(syncAssets)
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(javaLanguageVersion))
    })
    mainClass.set("com.hypixel.hytale.Main")
    args = createServerArgs()
    classpath = files(
        hytaleServerJar,
        sourceSets.main.get().output.classesDirs,
        sourceSets.main.get().output.resourcesDir?.absolutePath
    )
    workingDir = file("run")
    standardInput = System.`in`
}
