package org.jetbrains.kotlin.gradle

import org.gradle.api.logging.LogLevel
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.jetbrains.kotlin.gradle.incremental.BuildStep
import org.jetbrains.kotlin.gradle.incremental.parseTestBuildLog
import org.jetbrains.kotlin.incremental.testingUtils.*
import org.junit.Assume
import java.io.File
import kotlin.test.assertEquals

abstract class BaseIncrementalGradleIT : BaseGradleIT() {

    inner class JpsTestProject(val buildLogFinder: BuildLogFinder, val resourcesBase: File, val relPath: String, wrapperVersion: String = "2.10", minLogLevel: LogLevel = LogLevel.DEBUG) : Project(File(relPath).name, wrapperVersion, minLogLevel) {
        override val resourcesRoot = File(resourcesBase, relPath)
        val mapWorkingToOriginalFile = hashMapOf<File, File>()

        override fun setupWorkingDir(workingDir: File) {
            val srcDir = File(workingDir, "src")
            srcDir.mkdirs()
            val sourceMapping = copyTestSources(resourcesRoot, srcDir, filePrefix = "")
            mapWorkingToOriginalFile.putAll(sourceMapping)
            File(resourcesRootFile, "GradleWrapper-$wrapperVersion").copyRecursively(workingDir)
            File(resourcesRootFile, "incrementalGradleProject").copyRecursively(workingDir)
        }
    }

    fun JpsTestProject.performAndAssertBuildStages(options: BuildOptions = defaultBuildOptions(), weakTesting: Boolean = false) {
        // TODO: support multimodule tests
        if (resourcesRoot.walk().filter { it.name.equals("dependencies.txt", ignoreCase = true) }.any()) {
            Assume.assumeTrue("multimodule tests are not supported yet", false)
        }

        val connection = with(GradleConnector.newConnector()) {
            forProjectDirectory(projectDir)
            useGradleVersion(wrapperVersion)
            connect()
        }

        try {
            doPerformAndAssertBuildStages(options, connection, weakTesting)
        }
        finally {
            connection.close()
        }
    }

    private fun JpsTestProject.doPerformAndAssertBuildStages(options: BuildOptions, connection: ProjectConnection, weakTesting: Boolean) {
        build("classes", options = options, connection = connection) {
            assertSuccessful()
            assertReportExists()
        }

        val buildLogFile = buildLogFinder.findBuildLog(resourcesRoot) ?:
                throw IllegalStateException("build log file not found in $resourcesRoot")
        val buildLogSteps = parseTestBuildLog(buildLogFile)
        val modifications = getModificationsToPerform(resourcesRoot,
                                                      moduleNames = null,
                                                      allowNoFilesWithSuffixInTestData = false,
                                                      touchPolicy = TouchPolicy.CHECKSUM)

        assert(modifications.size == buildLogSteps.size) {
            "Modifications count (${modifications.size}) != expected build log steps count (${buildLogSteps.size})"
        }

        println("<--- Expected build log size: ${buildLogSteps.size}")
        buildLogSteps.forEach {
            println("<--- Expected build log stage: ${if (it.compileSucceeded) "succeeded" else "failed"}: kotlin: ${it.compiledKotlinFiles} java: ${it.compiledJavaFiles}")
        }

        for ((modificationStep, buildLogStep) in modifications.zip(buildLogSteps)) {
            modificationStep.forEach { it.perform(projectDir, mapWorkingToOriginalFile) }
            buildAndAssertStageResults(buildLogStep, options, connection, weakTesting)
        }

        rebuildAndCompareOutput(buildLogSteps.last().compileSucceeded, options, connection)
    }

    private fun JpsTestProject.buildAndAssertStageResults(expected: BuildStep, options: BuildOptions, connection: ProjectConnection, weakTesting: Boolean) {
        build("classes", options = options, connection = connection) {
            if (expected.compileSucceeded) {
                assertSuccessful()
                assertCompiledJavaSources(expected.compiledJavaFiles, weakTesting)
                assertCompiledKotlinSources(expected.compiledKotlinFiles, weakTesting)
            }
            else {
                assertFailed()
            }
        }
    }

    private fun JpsTestProject.rebuildAndCompareOutput(rebuildSucceedExpected: Boolean, options: BuildOptions, connection: ProjectConnection) {
        val outDir = File(File(projectDir, "build"), "classes")
        val incrementalOutDir = File(workingDir, "kotlin-classes-incremental")
        incrementalOutDir.mkdirs()
        outDir.copyRecursively(incrementalOutDir)

        build("clean", "classes", options = options, connection = connection) {
            val rebuildSucceed = resultCode == 0
            assertEquals(rebuildSucceed, rebuildSucceedExpected, "Rebuild exit code differs from incremental exit code")
            outDir.mkdirs()
            assertEqualDirectories(outDir, incrementalOutDir, forgiveExtraFiles = !rebuildSucceed)
        }
    }
}

