package org.jetbrains.kotlin.gradle

import org.gradle.api.logging.LogLevel
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import java.io.File

@RunWith(Parameterized::class)
class KotlinGradlePluginJpsParametrizedIT : BaseIncrementalGradleIT() {

    @Parameterized.Parameter
    @JvmField
    var relativePath: String = ""

    @Test
    fun testFromJps() {
        val project = JpsTestProject(jpsResourcesPath, relativePath, "2.4", LogLevel.DEBUG)

        callPerformAndAssertBuildStages(project)
    }

    companion object {

        private val jpsResourcesPath = File("../../../jps-plugin/testData/incremental")

        @Suppress("unused")
        @Parameterized.Parameters(name = "{index}: {0}")
        @JvmStatic
        fun data(): List<Array<String>> =
                jpsResourcesPath.walk()
                        .filter { it.isDirectory && isKnownJpsTestProject(it).first }
                        .map { arrayOf(it.toRelativeString(jpsResourcesPath)) }
                        .toList()
    }
}
