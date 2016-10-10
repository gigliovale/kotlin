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

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.jvm.FileLoweringPass
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer

class ClassContext(val irClass: IrClass, val parent: ClassContext?)

abstract class ClassLowerWithContext : FileLoweringPass, IrElementTransformer<ClassContext?> {

    private val companion2Context = mapOf<ClassDescriptor, ClassContext>()

    override fun lower(irFile: IrFile) {
        irFile.accept(this, null)
    }

    override fun visitClass(declaration: IrClass, data: ClassContext?): IrStatement {
        val context = ClassContext(declaration, data)
        lowerBefore(declaration, context)
        val clazz = super.visitClass(declaration, context)
        lower(declaration, context)
        return clazz
    }

    open fun lowerBefore(irCLass: IrClass, context: ClassContext) {}

    abstract fun lower(irCLass: IrClass, context: ClassContext)

    fun findCompanionFor(irClass: IrClass, descriptor: ClassDescriptor): ClassContext {
        val companion = companion2Context[descriptor] ?: throw RuntimeException("Can't find companion object for $irClass by $descriptor")
        assert(irClass == companion.parent?.irClass) {"Wrong companion object: $irClass != ${companion.parent?.irClass}"}
        return companion
    }
}