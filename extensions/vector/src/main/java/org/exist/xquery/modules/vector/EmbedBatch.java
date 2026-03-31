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

import org.exist.vector.VectorEmbeddingProvider;
import org.exist.vector.VectorEmbeddingService;
import org.exist.vector.VectorModelConstants;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.modules.vector.VectorModule.functionSignature;

/**
 * vector:embed-batch($texts, $model, $model-path?) — Embed multiple texts at query time.
 * Returns array of arrays (one embedding per input text) for use with ft:query-vector.
 */
public class EmbedBatch extends BasicFunction {

  private static final FunctionParameterSequenceType FS_PARAM_TEXTS = param("texts", Type.STRING, Cardinality.ZERO_OR_MORE,
      "Texts to embed (e.g. document titles for batch indexing).");
  private static final FunctionParameterSequenceType FS_PARAM_MODEL = param("model", Type.STRING,
      "Model identifier (e.g. 'all-MiniLM-L6-v2').");
  private static final FunctionParameterSequenceType FS_PARAM_MODEL_PATH = optParam("model-path", Type.STRING,
      "Optional path to model directory. If omitted, resolves via onnx-models/{model} or conf.xml registry.");
  private static final FunctionParameterSequenceType FS_PARAM_API_KEY = optParam("api-key", Type.STRING,
      "Optional API key for HTTP APIs (OpenAI, Cohere). Alternative to OPENAI_API_KEY/COHERE_API_KEY env vars.");

  static final FunctionSignature[] signatures = {
      functionSignature("embed-batch",
          "Embed multiple texts for vector search. Returns array of arrays (one embedding per input).",
          new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "Array of embedding arrays"),
          FS_PARAM_TEXTS,
          FS_PARAM_MODEL),
      functionSignature("embed-batch",
          "Embed multiple texts with explicit model path.",
          new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "Array of embedding arrays"),
          FS_PARAM_TEXTS,
          FS_PARAM_MODEL,
          FS_PARAM_MODEL_PATH),
      functionSignature("embed-batch",
          "Embed multiple texts with model path and optional API key (alternative to env vars).",
          new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "Array of embedding arrays"),
          FS_PARAM_TEXTS,
          FS_PARAM_MODEL,
          FS_PARAM_MODEL_PATH,
          FS_PARAM_API_KEY)
  };

  public EmbedBatch(final XQueryContext context, final FunctionSignature signature) {
    super(context, signature);
  }

  @Override
  public Sequence eval(final Sequence[] args, @Nullable final Sequence contextSequence) throws XPathException {
    final Sequence textsSeq = args[0];
    final String model = requireModel(args[1]);
    final String pathOrUrl = optionalArg(args, 2);
    final String apiKey = optionalArg(args, 3);
    final int dimension = VectorModelConstants.getDefaultDimension(model);

    try {
      final VectorEmbeddingProvider provider = VectorEmbeddingService.getInstance().getProvider(model, pathOrUrl, dimension, apiKey);
      if (provider == null) {
        throw new XPathException(this, "Failed to load embedding model: " + model);
      }
      return embedAll(provider, textsSeq);
    } catch (final NoClassDefFoundError e) {
      throw new XPathException(this, "Vector embedding module not available: " + e.getMessage());
    }
  }

  private ArrayType embedAll(final VectorEmbeddingProvider provider, final Sequence textsSeq) throws XPathException {
    final List<Sequence> resultArrays = new ArrayList<>();
    for (final SequenceIterator it = textsSeq.iterate(); it.hasNext(); ) {
      final String text = it.nextItem().getStringValue();
      final float[] vec = provider.embed(text, true);
      if (vec == null || vec.length == 0) {
        throw new XPathException(this, "Embedding returned empty result for text: "
            + (text.length() > 50 ? text.substring(0, 50) + "..." : text));
      }
      resultArrays.add(floatsToArray(vec).toSequence());
    }
    return new ArrayType(this, context, resultArrays);
  }

  private String requireModel(final Sequence arg) throws XPathException {
    final String model = arg.getStringValue().trim();
    if (model.isEmpty()) {
      throw new XPathException(this, "Model parameter must not be empty");
    }
    return model;
  }

  @Nullable
  private static String optionalArg(final Sequence[] args, final int index) throws XPathException {
    if (args.length > index && !args[index].isEmpty()) {
      return args[index].getStringValue().trim();
    }
    return null;
  }

  private ArrayType floatsToArray(final float[] vec) throws XPathException {
    final List<Sequence> items = new ArrayList<>(vec.length);
    for (final float v : vec) {
      items.add(new DoubleValue(this, v).toSequence());
    }
    return new ArrayType(this, context, items);
  }
}
