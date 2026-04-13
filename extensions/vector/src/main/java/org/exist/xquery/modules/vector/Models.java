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
package org.exist.xquery.modules.vector;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.exist.vector.ModelRegistry;
import org.exist.vector.VectorModelConstants;

import javax.annotation.Nullable;

import static org.exist.xquery.modules.vector.VectorModule.functionSignature;

/**
 * vector:models() — List embedding model identifiers.
 * <p>
 * Returns (1) models from the {@code <vector-models>} section in conf.xml, plus
 * (2) built-in models with known dimensions (ONNX and HTTP API). To use models
 * not in the built-in list, add them to conf.xml:
 * <pre>
 * &lt;vector-models&gt;
 *   &lt;model id="my-model" path="onnx-models/my-model" dimension="384"/&gt;
 * &lt;/vector-models&gt;
 * </pre>
 */
public class Models extends BasicFunction {

  static final FunctionSignature[] signatures = {
      functionSignature("models",
          "Returns a sequence of embedding model identifiers: registry models from conf.xml first, then built-in ONNX and HTTP API models.",
          new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "Model IDs"))
  };

  public Models(final XQueryContext context, final FunctionSignature signature) {
    super(context, signature);
  }

  @Override
  public Sequence eval(final Sequence[] args, @Nullable final Sequence contextSequence) {
    final ValueSequence result = new ValueSequence();
    final java.util.Set<String> registryIds = ModelRegistry.getInstance().getModelIds();
    for (final String model : registryIds) {
      result.add(new StringValue(this, model));
    }
    for (final String model : VectorModelConstants.getKnownModelIds()) {
      if (!registryIds.contains(model)) {
        result.add(new StringValue(this, model));
      }
    }
    return result;
  }
}
