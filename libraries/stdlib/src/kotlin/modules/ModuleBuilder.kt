package kotlin.modules

import java.util.ArrayList

public fun module(name: String, outputDir: String, callback: ModuleBuilder.() -> Unit) {
    val builder = ModuleBuilder(name, outputDir)
    builder.callback()
    AllModules.get()?.add(builder)
}

public class SourcesBuilder(private val parent: ModuleBuilder) {
    public fun plusAssign(pattern: String) {
        parent.addSourceFiles(pattern)
    }
}

public class DependencyClasspathBuilder(private val parent: ModuleBuilder) {
    public fun plusAssign(name: String) {
        parent.addClasspathEntry(name)
    }
}

public class OwnClasspathBuilder(private val parent: ModuleBuilder) {
    public fun plusAssign(name: String) {
        parent.addOwnClasspathEntry(name)
    }
}

public class AnnotationsPathBuilder(private val parent: ModuleBuilder) {
    public fun plusAssign(name: String) {
        parent.addAnnotationsPathEntry(name)
    }
}

public open class ModuleBuilder(private val name: String, private val outputDir: String) : Module {
    // http://youtrack.jetbrains.net/issue/KT-904
    private val sourceFiles0 = ArrayList<String>()
    private val ownClasspathRoots0 = ArrayList<String>()
    private val dependencyClasspathRoots0 = ArrayList<String>()
    private val annotationsRoots0 = ArrayList<String>()

    public val sources: SourcesBuilder
        get() = SourcesBuilder(this)

    public val classpath: DependencyClasspathBuilder
        get() = DependencyClasspathBuilder(this)

    public val ownClasspath: OwnClasspathBuilder
        get() = OwnClasspathBuilder(this)

    public val annotationsPath: AnnotationsPathBuilder
        get() = AnnotationsPathBuilder(this)

    public fun addSourceFiles(pattern: String) {
        sourceFiles0.add(pattern)
    }

    public fun addClasspathEntry(name: String) {
        dependencyClasspathRoots0.add(name)
    }

    public fun addOwnClasspathEntry(name: String) {
        ownClasspathRoots0.add(name)
    }

    public fun addAnnotationsPathEntry(name: String) {
        annotationsRoots0.add(name)
    }

    public override fun getOutputDirectory(): String = outputDir
    public override fun getSourceFiles(): List<String> = sourceFiles0
    public override fun getClasspathRoots(): List<String> = dependencyClasspathRoots0
    public override fun getOwnClasspathRoots(): List<String> = ownClasspathRoots0
    public override fun getAnnotationsRoots(): List<String> = annotationsRoots0
    public override fun getModuleName(): String = name
}

