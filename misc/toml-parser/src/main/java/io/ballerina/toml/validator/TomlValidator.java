/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.toml.validator;

import io.ballerina.toml.api.Toml;
import io.ballerina.toml.semantic.TomlType;
import io.ballerina.toml.semantic.ast.TomlTableArrayNode;
import io.ballerina.toml.semantic.ast.TomlTableNode;
import io.ballerina.toml.semantic.ast.TopLevelNode;
import io.ballerina.toml.validator.validations.AdditionalPropertiesVisitor;
import io.ballerina.toml.validator.validations.TypeCheckerVisitor;
import io.ballerina.tools.diagnostics.Diagnostic;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contains the validation logic for AdditionalProperties in the JSON schema.
 *
 * @since 2.0.0
 */
public class TomlValidator {

    private final Schema rootSchema;

    public TomlValidator(Schema rootSchema) {
        this.rootSchema = rootSchema;
    }

    public void validate(Toml toml) {
        Map<String, Schema> rootSchemaProperties = rootSchema.getProperties();
        processProperties(toml.getDiagnostics(), rootSchemaProperties, toml.getRootNode());
    }

    private void processProperties(Set<Diagnostic> toml, Map<String, Schema> schema, TomlTableNode tableNode) {
        Set<Map.Entry<String, Schema>> entries = schema.entrySet();
        for (Map.Entry<String, Schema> entry : entries) {
            String key = entry.getKey();
            Schema value = entry.getValue();

            TopLevelNode topLevelNode = tableNode.children().get(key);
            if (topLevelNode != null) {
                AdditionalPropertiesVisitor additionalPropertiesVisitor = new AdditionalPropertiesVisitor(toml, value);
                topLevelNode.accept(additionalPropertiesVisitor);

                TypeCheckerVisitor typeCheckerVisitor = new TypeCheckerVisitor(toml, value, key);
                topLevelNode.accept(typeCheckerVisitor);
            }

            if (value.getType().equals("object")) {
                if (topLevelNode != null && topLevelNode.kind() == TomlType.TABLE) {
                    processProperties(toml, value.getProperties(), (TomlTableNode) topLevelNode);
                }
            } else if (value.getType().equals("array")) {
                if (topLevelNode != null && topLevelNode.kind() == TomlType.TABLE_ARRAY) {
                    Schema items = value.getItems();
                    TomlTableArrayNode tableArrayNode = (TomlTableArrayNode) topLevelNode;
                    List<TomlTableNode> children = tableArrayNode.children();
                    for (TomlTableNode child : children) {
                        processProperties(toml, items.getProperties(), child);
                    }
                }
            }
        }
    }
}
