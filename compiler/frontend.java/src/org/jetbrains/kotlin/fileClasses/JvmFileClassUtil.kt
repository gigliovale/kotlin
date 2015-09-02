/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.fileClasses

import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.annotations.AnnotationsImpl
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.constants.BooleanValue
import org.jetbrains.kotlin.resolve.constants.StringValue

public object JvmFileClassUtil {
    public val JVM_CLASS_NAME: FqName = FqName("kotlin.jvm.JvmClassName")
    public val JVM_CLASS_NAME_SHORT_NAME: String = JVM_CLASS_NAME.shortName().asString()

    private val JVM_CLASS_NAME_PARAM_NAME: String = "name"
    private val JVM_CLASS_NAME_PARAM_NAME_INDEX: Int = 0
    private val JVM_CLASS_NAME_PARAM_MULTIPLE_FILES: String = "multipleFiles"
    private val JVM_CLASS_NAME_PARAM_MULTIPLE_FILES_INDEX: Int = 1

    public @jvmStatic fun getFileClassInfo(file: JetFile, jvmClassNameAnnotation: ParsedJmvClassNameAnnotation?): JvmFileClassInfo =
            if (jvmClassNameAnnotation != null)
                getFileClassInfoForAnnotation(file, jvmClassNameAnnotation)
            else
                getDefaultFileClassInfo(file)

    public @jvmStatic fun getFileClassInfoForAnnotation(file: JetFile, jvmClassNameAnnotation: ParsedJmvClassNameAnnotation): JvmFileClassInfo =
            if (jvmClassNameAnnotation.multipleFiles)
                JvmMultifileFacadePartInfo(getHiddenPartFqName(file, jvmClassNameAnnotation),
                                           getFacadeFqName(file, jvmClassNameAnnotation))
            else
                JvmFileFacadeInfo(getFacadeFqName(file, jvmClassNameAnnotation))

    public @jvmStatic fun getDefaultFileClassInfo(file: JetFile): JvmFileClassInfo =
            JvmFileFacadeInfo(PackagePartClassUtils.getPackagePartFqName(file.packageFqName, file.name))

    public @jvmStatic fun getFacadeFqName(file: JetFile, jvmClassNameAnnotation: ParsedJmvClassNameAnnotation): FqName =
            file.packageFqName.child(Name.identifier(jvmClassNameAnnotation.name))

    public @jvmStatic fun getHiddenPartFqName(file: JetFile, jvmClassNameAnnotation: ParsedJmvClassNameAnnotation): FqName =
            file.packageFqName.child(Name.identifier(manglePartName(jvmClassNameAnnotation.name, file.name)))

    public @jvmStatic fun manglePartName(facadeName: String, fileName: String): String =
            "${facadeName}__${PackagePartClassUtils.getFilePartShortName(fileName)}"

    public @jvmStatic fun parseJvmClassName(annotations: Annotations): ParsedJmvClassNameAnnotation? =
            annotations.findAnnotation(JVM_CLASS_NAME)?.let { parseJvmClassName(it) }

    public @jvmStatic fun parseJvmClassName(annotation: AnnotationDescriptor): ParsedJmvClassNameAnnotation {
        var name: String? = null
        var multipleFiles: Boolean = false
        for ((parameter, value) in annotation.allValueArguments.entrySet()) {
            when (parameter.name.asString()) {
                JVM_CLASS_NAME_PARAM_NAME ->
                    name = (value as? StringValue)?.value
                JVM_CLASS_NAME_PARAM_MULTIPLE_FILES ->
                    multipleFiles = (value as? BooleanValue)?.value ?: false
            }
        }
        return ParsedJmvClassNameAnnotation(name!!, multipleFiles)
    }

    public @jvmStatic fun getFileClassInfoNoResolve(file: JetFile): JvmFileClassInfo =
            getFileClassInfo(file, parseJvmClassNameNoResolve(file))

    public @jvmStatic fun parseJvmClassNameNoResolve(file: JetFile): ParsedJmvClassNameAnnotation? =
            findJvmClassNameAnnotationNoResolve(file)?.let { parseJvmClassNameNoResolve(it) }

    public @jvmStatic fun findJvmClassNameAnnotationNoResolve(file: JetFile): JetAnnotationEntry? =
            file.fileAnnotationList?.annotationEntries?.firstOrNull {
                it.calleeExpression?.constructorReferenceExpression?.getReferencedName() == JVM_CLASS_NAME_SHORT_NAME
            }

    public @jvmStatic fun parseJvmClassNameNoResolve(annotationEntry: JetAnnotationEntry): ParsedJmvClassNameAnnotation? {
        var name: String? = null
        var multipleFiles: Boolean = false
        var argumentIndex = 0
        var hasErrors: Boolean = false
        for (valueArgument in annotationEntry.valueArguments) {
            val mappedArgumentIndex = if (valueArgument.isNamed()) {
                when (valueArgument.getArgumentName()?.asName?.asString()) {
                    JVM_CLASS_NAME_PARAM_NAME ->
                        JVM_CLASS_NAME_PARAM_NAME_INDEX
                    JVM_CLASS_NAME_PARAM_MULTIPLE_FILES ->
                        JVM_CLASS_NAME_PARAM_MULTIPLE_FILES_INDEX
                    else -> -1
                }
            }
            else argumentIndex

            when (mappedArgumentIndex) {
                JVM_CLASS_NAME_PARAM_NAME_INDEX ->
                    name = getLiteralStringFromRestrictedConstExpression(valueArgument.getArgumentExpression())
                JVM_CLASS_NAME_PARAM_MULTIPLE_FILES_INDEX ->
                    multipleFiles = getLiteralBooleanFromRestrictedConstExpression(valueArgument.getArgumentExpression())
                else ->
                    hasErrors = true
            }
            ++argumentIndex
        }
        hasErrors = hasErrors || argumentIndex >= 2
        return if (hasErrors) null else name?.let { ParsedJmvClassNameAnnotation(it, multipleFiles) }
    }

    private @jvmStatic fun getLiteralStringFromRestrictedConstExpression(argumentExpression: JetExpression?): String? {
        val stringTemplate = argumentExpression as? JetStringTemplateExpression ?: return null
        val stringTemplateEntries = stringTemplate.entries
        if (stringTemplateEntries.size() != 1) return null
        val singleEntry = stringTemplateEntries[0] as? JetLiteralStringTemplateEntry ?: return null
        return singleEntry.text
    }

    private @jvmStatic fun getLiteralBooleanFromRestrictedConstExpression(argumentExpression: JetExpression?): Boolean =
            (argumentExpression as? JetConstantExpression)?.text == "true"

    public @jvmStatic fun collectFileAnnotations(file: JetFile, bindingContext: BindingContext): Annotations {
        val fileAnnotationsList = file.fileAnnotationList ?: return Annotations.EMPTY
        val annotationDescriptors = arrayListOf<AnnotationDescriptor>()
        for (annotationEntry in fileAnnotationsList.annotationEntries) {
            bindingContext.get(BindingContext.ANNOTATION, annotationEntry)?.let { annotationDescriptors.add(it) }
        }
        return AnnotationsImpl(annotationDescriptors)
    }
}

public class ParsedJmvClassNameAnnotation(public val name: String, public val multipleFiles: Boolean)
