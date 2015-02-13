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

package org.jetbrains.kotlin.resolve.constants.evaluate

import org.jetbrains.kotlin.resolve.annotation.AbstractAnnotationDescriptorResolveTest
import org.jetbrains.kotlin.resolve.BindingContext
import java.io.File
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import kotlin.test.assertNotNull
import java.util.regex.Pattern
import org.jetbrains.kotlin.test.JetTestUtils
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.resolve.constants.IntegerValueConstant
import org.jetbrains.kotlin.resolve.BindingContextUtils
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.psi.JetProperty
import org.jetbrains.kotlin.resolve.constants.CompileTimeConstant

public abstract class AbstractEvaluateExpressionTest : AbstractAnnotationDescriptorResolveTest() {

    // Test directives should look like [// val testedPropertyName: propertyCompileTimeConstant( = initializerCompileTimeConstant)?]
    fun doConstantTest(path: String) {
        doTest(path) {
            property, context ->

            composeTestDirective(property, context) {
                if (it is StringValue) "\\\"${it.getValue()}\\\"" else "$it"
            }
        }
    }

    // Test directives should look like [// val testedPropertyName: propertyCompileTimeConstant( = initializerCompileTimeConstant)?]
    fun doIsPureTest(path: String) {
        doTest(path) {
            property, context ->
            composeTestDirective(property, context) {
                if (it is IntegerValueConstant) it.isPure().toString() else "null"
            }
        }
    }

    // Test directives should look like [// val testedPropertyName: propertyCompileTimeConstant( = initializerCompileTimeConstant)?]
    fun doUsesVariableAsConstantTest(path: String) {
        doTest(path) {
            property, context ->
            composeTestDirective(property, context) {
                if (it != null) it.usesVariableAsConstant().toString() else "null"
            }
        }
    }

    private fun composeTestDirective(
            desc: VariableDescriptor,
            context: BindingContext,
            constantToString: (CompileTimeConstant<*>?) -> String
    ): String {
        val jetProperty = DescriptorToSourceUtils.descriptorToDeclaration(desc) as JetProperty

        val propertyCompileTimeConstant = desc.getCompileTimeInitializer()
        val initializerCompileTimeConstant = context[BindingContext.COMPILE_TIME_VALUE, jetProperty.getInitializer()]

        val propertyConstantString = constantToString(propertyCompileTimeConstant)
        val initializerConstantString = constantToString(initializerCompileTimeConstant)

        if (propertyConstantString == initializerConstantString) {
            return propertyConstantString
        }
        else {
            return "$propertyConstantString = $initializerConstantString"
        }
    }

    private fun doTest(path: String, getValueToTest: (VariableDescriptor, BindingContext) -> String) {
        val myFile = File(path)
        val fileText = FileUtil.loadFile(myFile, true)
        val packageView = getPackage(fileText)

        val propertiesForTest = getObjectsToTest(fileText)

        val expectedActual = arrayListOf<Pair<String, String>>()

        for (propertyName in propertiesForTest) {
            val expectedPropertyPrefix = "// val ${propertyName}: "
            val expected = InTextDirectivesUtils.findStringWithPrefixes(fileText, expectedPropertyPrefix)
            assertNotNull(expected, "Failed to find expected directive: $expectedPropertyPrefix")

            val property = AbstractAnnotationDescriptorResolveTest.getPropertyDescriptor(packageView, propertyName, false)
            ?: AbstractAnnotationDescriptorResolveTest.getLocalVarDescriptor(context!!, propertyName)

            val testedObject = getValueToTest(property, context!!)
            expectedActual.add(expectedPropertyPrefix + expected!! to expectedPropertyPrefix + testedObject)
        }

        var actualFileText = fileText
        for ((expected, actual) in expectedActual) {
            assert(actualFileText.contains(expected), "File text should contains $expected")
            actualFileText = actualFileText.replace(expected, actual)
        }

        JetTestUtils.assertEqualsToFile(myFile, actualFileText)
    }

    fun getObjectsToTest(fileText: String): List<String> {
        return InTextDirectivesUtils.findListWithPrefixes(fileText, "// val").map {
            val matcher = pattern.matcher(it)
            if (matcher.find()) {
                matcher.group(0) ?: "Couldn't match tested object $it"
            } else "Couldn't match tested object $it"
        }
    }

    class object {
        val pattern = Pattern.compile(".+(?=:)")
    }
}
