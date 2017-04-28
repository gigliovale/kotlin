/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.psi2ir.generators

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.SourceManager
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.symbols.impl.*

class SymbolTable {
    private abstract class SymbolTableBase<D : DeclarationDescriptor, B : IrSymbolOwner, S : IrBindableSymbol<D, B>> {
        val unboundSymbols = linkedSetOf<S>()

        protected abstract fun get(d: D): S?
        protected abstract fun set(d: D, s: S)

        fun markAsUnbound(s: S) {
            assert(unboundSymbols.add(s)) {
                "Symbol for ${s.descriptor} was already referenced"
            }
        }

        fun markAsBound(s: S) {
            unboundSymbols.remove(s)
        }

        inline fun declare(d: D, createSymbol: () -> S, createOwner: (S) -> B): B {
            val symbol = getOrCreateSymbol(d, createSymbol)
            val owner = createOwner(symbol)
            symbol.bind(owner)
            return owner
        }

        inline fun getOrCreateSymbol(d: D, createSymbol: () -> S): S {
            val existing = get(d)
            return if (existing == null) {
                val new = createSymbol()
                set(d, new)
                new
            }
            else {
                markAsBound(existing)
                existing
            }
        }

        inline fun referenced(d: D, createSymbol: () -> S): S {
            val s = get(d)
            if (s == null) {
                val new = createSymbol().also { markAsUnbound(it) }
                return new
            }
            return s
        }
    }

    private class FlatSymbolTable<D : DeclarationDescriptor, B : IrSymbolOwner, S : IrBindableSymbol<D, B>>
        : SymbolTableBase<D, B, S>()
    {
        val descriptorToSymbol = linkedMapOf<D, S>()

        override fun get(d: D): S? = descriptorToSymbol[d]

        override fun set(d: D, s: S) {
            descriptorToSymbol[d] = s
        }
    }

    private class ScopedSymbolTable<D : DeclarationDescriptor, B : IrSymbolOwner, S : IrBindableSymbol<D, B>>
        : SymbolTableBase<D, B, S>()
    {
        inner class Scope(val owner: DeclarationDescriptor, val parent: Scope?) {
            private val descriptorToSymbol = linkedMapOf<D, S>()

            operator fun get(d: D): S? =
                    descriptorToSymbol[d] ?: parent?.get(d)

            fun getLocal(d: D) = descriptorToSymbol[d]

            operator fun set(d: D, s: S) {
                descriptorToSymbol[d] = s
            }
        }

        private var currentScope: Scope? = null

        override fun get(d: D): S? {
            val scope = currentScope ?: throw AssertionError("No active scope")
            return scope[d]
        }
        override fun set(d: D, s: S) {
            val scope = currentScope ?: throw AssertionError("No active scope")
            scope[d] = s
        }

        inline fun declareLocal(d: D, createSymbol: () -> S, createOwner: (S) -> B): B {
            val scope = currentScope ?: throw AssertionError("No active scope")
            val symbol = scope.getLocal(d) ?: createSymbol().also { scope[d] = it }
            val owner = createOwner(symbol)
            symbol.bind(owner)
            return owner
        }

        fun enterScope(owner: DeclarationDescriptor) {
            currentScope = Scope(owner, currentScope)
        }

        fun leaveScope(owner: DeclarationDescriptor) {
            currentScope?.owner.let {
                assert(it == owner) { "Unexpected leaveScope: owner=$owner, currentScope.owner=$it" }
            }

            currentScope = currentScope?.parent

            if (currentScope != null && unboundSymbols.isNotEmpty()) {
                throw AssertionError("")
            }
        }
    }

    private val classSymbolTable = FlatSymbolTable<ClassDescriptor, IrClass, IrClassSymbol>()
    private val constructorSymbolTable = FlatSymbolTable<ClassConstructorDescriptor, IrConstructor, IrConstructorSymbol>()
    private val enumEntrySymbolTable = FlatSymbolTable<ClassDescriptor, IrEnumEntry, IrEnumEntrySymbol>()
    private val fieldSymbolTable = FlatSymbolTable<PropertyDescriptor, IrField, IrFieldSymbol>()
    private val simpleFunctionSymbolTable = FlatSymbolTable<FunctionDescriptor, IrSimpleFunction, IrSimpleFunctionSymbol>()

    private val typeParameterSymbolTable = ScopedSymbolTable<TypeParameterDescriptor, IrTypeParameter, IrTypeParameterSymbol>()
    private val valueParameterSymbolTable = ScopedSymbolTable<ParameterDescriptor, IrValueParameter, IrValueParameterSymbol>()
    private val variableSymbolTable = ScopedSymbolTable<VariableDescriptor, IrVariable, IrVariableSymbol>()
    private val scopedSymbolTables = listOf(typeParameterSymbolTable, valueParameterSymbolTable, variableSymbolTable)

    fun declareFile(fileEntry: SourceManager.FileEntry, packageFragmentDescriptor: PackageFragmentDescriptor): IrFile =
            IrFileImpl(
                    fileEntry, packageFragmentDescriptor,
                    IrFileSymbolImpl(packageFragmentDescriptor)
            )

    fun declareAnonymousInitializer(startOffset: Int, endOffset: Int, origin: IrDeclarationOrigin, descriptor: ClassDescriptor): IrAnonymousInitializer =
            IrAnonymousInitializerImpl(
                    startOffset, endOffset, origin, descriptor,
                    IrAnonymousInitializerSymbolImpl(descriptor)
            )

    fun declareClass(startOffset: Int, endOffset: Int, origin: IrDeclarationOrigin, descriptor: ClassDescriptor): IrClass =
            classSymbolTable.declare(
                    descriptor,
                    { IrClassSymbolImpl(descriptor) },
                    { IrClassImpl(startOffset, endOffset, origin, descriptor, it) }
            )


    fun referenceClass(descriptor: ClassDescriptor) =
            classSymbolTable.referenced(descriptor) { IrClassSymbolImpl(descriptor) }

    val unboundClasses: Set<IrClassSymbol> get() = classSymbolTable.unboundSymbols

    fun declareConstructor(startOffset: Int, endOffset: Int, origin: IrDeclarationOrigin, descriptor: ClassConstructorDescriptor): IrConstructor =
            constructorSymbolTable.declare(
                    descriptor,
                    { IrConstructorSymbolImpl(descriptor) },
                    { IrConstructorImpl(startOffset, endOffset, origin, descriptor, it) }
            )

    fun referenceConstructor(descriptor: ClassConstructorDescriptor) =
            constructorSymbolTable.referenced(descriptor) { IrConstructorSymbolImpl(descriptor) }

    val unboundConstructors: Set<IrConstructorSymbol> get() = constructorSymbolTable.unboundSymbols

    fun declareEnumEntry(startOffset: Int, endOffset: Int, origin: IrDeclarationOrigin, descriptor: ClassDescriptor): IrEnumEntry =
            enumEntrySymbolTable.declare(
                    descriptor,
                    { IrEnumEntrySymbolImpl(descriptor) },
                    { IrEnumEntryImpl(startOffset, endOffset, origin, descriptor, it) }
            )

    fun referenceEnumEntry(descriptor: ClassDescriptor) =
            enumEntrySymbolTable.referenced(descriptor) { IrEnumEntrySymbolImpl(descriptor) }

    val unboundEnumEntries: Set<IrEnumEntrySymbol> get() = enumEntrySymbolTable.unboundSymbols

    fun declareField(startOffset: Int, endOffset: Int, origin: IrDeclarationOrigin, descriptor: PropertyDescriptor): IrField =
            fieldSymbolTable.declare(
                    descriptor,
                    { IrFieldSymbolImpl(descriptor) },
                    { IrFieldImpl(startOffset, endOffset, origin, descriptor, it) }
            )

    fun declareField(startOffset: Int, endOffset: Int, origin: IrDeclarationOrigin, descriptor: PropertyDescriptor,
                     irInitializer: IrExpressionBody?) : IrField =
            declareField(startOffset, endOffset, origin, descriptor).apply { initializer = irInitializer }

    fun referenceField(descriptor: PropertyDescriptor) =
            fieldSymbolTable.referenced(descriptor) { IrFieldSymbolImpl(descriptor) }

    val unboundFields: Set<IrFieldSymbol> get() = fieldSymbolTable.unboundSymbols

    fun declareSimpleFunction(startOffset: Int, endOffset: Int, origin: IrDeclarationOrigin, descriptor: FunctionDescriptor): IrSimpleFunction =
            simpleFunctionSymbolTable.declare(
                    descriptor,
                    { IrSimpleFunctionSymbolImpl(descriptor) },
                    { IrFunctionImpl(startOffset, endOffset, origin, descriptor, it) }
            )

    fun referenceSimpleFunction(descriptor: FunctionDescriptor) =
            simpleFunctionSymbolTable.referenced(descriptor) { IrSimpleFunctionSymbolImpl(descriptor) }

    val unboundSimpleFunctions: Set<IrSimpleFunctionSymbol> get() = simpleFunctionSymbolTable.unboundSymbols

    fun declareTypeParameter(startOffset: Int, endOffset: Int, origin: IrDeclarationOrigin, descriptor: TypeParameterDescriptor) : IrTypeParameter =
            typeParameterSymbolTable.declareLocal(
                    descriptor,
                    { IrTypeParameterSymbolImpl(descriptor) },
                    { IrTypeParameterImpl(startOffset, endOffset, origin, descriptor, it) }
            )

    fun referenceTypeParameter(descriptor: TypeParameterDescriptor) =
            typeParameterSymbolTable.referenced(descriptor) { IrTypeParameterSymbolImpl(descriptor) }

    val unboundTypeParameters: Set<IrTypeParameterSymbol> get() = typeParameterSymbolTable.unboundSymbols

    fun declareValueParameter(startOffset: Int, endOffset: Int, origin: IrDeclarationOrigin, descriptor: ParameterDescriptor): IrValueParameter =
            valueParameterSymbolTable.declareLocal(
                    descriptor,
                    { IrValueParameterSymbolImpl(descriptor) },
                    { IrValueParameterImpl(startOffset, endOffset, origin, descriptor, it) }
            )

    fun referenceValueParameter(descriptor: ParameterDescriptor) =
            valueParameterSymbolTable.referenced(descriptor) { IrValueParameterSymbolImpl(descriptor) }

    val unboundValueParameters: Set<IrValueParameterSymbol> get() = valueParameterSymbolTable.unboundSymbols

    fun declareVariable(startOffset: Int, endOffset: Int, origin: IrDeclarationOrigin, descriptor: VariableDescriptor): IrVariable =
            variableSymbolTable.declareLocal(
                    descriptor,
                    { IrVariableSymbolImpl(descriptor) },
                    { IrVariableImpl(startOffset, endOffset, origin, descriptor, it) }
            )

    fun declareVariable(startOffset: Int, endOffset: Int, origin: IrDeclarationOrigin, descriptor: VariableDescriptor,
                        irInitializerExpression: IrExpression?
    ): IrVariable =
            declareVariable(startOffset, endOffset, origin, descriptor).apply {
                initializer = irInitializerExpression
            }

    fun referenceVariable(descriptor: VariableDescriptor) =
            variableSymbolTable.referenced(descriptor) { IrVariableSymbolImpl(descriptor) }

    val unboundVariables: Set<IrVariableSymbol> get() = variableSymbolTable.unboundSymbols

    fun enterScope(owner: DeclarationDescriptor) {
        scopedSymbolTables.forEach { it.enterScope(owner) }
    }

    fun leaveScope(owner: DeclarationDescriptor) {
        scopedSymbolTables.forEach { it.leaveScope(owner) }
    }
}

inline fun <T> SymbolTable.withScope(owner: DeclarationDescriptor, block: () -> T): T {
    enterScope(owner)
    val result = block()
    leaveScope(owner)
    return result
}