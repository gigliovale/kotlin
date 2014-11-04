/*
 * Copyright 2010-2014 JetBrains s.r.o.
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

package org.jetbrains.jet.plugin.decompiler.stubBuilder

import org.jetbrains.jet.descriptors.serialization.PackageData
import org.jetbrains.jet.descriptors.serialization.ProtoBuf
import org.jetbrains.jet.lang.resolve.name.FqName
import org.jetbrains.jet.lang.resolve.name.Name
import org.jetbrains.jet.lang.psi.stubs.KotlinFileStub

public class CompiledPackageClassStubBuilder(packageData: PackageData, packageFqName: FqName) : CompiledStubBuilderBase(packageData.getNameResolver(), packageFqName) {

    private val packageProto: ProtoBuf.Package

    {
        this.packageProto = packageData.getPackageProto()
    }

    public fun createStub(): KotlinFileStub {
        val fileStub = createFileStub(packageFqName)
        for (callableProto in packageProto.getMemberList()) {
            createCallableStub(fileStub, callableProto)
        }
        return fileStub
    }

    override fun getInternalFqName(name: String): FqName? {
        return packageFqName.child(Name.identifier(name))
    }
}
