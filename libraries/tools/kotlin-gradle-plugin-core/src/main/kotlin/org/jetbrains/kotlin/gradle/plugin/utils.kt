package org.jetbrains.kotlin.gradle.plugin

import org.gradle.api.Project
import java.io.File

public fun resolveDependencies(project: Project, vararg coordinates: String): Collection<File> {
    val dependencyHandler = project.getBuildscript().getDependencies()
    val configurationsContainer = project.getBuildscript().getConfigurations()

    val deps = coordinates.map { dependencyHandler.create(it) }
    val configuration = configurationsContainer.detachedConfiguration(*deps.copyToArray())

    return configuration.getResolvedConfiguration().getFiles(KSpec({ dep -> true }))!!
}

