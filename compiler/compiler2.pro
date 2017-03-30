-injars '<output>/kotlin-compiler-before-shrink.jar'(
!com/thoughtworks/xstream/converters/extended/ISO8601**,
!com/thoughtworks/xstream/converters/reflection/CGLIBEnhancedConverter**,
!com/thoughtworks/xstream/io/xml/JDom**,
!com/thoughtworks/xstream/io/xml/Dom4J**,
!com/thoughtworks/xstream/io/xml/Xom**,
!com/thoughtworks/xstream/io/xml/Wstx**,
!com/thoughtworks/xstream/io/xml/KXml2**,
!com/thoughtworks/xstream/io/xml/BEAStax**,
!com/thoughtworks/xstream/io/json/Jettison**,
!com/thoughtworks/xstream/mapper/CGLIBMapper**,
!com/thoughtworks/xstream/mapper/LambdaMapper**,
!org/apache/log4j/jmx/Agent*,
!org/apache/log4j/net/JMS*,
!org/apache/log4j/net/SMTP*,
!org/apache/log4j/or/jms/MessageRenderer*,
!org/jdom/xpath/Jaxen*,
!org/mozilla/javascript/xml/impl/xmlbeans/**,
!net/sf/cglib/**,
!META-INF/maven**,
**.class,**.properties,**.kt,**.kotlin_*,**.jnilib,**.so,**.dll,
META-INF/services/**,META-INF/native/**,META-INF/extensions/**,META-INF/MANIFEST.MF,
messages/**)

-outjars '<kotlin-home>/lib/kotlin-compiler2.jar'

-dontnote **
-dontwarn com.intellij.util.ui.IsRetina*
-dontwarn com.intellij.util.RetinaImage*
-dontwarn apple.awt.*
-dontwarn dk.brics.automaton.*
-dontwarn org.fusesource.**
-dontwarn org.imgscalr.Scalr**
-dontwarn org.xerial.snappy.SnappyBundleActivator
-dontwarn com.intellij.util.CompressionUtil
-dontwarn com.intellij.util.SnappyInitializer
-dontwarn com.intellij.util.SVGLoader
-dontwarn com.intellij.util.SVGLoader$MyTranscoder
-dontwarn net.sf.cglib.**
-dontwarn org.objectweb.asm.** # this is ASM3, the old version that we do not use
-dontwarn com.sun.jna.NativeString
-dontwarn com.sun.jna.WString
-dontwarn com.intellij.psi.util.PsiClassUtil
-dontwarn org.apache.hadoop.io.compress.*
-dontwarn com.google.j2objc.annotations.Weak
-dontwarn org.iq80.snappy.HadoopSnappyCodec$SnappyCompressionInputStream
-dontwarn org.iq80.snappy.HadoopSnappyCodec$SnappyCompressionOutputStream
-dontwarn com.google.common.util.concurrent.*
-dontwarn org.apache.xerces.dom.**
-dontwarn org.apache.xerces.util.**
-dontwarn org.w3c.dom.ElementTraversal

-libraryjars '<rtjar>'
-libraryjars '<jssejar>'
-injars '<bootstrap.runtime>'
-injars '<bootstrap.reflect>'
-libraryjars '<bootstrap.script.runtime>'

-dontoptimize
-dontobfuscate
#-printusage
-verbose

-keep class javax.inject.** {
    public protected *;
}

#-keep class org.jetbrains.kotlin.psi.** {
#    public protected *;
#}
#-keep class org.jetbrains.kotlin.js.** {
#    public protected *;
#}
#-keep class org.jetbrains.kotlin.resolve.** {
#    public protected *;
#}
#-keep class org.jetbrains.kotlin.** {
#    public protected *;
#}

-keep class org.jetbrains.kotlin.* { public *; }
#-keep class org.jetbrains.kotlin.*$** { public *; }
-keep class org.jetbrains.kotlin.analyzer.** { public *; }
#-keep class org.jetbrains.kotlin.annotation.** { public *; }
-keep class org.jetbrains.kotlin.builtins.** { public *; }
-keep class org.jetbrains.kotlin.cfg.** { public *; }
-keep class org.jetbrains.kotlin.checkers.** { public *; }
#-keep class org.jetbrains.kotlin.compiler.** { public *; }
-keep class org.jetbrains.kotlin.compilerRunner.** { public *; }
-keep class org.jetbrains.kotlin.config.** { public *; }
-keep class org.jetbrains.kotlin.container.** { public *; }
-keep class org.jetbrains.kotlin.context.** { public *; }
-keep class org.jetbrains.kotlin.coroutines.** { public *; }
-keep class org.jetbrains.kotlin.descriptors.** { public *; }
-keep class org.jetbrains.kotlin.diagnostics.** { public *; }
-keep class org.jetbrains.kotlin.frontend.** { public *; }
-keep class org.jetbrains.kotlin.js.** { public *; }
-keep class org.jetbrains.kotlin.lexer.** { public *; }
-keep class org.jetbrains.kotlin.name.** { public *; }
-keep class org.jetbrains.kotlin.parsing.** { public *; }
-keep class org.jetbrains.kotlin.protobuf.** { public *; }
#-keep class org.jetbrains.kotlin.psi.** { public *; }
-keep class org.jetbrains.kotlin.renderer.** { public *; }
-keep class org.jetbrains.kotlin.resolve.** { public *; }
#-keep class org.jetbrains.kotlin.script.** { public *; }
#-keep class org.jetbrains.kotlin.synthetic.** { public *; }
-keep class org.jetbrains.kotlin.types.** { public *; }
-keep class org.jetbrains.kotlin.util.** { public *; }
-keep class org.jetbrains.kotlin.utils.** { public *; }

#-keep class org.jetbrains.kotlin.asJava.** { public protected *; }
#-keep class org.jetbrains.kotlin.backend.** { public protected *; }
#-keep class org.jetbrains.kotlin.build.** { public protected *; }
#-keep class org.jetbrains.kotlin.cli.** { public protected *; }
#-keep class org.jetbrains.kotlin.codegen.** { public protected *; }
#-keep class org.jetbrains.kotlin.daemon.** { public protected *; }
#-keep class org.jetbrains.kotlin.extensions.** { public protected *; }
#-keep class org.jetbrains.kotlin.fileClasses.** { public protected *; }
#-keep class org.jetbrains.kotlin.idea.** { public protected *; }
#-keep class org.jetbrains.kotlin.incremental.** { public protected *; }
#-keep class org.jetbrains.kotlin.inline.** { public protected *; }
#-keep class org.jetbrains.kotlin.kdoc.** { public protected *; }
#-keep class org.jetbrains.kotlin.load.** { public protected *; }
#-keep class org.jetbrains.kotlin.modules.** { public protected *; }
#-keep class org.jetbrains.kotlin.platform.** { public protected *; }
#-keep class org.jetbrains.kotlin.preprocessor.** { public protected *; }
#-keep class org.jetbrains.kotlin.progress.** { public protected *; }
#-keep class org.jetbrains.kotlin.serialization.** { public protected *; }
#-keep class org.jetbrains.kotlin.storage.** { public protected *; }
#-keep class org.jetbrains.kotlin.ir.** { public protected *; }
#-keep class org.jetbrains.kotlin.psi2ir.** { public protected *; }

#-keep class org.jetbrains.kotlin.compiler.plugin.** {
#    public protected *;
#}
#
#-keep class org.jetbrains.kotlin.extensions.** {
#    public protected *;
#}
#
#-keep class org.jetbrains.kotlin.protobuf.** {
#    public protected *;
#}
#
#-keep class org.jetbrains.kotlin.container.** { *; }

#-keepclassmembers class com.intellij.openapi.vfs.VirtualFile {
#    public InputStream getInputStream();
#}
#
#-keep class com.intellij.openapi.vfs.StandardFileSystems {
#    public static *;
#}

#-keep class com.intellij.psi.** {
#    public protected *;
#}

# This is needed so that the platform code which parses XML wouldn't fail, see KT-16968
# Note that these directives probably keep too much in the compiler JAR, we might not need all classes in these packages
-keep class org.apache.xerces.impl.** { public *; }
-keep class org.apache.xerces.jaxp.** { public *; }
-keep class org.apache.xerces.parsers.** { public *; }
-keep class org.apache.xml.** { public *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * {
    <init>(...);
    ** toString();
    ** hashCode();
    #void start();
    #void stop();
    #void dispose();
}

#-keep class org.jetbrains.kotlin.cli.js.K2JSCompiler {
#    public static void main(java.lang.String[]);
#}

-keep class com.intellij.openapi.util.Disposer { public *; }
-keep class org.picocontainer.Disposable { public *; }
-keep class org.picocontainer.Startable { public *; }

-keep class org.jetbrains.kotlin.config.KotlinSourceRoot { public *; }

-keep class org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment { *; }
-keep class org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles {
    #public static ** JS_CONFIG_FILES;
    *;
}

-keep class org.jetbrains.kotlin.js.config.JsConfig { public *; }
#-keep class org.jetbrains.kotlin.config.JVMConfigurationKeys { public *; }
-keep class org.jetbrains.kotlin.cli.common.ModuleVisibilityHelperImpl { public *; }
-keep class org.jetbrains.kotlin.js.facade.K2JSTranslator { public *; }


-keep class org.jetbrains.kotlin.load.java.FieldOverridabilityCondition
-keep class org.jetbrains.kotlin.load.java.ErasedOverridabilityCondition


#-whyareyoukeeping class org.jetbrains.kotlin.codegen.**
#-whyareyoukeeping class org.jetbrains.kotlin.load.**
#-whyareyoukeeping class org.jetbrains.kotlin.serialization.**
-whyareyoukeeping class org.jetbrains.kotlin.ir.**
#-whyareyoukeeping class org.jetbrains.kotlin.backend.**
#-whyareyoukeeping class org.jetbrains.kotlin.daemon.**
#-whyareyoukeeping class org.jetbrains.kotlin.asJava.**
