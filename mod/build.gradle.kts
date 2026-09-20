import java.nio.file.Files

plugins {
    id("fabric-loom") version "1.17.21"
    java
    `maven-publish`
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

// Development-only source set: headless harnesses, profilers, debug commands. It compiles against main
// and is added to the runClient/runServer classpath, but `jar` packages sourceSets.main.output alone, so
// none of it reaches a player.
val devSourceSet: SourceSet = sourceSets.create("dev") {
    java.srcDir("src/dev/java")
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

sourceSets.test {
    compileClasspath += devSourceSet.output
    runtimeClasspath += devSourceSet.output
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    // Debris advection and the wind field run on the graphics card through OpenCL. JOCL bundles its own
    // natives and loads them at runtime; `include` packages it so a dedicated server needs no extra dep.
    implementation("org.jocl:jocl:2.0.5")
    include("org.jocl:jocl:2.0.5")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withJavadocJar()
}

// The javadoc that travels covers api/ and nothing else: the rest is mechanics, and a mechanic is not a
// promise. Doclint stays off because these are comments written for a reader, not a schema.
tasks.javadoc {
    include("oas/dreyka/vortexdread/api/**")
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

publishing {
    publications {
        create<MavenPublication>("mod") {
            from(components["java"])
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// Only main ships, so only main is stripped of local variable names. SourceFile and LineNumberTable stay:
// without them every player crash report says "(Unknown Source)".
tasks.named<JavaCompile>("compileJava") {
    options.debugOptions.debugLevel = "source,lines"
}

// ---- Kernel packaging ----
// The loader reads /kernels/<name>.clx. Each .cl is copied to that name with its comments truncated, and
// the raw .cl is excluded so the jar carries exactly one copy.
val kernelDir = file("src/main/resources/kernels")
tasks.processResources {
    exclude("kernels/*.cl")
    inputs.dir(kernelDir)
    doLast {
        val outDir = destinationDir.resolve("kernels")
        outDir.mkdirs()
        kernelDir.listFiles { f -> f.extension == "cl" }?.sortedBy { it.name }?.forEach { source ->
            // Truncating at the first `//` rather than dropping whole lines: several comments here are
            // trailing ones on live kernel code. There are no block comments and no `//` inside a string.
            val stripped = source.readText(Charsets.UTF_8).lineSequence()
                    .map { line -> val i = line.indexOf("//"); if (i >= 0) line.substring(0, i) else line }
                    .map { it.trimEnd() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n", postfix = "\n")
            outDir.resolve("${source.nameWithoutExtension}.clx").writeText(stripped, Charsets.UTF_8)
            logger.lifecycle("[kernel] ${source.name} -> ${source.nameWithoutExtension}.clx")
        }
    }
}

// OpenCL needs two nudges on Fedora, neither of which needs root. Mesa's rusticl exposes no device at all
// unless RUSTICL_ENABLE names the driver, and the JOCL binding dlopens the unversioned libOpenCL.so, which
// ships only in the -devel package. Short either one and the sky reports no usable device on a machine with a
// working card, and the launch looks fine while running the slow path. Nothing asks for RUSTICL_FEATURES=fp64:
// the solver is single precision throughout, and the double precision this card exposes is expanded in
// software and rounds worse than the single precision divide it was meant to replace.
val openClEnv: Map<String, String> = runCatching {
    val loader = listOf("/usr/lib64/libOpenCL.so.1", "/usr/lib/x86_64-linux-gnu/libOpenCL.so.1")
            .map { file(it) }
            .firstOrNull { it.exists() } ?: return@runCatching emptyMap()
    val dir = layout.buildDirectory.dir("opencl").get().asFile
    dir.mkdirs()
    val link = dir.resolve("libOpenCL.so").toPath()
    Files.deleteIfExists(link)
    Files.createSymbolicLink(link, loader.toPath())
    mapOf(
            "RUSTICL_ENABLE" to (System.getenv("RUSTICL_ENABLE") ?: "radeonsi"),
            "LD_LIBRARY_PATH" to listOfNotNull(dir.absolutePath, System.getenv("LD_LIBRARY_PATH"))
                    .joinToString(":")
    )
}.getOrDefault(emptyMap())

loom {
    runs {
        named("client") {
            openClEnv.forEach { (key, value) -> environmentVariable(key, value) }
            runDir("run")
            source(devSourceSet)
            vmArgs(
                "-Xms2G",
                "-Xmx6G",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=25",
                "-XX:G1NewSizePercent=40",
                "-XX:G1MaxNewSizePercent=50",
                "-XX:G1HeapRegionSize=16M",
                "-XX:G1ReservePercent=15",
                "-XX:+ParallelRefProcEnabled",
                "-XX:+PerfDisableSharedMem",
                "-XX:+AlwaysActAsServerClassMachine",
                "-XX:+DisableExplicitGC"
            )
            // The client refuses to start with two quick-play options, so this one is overridable rather
            // than additive. -PvdQuickPlay takes "none", "solo:<world>" or "join:<host:port>"; the headless
            // harness uses the last one so the client sits in the world its console commands drive.
            when (val quickPlay = (project.findProperty("vdQuickPlay") as String?) ?: "solo:Tornado Alley") {
                "none" -> {}
                else -> if (quickPlay.startsWith("join:")) {
                    programArgs("--quickPlayMultiplayer", quickPlay.removePrefix("join:"))
                } else {
                    programArgs("--quickPlaySingleplayer", quickPlay.removePrefix("solo:"))
                }
            }
        }
        create("client1") {
            client()
            openClEnv.forEach { (key, value) -> environmentVariable(key, value) }
            runDir("run/client1")
            configName = "Minecraft Client 1"
            source(devSourceSet)
            vmArgs("-Xms2G", "-Xmx4G", "-XX:+UseG1GC")
            programArgs("--username", "Tester1", "--quickPlayMultiplayer", "localhost:25565")
        }
        named("server") {
            // A headless server with a card gets the same win as a desktop: compute needs no display.
            openClEnv.forEach { (key, value) -> environmentVariable(key, value) }
            runDir("run/server")
            source(devSourceSet)
            vmArgs(
                "-Xms2G",
                "-Xmx6G",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=50",
                "-XX:+ParallelRefProcEnabled",
                "-XX:+PerfDisableSharedMem",
                "-XX:+AlwaysActAsServerClassMachine"
            )
        }
    }
}

// A forked JVM gets an empty stdin unless it is handed one, which leaves the dedicated server with no
// console at all: the headless harness drives it by writing commands into the run task's input.
tasks.named<JavaExec>("runServer") {
    standardInput = System.`in`
}

// Client gametests: pixels are the one thing neither JUnit nor a dedicated server can assert.
// createSourceSet gives src/gametest its own mod metadata, so the shipped fabric.mod.json never declares
// an entrypoint the player jar does not contain.
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "vortexdread-gametest"
        enableGameTests = true
        enableClientGameTests = true
        eula = true
    }
}

tasks.named<JavaExec>("runClientGameTest") {
    // -PvdGametestOnly=funnel,debris narrows the run to the suites whose simple name starts with one of
    // those tokens. The runner's own filter selects mod ids, and every suite here shares one.
    (project.findProperty("vdGametestOnly") as String?)?.let {
        systemProperty("vortexdread.gametest.only", it)
    }
    // The gametest run directory is rebuilt per launch, so a jar dropped into its mods/ folder by hand is
    // gone before the client reads it. fabric.addMods survives that rebuild.
    (project.findProperty("vdGametestMods") as String?)?.let { list ->
        val jars = list.split(",")
                .map { file(it.trim()).absolutePath }
                .joinToString(File.pathSeparator)
        systemProperty("fabric.addMods", jars)
    }
}

// `build` ignores a source set nothing depends on, so a gametest that stopped compiling would only
// surface at the next client launch.
tasks.check {
    dependsOn(tasks.named("compileGametestJava"))
}

// Measurement benches print tables no automated run reads, and they cost seconds. Gating them on a Gradle
// property rather than @Disabled keeps them runnable: --tests selects tests, it does not re-enable a
// disabled one, and the forked test JVM never sees a bare -D from the command line.
//   ./gradlew test -Pvd.bench=true --tests "oas.dreyka.vortexdread.wind.WindFieldPerfBench"
val benchFlag = providers.gradleProperty("vd.bench").orElse("false")

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2G"
    // The parity test wants the real card. Without these the forked test JVM finds no device, the test
    // skips itself, and the suite goes green having compared nothing.
    openClEnv.forEach { (key, value) -> environment(key, value) }
    // .get() is required: systemProperty stringifies its argument at fork time, and handing it the
    // Provider itself would set a value that never equals "true".
    systemProperty("vd.bench", benchFlag.get())
    // A bench whose table lands in the XML report and nowhere else has not been read. Only when asked for,
    // since the ordinary suite printing every stream turns a green run into three screens of noise.
    if (benchFlag.get() == "true") {
        testLogging.showStandardStreams = true
    }
}

// MIT obliges the notice to follow any substantial copy, and the same file carries the notice of the
// bundled JOCL. `../` because the Gradle root is mod/ while the file sits at the repository root.
// On `jar` rather than `remapJar`, which copies the jar output and inherits it.
tasks.jar {
    from("../LICENSE")
}
