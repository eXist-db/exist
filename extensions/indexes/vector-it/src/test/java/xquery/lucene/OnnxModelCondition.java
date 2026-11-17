/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package xquery.lucene;

import org.exist.util.ConfigurationHelper;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Enables a test only when the local ONNX model is present, see {@link RequiresOnnxModel}.
 */
final class OnnxModelCondition implements ExecutionCondition {

    static final String REQUIRED_PROPERTY = "exist.vector.model.required";

    static final String MODEL_PATH = "target/onnx-models/all-MiniLM-L6-v2";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
        final Path modelDir = ConfigurationHelper.getExistHome()
            .orElse(Path.of(System.getProperty("user.dir", ".")))
            .resolve(MODEL_PATH);
        if (Files.isRegularFile(modelDir.resolve("model.onnx")) && Files.isRegularFile(modelDir.resolve("tokenizer.json"))) {
            return ConditionEvaluationResult.enabled("ONNX model found in " + modelDir);
        }

        final String reason = "ONNX model not found in " + modelDir + " - run with -Ponnx-model";
        if (Boolean.getBoolean(REQUIRED_PROPERTY)) {
            throw new ExtensionConfigurationException(reason + " (required, " + REQUIRED_PROPERTY + " is set)");
        }
        return ConditionEvaluationResult.disabled(reason);
    }
}
