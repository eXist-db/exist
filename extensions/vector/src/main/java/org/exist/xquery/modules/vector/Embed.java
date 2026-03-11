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
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.Cardinality;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.vector.VectorEmbeddingProvider;
import org.exist.vector.VectorEmbeddingService;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.modules.vector.VectorModule.functionSignature;

/**
 * vector:embed($text, $model, $model-path?) — Embed text at query time for ft:query-vector.
 */
public class Embed extends BasicFunction {

  private static final FunctionParameterSequenceType FS_PARAM_TEXT = param("text", Type.STRING,
      "Text to embed (e.g. user search query).");
  private static final FunctionParameterSequenceType FS_PARAM_MODEL = param("model", Type.STRING,
      "Model identifier (e.g. 'all-MiniLM-L6-v2').");
  private static final FunctionParameterSequenceType FS_PARAM_MODEL_PATH = optParam("model-path", Type.STRING,
      "Optional path to model directory. If omitted, resolves via onnx-models/{model} relative to exist.home.");

  private static final Map<String, Integer> KNOWN_DIMENSIONS = Map.of(
      "all-MiniLM-L6-v2", 384,
      "all-MiniLM-L12-v2", 384,
      "paraphrase-MiniLM-L3-v2", 384
  );

  final static FunctionSignature[] signatures = {
      functionSignature("embed",
          "Embed text for vector search. Returns array of floats for use with ft:query-vector.",
          new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "Array of embedding floats"),
          FS_PARAM_TEXT,
          FS_PARAM_MODEL),
      functionSignature("embed",
          "Embed text with explicit model path.",
          new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "Array of embedding floats"),
          FS_PARAM_TEXT,
          FS_PARAM_MODEL,
          FS_PARAM_MODEL_PATH)
  };

  public Embed(final XQueryContext context, final FunctionSignature signature) {
    super(context, signature);
  }

  @Override
  public Sequence eval(final Sequence[] args, @Nullable final Sequence contextSequence) throws XPathException {
    final String text = args[0].getStringValue();
    final String model = args[1].getStringValue().trim();
    if (model.isEmpty()) {
      throw new XPathException(this, "Model parameter must not be empty");
    }

    final String pathOrUrl;
    if (args.length >= 3 && !args[2].isEmpty()) {
      pathOrUrl = args[2].getStringValue().trim();
    } else {
      pathOrUrl = "onnx-models/" + model;
    }

    final int dimension = KNOWN_DIMENSIONS.getOrDefault(model, 384);

    try {
      final VectorEmbeddingProvider provider = VectorEmbeddingService.getInstance().getProvider(model, pathOrUrl, dimension);
      if (provider == null) {
        throw new XPathException(this, "Failed to load embedding model: " + model + " from " + pathOrUrl);
      }
      final float[] vec = provider.embed(text);
      if (vec == null || vec.length == 0) {
        throw new XPathException(this, "Embedding returned empty result");
      }

      final List<Sequence> items = new ArrayList<>(vec.length);
      for (final float v : vec) {
        items.add(new DoubleValue(this, v).toSequence());
      }
      return new ArrayType(this, context, items);
    } catch (final NoClassDefFoundError e) {
      throw new XPathException(this, "Vector embedding module not available: " + e.getMessage());
    }
  }
}
