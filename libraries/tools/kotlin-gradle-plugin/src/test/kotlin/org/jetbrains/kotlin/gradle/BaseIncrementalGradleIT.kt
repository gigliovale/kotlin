package org.jetbrains.kotlin.gradle

import com.google.common.io.Files
import org.gradle.api.logging.LogLevel
import org.gradle.tooling.GradleConnector
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


abstract class BaseIncrementalGradleIT : BaseGradleIT() {

    open inner class IncrementalTestProject(name: String, wrapperVersion: String = "1.6", minLogLevel: LogLevel = LogLevel.DEBUG) : Project(name, wrapperVersion, minLogLevel) {
        var modificationStage: Int = 1
    }

    inner class JpsTestProject(val resourcesBase: File, val relPath: String, wrapperVersion: String = "1.6", minLogLevel: LogLevel = LogLevel.DEBUG) : IncrementalTestProject(File(relPath).name, wrapperVersion, minLogLevel) {
        override val resourcesRoot = File(resourcesBase, relPath)

        override fun setupWorkingDir() {
            val srcDir = File(projectDir, "src")
            srcDir.mkdirs()
            resourcesRoot.walk()
                         .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".java")) }
                         .forEach { Files.copy(it, File(srcDir, it.name)) }
            copyDirRecursively(File(resourcesRootFile, "GradleWrapper-$wrapperVersion"), projectDir)
            File(projectDir, "build.gradle").writeText("""
buildscript {
  repositories {
    maven {
        url 'file://' + pathToKotlinPlugin
    }
  }
  dependencies {
    classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:0.1-SNAPSHOT'
  }
}

apply plugin: "kotlin"

sourceSets {
  main {
     kotlin {
        srcDir 'src'
     }
     java {
        srcDir 'src'
     }
  }
  test {
     kotlin {
        srcDir 'src'
     }
     java {
        srcDir 'src'
     }
  }
}

repositories {
  maven {
     url 'file://' + pathToKotlinPlugin
  }
}
            """)
        }
    }

    fun IncrementalTestProject.modify(runStage: Int? = null) {
        // TODO: multimodule support
        val projectSrcDir = File(File(workingDir, projectName), "src")
        assertTrue(projectSrcDir.exists())
        val actualStage = runStage ?: modificationStage

        fun resource2project(f: File) = File(projectSrcDir, f.toRelativeString(resourcesRoot))

        resourcesRoot.walk().filter { it.isFile }.forEach {
            val nameParts = it.name.split(".")
            if (nameParts.size > 2) {
                val (fileStage, hasStage) = nameParts.last().toIntOr(0)
                if (!hasStage || fileStage == actualStage) {
                    val orig = File(resource2project(it.parentFile), nameParts.dropLast(if (hasStage) 2 else 1).joinToString("."))
                    when (if (hasStage) nameParts[nameParts.size - 2] else nameParts.last()) {
                        "touch" -> {
                            assert(orig.exists())
                            orig.setLastModified(Date().time)
                        }
                        "new" -> {
                            it.copyTo(orig, overwrite = true)
                            orig.setLastModified(Date().time)
                        }
                        "delete" -> {
                            assert(orig.exists())
                            orig.delete()
                        }
                    }
                }
            }
        }

        modificationStage = actualStage + 1
    }

    class StageResults(val compiledKotlinFiles: HashSet<String> = hashSetOf(), val compiledJavaFiles: HashSet<String> = hashSetOf(), var compileSucceeded: Boolean = true)

    fun parseTestBuildLog(file: File): List<StageResults> {
        class StagedLines(val stage: Int, val line: String)

        return file.readLines()
            .map { if (it.startsWith("========== Step")) "" else it }
            .fold(arrayListOf<StagedLines>()) { slines, line ->
                val (curStage, prevWasBlank) = slines.lastOrNull()?.let{ Pair(it.stage, it.line.isBlank()) } ?: Pair(0, false)
                slines.add(StagedLines(curStage + if (line.isBlank() && prevWasBlank) 1 else 0, line))
                slines
            }
            .fold(Pair(0, arrayListOf<StageResults>())) { stageAndRes, sline ->
                // for lazy creation of the node
                fun curStageResults(): StageResults {
                    if (stageAndRes.second.isEmpty() || sline.stage > stageAndRes.first) {
                        stageAndRes.second.add(StageResults())
                    }
                    return stageAndRes.second.last()
                }

                when {
                    sline.line.endsWith(".java", ignoreCase = true) -> curStageResults().compiledJavaFiles.add(sline.line)
                    sline.line.endsWith(".kt", ignoreCase = true) -> curStageResults().compiledKotlinFiles.add(sline.line)
                    sline.line.equals("COMPILATION FAILED", ignoreCase = true) -> curStageResults().compileSucceeded = false
                }
                Pair(sline.stage, stageAndRes.second)
            }
            .second
    }


    fun IncrementalTestProject.performAndAssertBuildStages() {

        val checkKnown = isKnownJpsTestProject(resourcesRoot)
        assertTrue(checkKnown.first, checkKnown.second ?: "")

        build("build", options = BuildOptions(withDaemon = true)) {
            assertSuccessful()
            assertReportExists()
        }

        val buildLogFile = resourcesRoot.listFiles { f: File -> f.name.endsWith("build.log") }?.sortedBy { it.length() }?.firstOrNull()
        assertNotNull(buildLogFile, "*build.log file not found" )

        val buildLog = parseTestBuildLog(buildLogFile!!)
        assertTrue(buildLog.any())

        if (buildLog.size == 1) {
            modify()
            buildAndAssertStageResults(buildLog.first())
        }
        else {
            buildLog.forEachIndexed { stage, stageResults ->
                modify(stage + 1)
                buildAndAssertStageResults(stageResults)
            }
        }
    }

    fun IncrementalTestProject.buildAndAssertStageResults(expected: StageResults) {
        build("build", options = BuildOptions(withDaemon = true)) {
            if (expected.compileSucceeded) {
                assertSuccessful()
            }
            else {
                assertFailed()
            }
            assertCompiledJavaSources(expected.compiledJavaFiles)
            assertCompiledKotlinSources(expected.compiledKotlinFiles)
        }
    }

    fun IncrementalTestProject.buildWithApi(vararg tasks: String, options: BuildOptions = BuildOptions(), check: CompiledProject.() -> Unit) {
        val projectDir = File(workingDir, projectName)
        if (!projectDir.exists())
            setupWorkingDir()

        val output = gradleBuild(wrapperVersion, projectDir, tasks, createGradleTailParameters(options.copy(daemonOptionSupported = false)).toTypedArray()).toString()
        val resultCode = 0 // TODO: take from gradle
        CompiledProject(this, output, resultCode).check()
    }

    fun callPerformAndAssertBuildStages(project: IncrementalTestProject) = project.performAndAssertBuildStages()
}


private val knownExtensions = arrayListOf("kt", "java")

private fun String.toIntOr(defaultVal: Int): Pair<Int, Boolean> {
    try {
        return Pair(toInt(), true)
    }
    catch (e: NumberFormatException) {
        return Pair(defaultVal, false)
    }
}

fun isKnownJpsTestProject(projectRoot: File): Pair<Boolean, String?> {
    var hasKnownSources = false
    projectRoot.walk().filter { it.isFile }.forEach {
        if (it.name.equals("dependencies.txt", ignoreCase = true))
            return@isKnownJpsTestProject Pair(false, "multimodule tests are not supported yet")
        val nameParts = it.name.split(".")
        if (nameParts.size > 2) {
            val (fileStage, hasStage) = nameParts.last().toIntOr(0)
            val ext = nameParts.get(nameParts.size - (if (hasStage) 3 else 2))
            if (!knownExtensions.contains(ext))
                return@isKnownJpsTestProject Pair(false, "unknown staged file ${it.name}")
        }
        if (!hasKnownSources && it.extension in knownExtensions) {
            hasKnownSources = true
        }
    }
    return if (hasKnownSources) Pair(true, null)
           else Pair(false, "no known sources found")
}


fun gradleBuild(gradleVersion: String, projectDir: File, tasks: Array<out String>, args: Array<out String>): OutputStream {
    // Configure the connector and create the connection
    val connector = GradleConnector.newConnector()

//    connector.useGradleVersion(gradleVersion)

    connector.forProjectDirectory(projectDir)

    val connection = connector.connect()
    try {
        // Configure the build
        val launcher = connection.newBuild()
        launcher.forTasks(*tasks)
        launcher.withArguments(*args)
        val output = ByteArrayOutputStream()
        launcher.setStandardOutput(output)
        launcher.setStandardError(output)

        // Run the build
        launcher.run()
        return output
    } finally {
        // Clean up
        connection.close()
    }
}