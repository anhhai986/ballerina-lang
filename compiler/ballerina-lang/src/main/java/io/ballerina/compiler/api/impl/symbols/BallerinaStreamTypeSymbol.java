/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
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
package io.ballerina.compiler.api.impl.symbols;

import io.ballerina.compiler.api.ModuleID;
import io.ballerina.compiler.api.symbols.StreamTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.types.BStreamType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.util.CompilerContext;

import java.util.Optional;

/**
 * Represents an stream type descriptor.
 *
 * @since 2.0.0
 */
public class BallerinaStreamTypeSymbol extends AbstractTypeSymbol implements StreamTypeSymbol {

    private TypeSymbol typeParameter;
    private TypeSymbol completionValueTypeParameter;
    private String signature;

    public BallerinaStreamTypeSymbol(CompilerContext context, ModuleID moduleID, BStreamType streamType) {
        super(context, TypeDescKind.STREAM, moduleID, streamType);
    }

    @Override
    public TypeSymbol typeParameter() {
        if (this.typeParameter == null) {
            TypesFactory typesFactory = TypesFactory.getInstance(this.context);
            this.typeParameter = typesFactory.getTypeDescriptor(((BStreamType) this.getBType()).constraint);
        }

        return this.typeParameter;
    }

    @Override
    public Optional<TypeSymbol> completionValueTypeParameter() {
        if (this.completionValueTypeParameter == null) {
            BType completionType = ((BStreamType) this.getBType()).error;

            if (completionType != null) {
                TypesFactory typesFactory = TypesFactory.getInstance(this.context);
                this.completionValueTypeParameter = typesFactory.getTypeDescriptor(completionType);
            }
        }

        return Optional.ofNullable(this.completionValueTypeParameter);
    }

    @Override
    public String signature() {
        if (this.signature == null) {
            StringBuilder sigBuilder = new StringBuilder("stream<");
            sigBuilder.append(this.typeParameter().signature());
            this.completionValueTypeParameter().ifPresent(t -> sigBuilder.append(", ").append(t.signature()));
            sigBuilder.append('>');
            this.signature = sigBuilder.toString();
        }
        return this.signature;
    }
}