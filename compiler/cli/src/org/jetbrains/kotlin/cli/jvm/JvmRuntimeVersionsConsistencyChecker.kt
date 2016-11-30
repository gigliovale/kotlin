/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.cli.jvm

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.utils.SmartList
import java.util.*
import java.util.jar.Manifest

internal inline fun Properties.getString(propertyName: String, otherwise: () -> String): String =
        getProperty(propertyName) ?: otherwise()

object JvmRuntimeVersionsConsistencyChecker {
    private val LOG = Logger.getInstance(JvmRuntimeVersionsConsistencyChecker::class.java)

    private fun fatal(message: String): Nothing {
        LOG.error(message)
        throw AssertionError(message)
    }

    private fun <T> T?.assertNotNull(message: () -> String): T =
            if (this == null) fatal(message()) else this

    // TODO replace with ERROR after bootstrapping
    private val VERSION_ISSUE_SEVERITY = CompilerMessageSeverity.WARNING

    private const val META_INF = "META-INF"
    private const val MANIFEST_MF = "$META_INF/MANIFEST.MF"

    private const val MANIFEST_KOTLIN_VERSION_ATTRIBUTE = "manifest.impl.attribute.kotlin.version"
    private const val MANIFEST_KOTLIN_VERSION_VALUE = "manifest.impl.value.kotlin.version"

    private const val KOTLIN_STDLIB_MODULE = "$META_INF/kotlin-stdlib.kotlin_module"
    private const val KOTLIN_REFLECT_MODULE = "$META_INF/kotlin-reflection.kotlin_module"

    private val KOTLIN_VERSION_ATTRIBUTE: String
    private val CURRENT_COMPILER_VERSION: LanguageVersion

    init {
        val manifestProperties: Properties = try {
            JvmRuntimeVersionsConsistencyChecker::class.java
                    .getResourceAsStream("/kotlinManifest.properties")
                    .let { input -> Properties().apply { load(input) } }
        }
        catch (e: Exception) {
            LOG.error(e)
            throw e
        }

        KOTLIN_VERSION_ATTRIBUTE = manifestProperties.getProperty(MANIFEST_KOTLIN_VERSION_ATTRIBUTE)
                .assertNotNull { "$MANIFEST_KOTLIN_VERSION_ATTRIBUTE not found in kotlinManifest.properties" }

        CURRENT_COMPILER_VERSION = run {
            val kotlinVersionString = manifestProperties.getProperty(MANIFEST_KOTLIN_VERSION_VALUE)
                    .assertNotNull { "$MANIFEST_KOTLIN_VERSION_VALUE not found in kotlinManifest.properties" }

            LanguageVersion.fromFullVersionString(kotlinVersionString)
                    .assertNotNull { "Incorrect Kotlin version: $kotlinVersionString" }
        }

        if (CURRENT_COMPILER_VERSION != LanguageVersion.LATEST) {
            fatal("Kotlin compiler version $CURRENT_COMPILER_VERSION in kotlinManifest.properties doesn't match ${LanguageVersion.LATEST}")
        }
    }

    class FileWithLanguageVersion(val file: VirtualFile, val version: LanguageVersion) {
        override fun toString(): String =
                "${file.canonicalPath}:$version"
    }

    class RuntimeJarsInfo(
            val kotlinStdlibJars: List<FileWithLanguageVersion>,
            val kotlinReflectJars: List<FileWithLanguageVersion>
    ) {
        val hasAnyJarsToCheck: Boolean
            get() = !(kotlinStdlibJars.isEmpty() && kotlinReflectJars.isEmpty())
    }

    fun checkCompilerClasspathConsistency(
            messageCollector: MessageCollector,
            languageVersionSettings: LanguageVersionSettings?,
            classpathJars: List<VirtualFile>
    ) {
        val runtimeJarsInfo = collectRuntimeJarsInfo(classpathJars)
        if (!runtimeJarsInfo.hasAnyJarsToCheck) return

        val languageVersion = languageVersionSettings?.languageVersion ?: CURRENT_COMPILER_VERSION

        // Even if language version option was explicitly specified, the JAR files SHOULD NOT be newer than the compiler.
        checkNotNewerThanCompiler(messageCollector, runtimeJarsInfo.kotlinStdlibJars)
        checkNotNewerThanCompiler(messageCollector, runtimeJarsInfo.kotlinReflectJars)

        checkCompatibleWithLanguageVersion(messageCollector, runtimeJarsInfo.kotlinStdlibJars, languageVersion)
        checkCompatibleWithLanguageVersion(messageCollector, runtimeJarsInfo.kotlinReflectJars, languageVersion)

        checkRuntimeAndReflectCompatibility(messageCollector, runtimeJarsInfo)
    }

    private fun checkNotNewerThanCompiler(messageCollector: MessageCollector, jars: List<FileWithLanguageVersion>) {
        for (jar in jars) {
            if (jar.version > CURRENT_COMPILER_VERSION) {
                messageCollector.issue("Run-time JAR file $jar is newer than compiler version $CURRENT_COMPILER_VERSION")
            }
        }
    }

    private fun checkCompatibleWithLanguageVersion(messageCollector: MessageCollector, jars: List<FileWithLanguageVersion>, languageVersion: LanguageVersion) {
        for (jar in jars) {
            if (jar.version < languageVersion) {
                messageCollector.issue("Run-time JAR file $jar is older than required for language version $languageVersion")
            }
        }
    }

    private fun checkRuntimeAndReflectCompatibility(messageCollector: MessageCollector, runtimeJarsInfo: RuntimeJarsInfo) {
        val oldestStdlibJar = runtimeJarsInfo.kotlinStdlibJars.minBy { it.version } ?: return
        val newestReflectJar = runtimeJarsInfo.kotlinReflectJars.maxBy { it.version } ?: return

        if (oldestStdlibJar.version != newestReflectJar.version) {
            messageCollector.issue("Run-time JAR file $oldestStdlibJar is not compatible with reflection JAR file $newestReflectJar")
        }
    }

    private fun MessageCollector.issue(message: String) {
        report(VERSION_ISSUE_SEVERITY, message, CompilerMessageLocation.NO_LOCATION)
    }

    private fun collectRuntimeJarsInfo(classpathJars: List<VirtualFile>): RuntimeJarsInfo {
        val kotlinStdlibJars: MutableList<FileWithLanguageVersion> = SmartList()
        val kotlinReflectJars: MutableList<FileWithLanguageVersion> = SmartList()

        for (jar in classpathJars) {
            val manifestFile = jar.findFileByRelativePath(MANIFEST_MF) ?: continue

            val containsKotlinStdlib = jar.findFileByRelativePath(KOTLIN_STDLIB_MODULE) != null
            val containsKotlinReflect = jar.findFileByRelativePath(KOTLIN_REFLECT_MODULE) != null
            if (!(containsKotlinStdlib || containsKotlinReflect)) continue

            val manifest = try {
                Manifest(manifestFile.inputStream)
            }
            catch (e: Exception) {
                continue
            }
            val version = manifest.getKotlinLanguageVersion()

            if (containsKotlinStdlib) kotlinStdlibJars.add(FileWithLanguageVersion(jar, version))
            if (containsKotlinReflect) kotlinReflectJars.add(FileWithLanguageVersion(jar, version))
        }

        return RuntimeJarsInfo(kotlinStdlibJars, kotlinReflectJars)
    }

    private fun Manifest.getKotlinLanguageVersion(): LanguageVersion =
            mainAttributes.getValue(KOTLIN_VERSION_ATTRIBUTE)?.let {
                LanguageVersion.fromFullVersionString(it)
            }
            ?: LanguageVersion.KOTLIN_1_0

}