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

package org.jetbrains.kotlin.android

import com.android.SdkConstants.CLASS_PARCEL
import com.android.SdkConstants.CLASS_PARCELABLE
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ParameterDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeInsight.shorten.addToShorteningWaitSet
import org.jetbrains.kotlin.idea.codeInsight.shorten.performDelayedRefactoringRequests
import org.jetbrains.kotlin.idea.intentions.getLeftMostReceiverExpression
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.search.usagesSearch.propertyDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType


private val CREATOR_NAME = "CREATOR"
private val PARCEL_NAME = "parcel"
private val CREATOR_TEXT =
        "companion object $CREATOR_NAME : android.os.Parcelable.Creator<%1\$s> {\n" +
        "    override fun createFromParcel($PARCEL_NAME: $CLASS_PARCEL): %1\$s {\n" +
        "        return %1\$s($PARCEL_NAME)\n" +
        "    }\n\n" +
        "    override fun newArray(size: Int): Array<%1\$s?> {\n" +
        "        return arrayOfNulls(size)\n" +
        "    }\n" +
        "}"
private val WRITE_TO_PARCEL_TEXT = "override fun writeToParcel($PARCEL_NAME: $CLASS_PARCEL, flags: Int) {\n}"
private val WRITE_TO_PARCEL_SUPER_CALL_TEXT = "super.writeToParcel($PARCEL_NAME, flags)"
private val WRITE_TO_PARCEL_WITH_SUPER_TEXT =
        "override fun writeToParcel($PARCEL_NAME: $CLASS_PARCEL, flags: Int) {\n$WRITE_TO_PARCEL_SUPER_CALL_TEXT\n}"
private val DESCRIBE_CONTENTS_TEXT = "override fun describeContents(): Int {\nreturn 0\n}"
private val CONSTRUCTOR_TEXT = "constructor($PARCEL_NAME: $CLASS_PARCEL)"


fun KtClass.canAddParcelable(): Boolean =
        (!superExtendsParcelable() && findParcelableSupertype() == null)
        || findCreator() == null
        || findConstructorFromParcel() == null
        || findWriteToParcel() == null
        || findDescribeContents() == null

fun KtClass.canRedoParcelable(): Boolean = canRemoveParcelable()

fun KtClass.canRemoveParcelable(): Boolean =
        findParcelableSupertype()
        ?: findCreator()
        ?: findConstructorFromParcel()
        ?: findWriteToParcel()
        ?: findDescribeContents() != null

fun KtClass.implementParcelable(parcelableTypeName: String? = null) {
    val factory = KtPsiFactory(this)
    val superExtendsParcelable = superExtendsParcelable() || parcelableTypeName != null

    val constructor = findOrCreateConstructor(factory)
    findOrCreateParcelableSupertype(factory, parcelableTypeName)

    addFieldReads(constructor, factory)

    val writeToParcel = findOrCreateWriteToParcel(factory, superExtendsParcelable)
    addFieldWrites(writeToParcel, factory, superExtendsParcelable)

    findOrCreateDescribeContents(factory)
    findOrCreateCreator(factory)

    performDelayedRefactoringRequests(project)
    save()
}

fun KtClass.removeParcelableImplementation() {
    findParcelableSupertype()?.let(this::removeSuperTypeListEntry)
    findConstructorFromParcel()?.let {
        if (it is KtPrimaryConstructor) {
            tryDeleteInitBlock()
        }
        it.delete()
    }
    findWriteToParcel()?.delete()
    findDescribeContents()?.delete()
    findCreator()?.delete()
    save()
}

fun KtClass.reimplementParcelable() {
    val parcelableTypeName = findParcelableSupertype()?.typeReference?.takeIf { !it.isParcelableReference() }?.text
    removeParcelableImplementation()
    implementParcelable(parcelableTypeName)
}

private fun KtClass.findCreator(): KtObjectDeclaration? = companionObjects.find { it.name == CREATOR_NAME }

private fun KtClass.findParcelableSupertype(): KtSuperTypeListEntry? = getSuperTypeList()?.findParcelable()

private fun KtSuperTypeList.findParcelable() = entries?.find { it.typeReference?.isParcelableSuccessorReference() ?: false }

private fun KtTypeReference.isParcelableSuccessorReference() =
        analyze(BodyResolveMode.PARTIAL)[BindingContext.TYPE, this]?.isSuccessorOfParcelable() ?: false

private fun KtClass.superExtendsParcelable() = superTypeListEntries.find { it.typeReference?.extendsParcelable() ?: false } != null

private fun KtClass.tryDeleteInitBlock() {
    getAnonymousInitializers().forEach { initializer ->
        val block = initializer.body as? KtBlockExpression
        if (block != null) {
            block.statements.forEach { statement ->
                if (statement.isReadFromParcelPropertyAssignment()) {
                    statement.delete()
                }
            }

            if (block.statements.isEmpty()) {
                initializer.delete()
            }
        }
    }
}

private fun KtExpression.isReadFromParcelPropertyAssignment(): Boolean {
    if (this !is KtBinaryExpression || this.operationToken != KtTokens.EQ) {
        return false
    }

    return this.right?.isReadFromParcel() ?: false
}

private fun KtExpression.isReadFromParcel(): Boolean {
    val reference = firstChild as? KtReferenceExpression
                    ?: (firstChild as? KtDotQualifiedExpression)?.getLeftMostReceiverExpression() as? KtReferenceExpression ?: return false
    val target = reference.analyze(BodyResolveMode.PARTIAL)[BindingContext.REFERENCE_TARGET, reference] ?: return false
    return (target as? ParameterDescriptor)?.type?.fqNameEquals(CLASS_PARCEL) ?: false
}

private fun KtClass.addFieldWrites(function: KtFunction, factory: KtPsiFactory, callSuper: Boolean) {
    val bodyExpression = function.bodyExpression
    if (bodyExpression !is KtBlockExpression || !bodyExpression.isEmptyWriteToParcel(callSuper)) {
        return
    }

    val propertyParameterDescriptors = getPropertyParameterDescriptors()
    val propertyDescriptors = declarations
            .filter { it.isParcelableProperty() }
            .mapNotNull { it.descriptor as? PropertyDescriptor }

    val descriptors = if (propertyParameterDescriptors != null)
        propertyParameterDescriptors + propertyDescriptors
    else
        propertyDescriptors

    val parcelName = function.valueParameters[0].name ?: return
    val flagsName = function.valueParameters[1].name ?: return
    val blockText = descriptors
            .mapNotNull { it.formatWriteToParcel(parcelName, flagsName) }
            .joinToString(separator = "\n")

    val block = factory.createBlock(
            if (callSuper)
                WRITE_TO_PARCEL_SUPER_CALL_TEXT + if (blockText.isNotBlank()) "\n$blockText" else ""
            else blockText
    )

    bodyExpression.replace(block)
}

private fun KtClass.addFieldReads(constructor: KtConstructor<*>, factory: KtPsiFactory) {
    val bodyExpression = constructor.getBodyExpression()
    if (bodyExpression != null && bodyExpression.statements.isNotEmpty()) {
        return
    }

    val parcelName = constructor.getValueParameters().firstOrNull()?.name ?: return
    val parcelableProperties = declarations
            .filter { it.isParcelableProperty() }
            .mapNotNull { it.descriptor as? PropertyDescriptor }

    if (parcelableProperties.isEmpty()) {
        return
    }

    val blockText = parcelableProperties
            .mapNotNull { descriptor -> descriptor.formatReadFromParcel(parcelName)?.let { "${descriptor.name} = $it" } }
            .joinToString(separator = "\n")

    val block = factory.createBlock(blockText)

    if (constructor is KtPrimaryConstructor) {
        val initializer = factory.createAnonymousInitializer()
        initializer.body?.replace(block)
        addDeclaration(initializer).apply {
            addNewLineBeforeDeclaration()
            addToShorteningWaitSet()
        }
    }
    else {
        bodyExpression?.replace(block) ?: constructor.add(block)
    }
}

private fun  KtDeclaration.isParcelableProperty(): Boolean =
        this is KtProperty && !hasDelegate() && !isTransient() && (isVar || (!hasInitializer() && getter == null))

private fun KtProperty.isTransient() = annotationEntries.find { it.isTransient() } != null

private fun KtAnnotationEntry.isTransient(): Boolean =
    typeReference?.analyze(BodyResolveMode.PARTIAL)?.get(BindingContext.TYPE, typeReference)?.fqNameEquals("kotlin.jvm.Transient") ?: false

private fun KtExpression.isCallToSuperWriteToParcel() =
        this is KtDotQualifiedExpression
        && receiverExpression is KtSuperExpression
        && (selectorExpression as? KtCallExpression)?.calleeExpression?.text == "writeToParcel"

private fun KtBlockExpression.isEmptyWriteToParcel(callSuper: Boolean): Boolean =
        if (callSuper) {
            statements.isEmpty() || statements.size == 1 && statements.first().isCallToSuperWriteToParcel()
        }
        else {
            statements.isEmpty()
        }

private fun PropertyDescriptor.formatReadFromParcel(parcelName: String): String? {
    val type = returnType ?: return null
    if (KotlinBuiltIns.isPrimitiveType(type)) {
        return when {
            KotlinBuiltIns.isBoolean(type) -> "$parcelName.readByte() != 0.toByte()"
            KotlinBuiltIns.isShort(type) -> "$parcelName.readInt().toShort()"
            KotlinBuiltIns.isChar(type) -> "$parcelName.readInt().toChar()"
            else -> "$parcelName.read${type.getName()}()"
        }
    }

    return when {
        KotlinBuiltIns.isString(type) -> "$parcelName.readString()"
        type.isSuccessorOfParcelable(true) ->
            "$parcelName.readParcelable(${type.constructor.declarationDescriptor?.fqNameSafe}::class.java.classLoader)"
        type.isArrayOfParcelable() -> "$parcelName.createTypedArray(${type.arguments.single().type.getName()}.CREATOR)"
        type.isListOfParcelable() -> "$parcelName.createTypedArrayList(${type.arguments.single().type.getName()}.CREATOR)"
        else -> null
    }
}

private fun PropertyDescriptor.formatWriteToParcel(parcelName: String, flagsName: String): String? {
    val type = returnType ?: return null

    if (KotlinBuiltIns.isPrimitiveType(type)) {
        return when {
            KotlinBuiltIns.isBoolean(type) -> "$parcelName.writeByte(if ($name)  1 else 0)"
            KotlinBuiltIns.isShort(type) -> "$parcelName.writeInt($name.toInt())"
            KotlinBuiltIns.isChar(type) -> "$parcelName.writeInt($name.toInt())"
            else -> "$parcelName.write${type.getName()}($name)"
        }
    }

    return when {
        KotlinBuiltIns.isString(type) -> "$parcelName.writeString($name)"
        type.isSuccessorOfParcelable(true) -> "$parcelName.writeParcelable($name, $flagsName)"
        type.isArrayOfParcelable() -> "$parcelName.writeTypedArray($name, $flagsName)"
        type.isListOfParcelable() -> "$parcelName.writeTypedList($name)"
        else -> null
    }
}

private fun KtClass.findOrCreateCreator(factory: KtPsiFactory): KtClassOrObject {
    findCreator()?.let {
        return it
    }

    val creator = factory.createObject(CREATOR_TEXT.format(name))
    return addDeclaration(creator).apply { addToShorteningWaitSet() }
}

private fun KtClass.findOrCreateParcelableSupertype(factory: KtPsiFactory, parcelableTypeName: String? = null): KtSuperTypeListEntry? {
    findParcelableSupertype()?.let {
        return it
    }

    fun KtClass.getParcelName() = (findConstructorFromParcel() as? KtPrimaryConstructor)?.valueParameters?.first()?.name ?: PARCEL_NAME

    val supertypeEntry = if (parcelableTypeName != null)
        factory.createSuperTypeCallEntry("$parcelableTypeName(${getParcelName()})")
    else
        factory.createSuperTypeEntry(CLASS_PARCELABLE)

    return addSuperTypeListEntry(supertypeEntry).apply { addToShorteningWaitSet() }
}

private fun KtClass.save() = FileDocumentManager.getInstance().getDocument(containingFile.virtualFile)?.let {
    PsiDocumentManager.getInstance(project).commitDocument(it)
}

private fun KtClass.findOrCreateConstructor(factory: KtPsiFactory): KtConstructor<*> {
    findConstructorFromParcel()?.let {
        return it
    }

    val constructor = if (primaryConstructor == null)
        createPrimaryConstructor(factory)
    else
        createSecondaryConstructor(factory)

    constructor.addToShorteningWaitSet()
    return constructor
}

private fun KtClass.createPrimaryConstructor(factory: KtPsiFactory): KtConstructor<*> {
    val constructor = createPrimaryConstructorIfAbsent()
    constructor.valueParameterList?.apply { addParameter(factory.createParameter("$PARCEL_NAME: $CLASS_PARCEL")) }
    return constructor
}

private fun KtClass.getPropertyParameterDescriptors(): List<PropertyDescriptor>? =
        primaryConstructor
                ?.valueParameters
                ?.takeWhile { it.isPropertyParameter() }
                ?.mapNotNull { it.propertyDescriptor }

private fun KtClass.createSecondaryConstructor(factory: KtPsiFactory): KtConstructor<*> {
    val arguments = getPropertyParameterDescriptors()
            ?.map { it.formatReadFromParcel(PARCEL_NAME) }
            ?.takeWhile { it != null }

    val argumentList = arguments?.joinToString(
            prefix = if (arguments.size > 1) "(\n" else "(",
            postfix = ")",
            separator = if (arguments.size > 1) ",\n" else ", ")

    val constructorText = if (argumentList != null)
        "$CONSTRUCTOR_TEXT :this$argumentList {\n}"
    else
        "$CONSTRUCTOR_TEXT {\n}"

    val constructor =  factory.createSecondaryConstructor(constructorText)
    val lastProperty = declarations.findLast { it is KtProperty }
    if (lastProperty != null) {
        return addDeclarationAfter(constructor, lastProperty).apply { addNewLineBeforeDeclaration() }
    }
    else {
        val firstFunction = declarations.findLast { it is KtFunction }
        return addDeclarationBefore(constructor, firstFunction).apply { addNewLineBeforeDeclaration() }
    }
}

private fun KtTypeReference.extendsParcelable(): Boolean =
    analyze(BodyResolveMode.PARTIAL)[BindingContext.TYPE, this]
            ?.constructor?.supertypes?.find { it.fqNameEquals(CLASS_PARCELABLE) } != null

private fun KtClass.findWriteToParcel() = declarations.find { it.isWriteToParcel() }

private fun KtDeclaration.isWriteToParcel(): Boolean = this is KtFunction && name == "writeToParcel" && valueParameters.let {
    it.size == 2 && it[0].isParcelParameter() && (it[1].typeReference?.fqNameEquals("kotlin.Int") ?: false)
}

private fun KtClass.findOrCreateWriteToParcel(factory: KtPsiFactory, callSuper: Boolean): KtFunction {
    findWriteToParcel()?.let {
        return it as KtFunction
    }

    val writeToParcel = factory.createFunction(if (callSuper) WRITE_TO_PARCEL_WITH_SUPER_TEXT else WRITE_TO_PARCEL_TEXT)
    return addDeclaration(writeToParcel).apply {
        addNewLineBeforeDeclaration()
        addToShorteningWaitSet()
    }
}

private fun KtClass.findDescribeContents() = declarations.find { it.isDescribeContents() }

private fun KtDeclaration.isDescribeContents(): Boolean = this is KtFunction &&
                                                          name == "describeContents" &&
                                                          valueParameters.isEmpty()

private fun KtClass.findOrCreateDescribeContents(factory: KtPsiFactory): KtFunction {
    findDescribeContents()?.let {
        return it as KtFunction
    }

    val describeContents = factory.createFunction(DESCRIBE_CONTENTS_TEXT)
    return addDeclaration(describeContents).apply {
        addNewLineBeforeDeclaration()
        addToShorteningWaitSet()
    }
}

private fun KtClass.findConstructorFromParcel(): KtConstructor<*>? =
    primaryConstructor?.takeIf { it.isConstructorFromParcel() } ?: secondaryConstructors.find { it.isConstructorFromParcel() }

private fun KtConstructor<*>.isConstructorFromParcel(): Boolean = getValueParameters().let {
    it.size == 1 && it.single().isParcelParameter()
}

private fun KtParameter.isParcelParameter(): Boolean = typeReference?.fqNameEquals(CLASS_PARCEL) ?: false

private fun KtTypeReference.isParcelableReference() = fqNameEquals(CLASS_PARCELABLE)

private fun KtTypeReference.fqNameEquals(fqName: String) =
        analyze(BodyResolveMode.PARTIAL)[BindingContext.TYPE, this]?.fqNameEquals(fqName) ?: false

private fun KotlinType.getName() = constructor.declarationDescriptor?.name

private fun KotlinType.fqNameEquals(fqName: String) = constructor.declarationDescriptor?.fqNameSafe?.asString() == fqName

private fun KotlinType.isSuccessorOfParcelable(strict: Boolean = false): Boolean =
        (!strict && fqNameEquals(CLASS_PARCELABLE)) || constructor.supertypes.any { it.isSuccessorOfParcelable(false) }

private fun KotlinType.isArrayOfParcelable(): Boolean =
        KotlinBuiltIns.isArray(this) && arguments.singleOrNull()?.type?.isSuccessorOfParcelable(true) ?: false

private fun KotlinType.isListOfParcelable(): Boolean =
        KotlinBuiltIns.isListOrNullableList(this) && arguments.singleOrNull()?.type?.isSuccessorOfParcelable(true) ?: false

private fun <T: KtDeclaration> T.addNewLineBeforeDeclaration() = parent.addBefore(KtPsiFactory(this).createNewLine(), this)