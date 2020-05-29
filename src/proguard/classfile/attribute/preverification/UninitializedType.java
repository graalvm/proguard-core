/*
 * ProGuardCORE -- library to process Java bytecode.
 *
 * Copyright (c) 2002-2020 Guardsquare NV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package proguard.classfile.attribute.preverification;

import proguard.classfile.*;
import proguard.classfile.attribute.CodeAttribute;
import proguard.classfile.attribute.preverification.visitor.VerificationTypeVisitor;

/**
 * This {@link VerificationType} represents a <code>Uninitialized</code> type.
 *
 * @author Eric Lafortune
 */
public class UninitializedType extends VerificationType
{
    public int u2newInstructionOffset;


    /**
     * Creates an uninitialized UninitializedType.
     */
    public UninitializedType()
    {
    }


    /**
     * Creates an UninitializedType pointing to the given 'new' instruction.
     */
    public UninitializedType(int u2newInstructionOffset)
    {
        this.u2newInstructionOffset = u2newInstructionOffset;
    }


    // Implementations for VerificationType.

    public int getTag()
    {
        return UNINITIALIZED_TYPE;
    }


    public void accept(Clazz clazz, Method method, CodeAttribute codeAttribute, int instructionOffset, VerificationTypeVisitor verificationTypeVisitor)
    {
        verificationTypeVisitor.visitUninitializedType(clazz, method, codeAttribute, instructionOffset, this);
    }


    public void stackAccept(Clazz clazz, Method method, CodeAttribute codeAttribute, int instructionOffset, int stackIndex, VerificationTypeVisitor verificationTypeVisitor)
    {
        verificationTypeVisitor.visitStackUninitializedType(clazz, method, codeAttribute, instructionOffset, stackIndex, this);
    }


    public void variablesAccept(Clazz clazz, Method method, CodeAttribute codeAttribute, int instructionOffset, int variableIndex, VerificationTypeVisitor verificationTypeVisitor)
    {
        verificationTypeVisitor.visitVariablesUninitializedType(clazz, method, codeAttribute, instructionOffset, variableIndex, this);
    }


    // Implementations for Object.

    public boolean equals(Object object)
    {
        if (!super.equals(object))
        {
            return false;
        }

        UninitializedType other = (UninitializedType)object;

        return this.u2newInstructionOffset == other.u2newInstructionOffset;
    }


    public int hashCode()
    {
        return super.hashCode() ^
               u2newInstructionOffset;
    }


    public String toString()
    {
        return "u:" + u2newInstructionOffset;
    }
}
