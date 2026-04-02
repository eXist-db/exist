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
package org.exist.vector;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * ONNX-based vector embedding provider. Uses HuggingFace tokenizer and ONNX Runtime.
 */
public final class OnnxVectorProvider implements VectorEmbeddingProvider {

  private static final Logger LOG = LogManager.getLogger(OnnxVectorProvider.class);

  private static final String MODEL_FILE = "model.onnx";
  private static final String TOKENIZER_FILE = "tokenizer.json";

  private static final String INPUT_IDS = "input_ids";
  private static final String ATTENTION_MASK = "attention_mask";
  private static final String TOKEN_TYPE_IDS = "token_type_ids";
  private static final String LAST_HIDDEN_STATE = "last_hidden_state";
  private static final String SENTENCE_EMBEDDING = "sentence_embedding";
  private static final String TOKEN_EMBEDDINGS = "token_embeddings";

  private final OrtEnvironment env;
  private final OrtSession session;
  private final HuggingFaceTokenizer tokenizer;
  private final int dimension;
  private final String outputName;
  private final String tokenTypeIdsInputName;

  private OnnxVectorProvider(final OrtEnvironment env, final OrtSession session,
      final HuggingFaceTokenizer tokenizer, final int dimension, final String outputName,
      final String tokenTypeIdsInputName) {
    this.env = env;
    this.session = session;
    this.tokenizer = tokenizer;
    this.dimension = dimension;
    this.outputName = outputName;
    this.tokenTypeIdsInputName = tokenTypeIdsInputName;
  }

  @Override
  public int getDimension() {
    return dimension;
  }

  @Override
  @Nullable
  public float[] embed(final String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      final Encoding encoding = tokenizer.encode(text);
      if (encoding == null) {
        return null;
      }
      final long[] ids = encoding.getIds();
      if (ids == null || ids.length == 0) {
        return null;
      }

      final Map<String, OnnxTensor> inputs = prepareInputs(encoding);
      try (Result result = session.run(inputs)) {
        if (SENTENCE_EMBEDDING.equals(outputName)) {
          return extractSentenceEmbedding(result);
        }
        final long[] attnMask = encoding.getAttentionMask();
        final long[] mask = attnMask != null ? attnMask : createAttentionMask(ids.length);
        return meanPool(result, ids.length, mask);
      } finally {
        inputs.values().forEach(OnnxTensor::close);
      }
    } catch (final Exception e) {
      LOG.debug("Embedding failed: {}", e.getMessage());
      return null;
    }
  }

  private Map<String, OnnxTensor> prepareInputs(final Encoding encoding) throws ai.onnxruntime.OrtException {
    final long[] ids = encoding.getIds();
    final long[] attnMask = encoding.getAttentionMask();
    final long[][] attentionMask = attnMask != null ? new long[][] { attnMask } : new long[][] { createAttentionMask(ids.length) };

    final Map<String, OnnxTensor> inputs = new HashMap<>();
    inputs.put(INPUT_IDS, OnnxTensor.createTensor(env, new long[][] { ids }));
    inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(env, attentionMask));
    if (tokenTypeIdsInputName != null) {
      inputs.put(tokenTypeIdsInputName, OnnxTensor.createTensor(env, new long[][] { createTokenTypeIds(ids.length) }));
    }
    return inputs;
  }

  private float[] extractSentenceEmbedding(final Result result) throws Exception {
    try (final OnnxTensor tensor = (OnnxTensor) result.get(0)) {
      final float[][] data = (float[][]) tensor.getValue();
      if (data != null && data.length > 0 && data[0].length == dimension) {
        return data[0];
      }
    }
    return null;
  }

  private static long[] createAttentionMask(final int len) {
    final long[] mask = new long[len];
    for (int i = 0; i < len; i++) {
      mask[i] = 1;
    }
    return mask;
  }

  private static long[] createTokenTypeIds(final int len) {
    final long[] ids = new long[len];
    for (int i = 0; i < len; i++) {
      ids[i] = 0;
    }
    return ids;
  }

  private float[] meanPool(final Result result, final int seqLen, final long[] attentionMask) throws Exception {
    try (final OnnxTensor tensor = findHiddenStateTensor(result)) {
      final float[][][] hidden = (float[][][]) tensor.getValue();
      if (hidden == null || hidden.length == 0 || hidden[0].length == 0 || hidden[0][0].length != dimension) {
        return null;
      }
      return computeMeanPool(hidden[0], seqLen, attentionMask);
    }
  }

  private OnnxTensor findHiddenStateTensor(final Result result) {
    for (final Map.Entry<String, OnnxValue> entry : result) {
      if (LAST_HIDDEN_STATE.equals(entry.getKey()) || TOKEN_EMBEDDINGS.equals(entry.getKey())) {
        return (OnnxTensor) entry.getValue();
      }
    }
    return (OnnxTensor) result.get(0);
  }

  @Nullable
  private float[] computeMeanPool(final float[][] tokenEmbeddings, final int seqLen, final long[] attentionMask) {
    final float[] sum = new float[dimension];
    int count = 0;
    for (int t = 0; t < seqLen && t < tokenEmbeddings.length; t++) {
      if (t < attentionMask.length && attentionMask[t] == 1) {
        for (int d = 0; d < dimension; d++) {
          sum[d] += tokenEmbeddings[t][d];
        }
        count++;
      }
    }
    if (count == 0) {
      return null;
    }
    for (int d = 0; d < dimension; d++) {
      sum[d] /= count;
    }
    return sum;
  }

  /**
   * Creates an ONNX provider from a model directory. Expects model.onnx and tokenizer.json.
   *
   * @param modelPath path to the model directory
   * @param dimension expected embedding dimension
   * @return provider or null if loading fails
   */
  @Nullable
  public static OnnxVectorProvider create(@Nonnull final Path modelPath, final int dimension) {
    final Path modelFile = modelPath.resolve(MODEL_FILE);
    final Path tokenizerFile = modelPath.resolve(TOKENIZER_FILE);
    if (!Files.isRegularFile(modelFile) || !Files.isRegularFile(tokenizerFile)) {
      LOG.warn("Model files not found: {} or {}", modelFile, tokenizerFile);
      return null;
    }

    try {
      final HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile);
      final OrtEnvironment env = OrtEnvironment.getEnvironment();
      final OrtSession session = createSession(env, modelFile);
      final String tokenTypeIdsInputName = resolveTokenTypeIdsInput(session);
      final String outputName = resolveOutputName(session);

      return new OnnxVectorProvider(env, session, tokenizer, dimension, outputName, tokenTypeIdsInputName);
    } catch (final Exception e) {
      LOG.warn("Failed to create OnnxVectorProvider: {}", e.getMessage());
      return null;
    }
  }

  private static OrtSession createSession(final OrtEnvironment env, final Path modelFile) throws Exception {
    final OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
    try {
      opts.addCUDA(0);
      LOG.info("Using CUDA execution provider for embedding (device 0)");
    } catch (final Throwable t) {
      LOG.debug("CUDA not available, using CPU: {}", t.getMessage());
    }
    return env.createSession(modelFile.toString(), opts);
  }

  @Nullable
  private static String resolveTokenTypeIdsInput(final OrtSession session) throws Exception {
    final Set<String> inputNames = session.getInputNames();
    if (LOG.isDebugEnabled()) {
      LOG.debug("ONNX model input names: {}", inputNames);
    }
    for (final String name : inputNames) {
      if (TOKEN_TYPE_IDS.equals(name) || (name != null && name.endsWith("token_type_ids"))) {
        return name;
      }
    }
    return inputNames.size() >= 3 ? TOKEN_TYPE_IDS : null;
  }

  private static String resolveOutputName(final OrtSession session) throws Exception {
    for (final String name : session.getOutputNames()) {
      if (SENTENCE_EMBEDDING.equals(name) || TOKEN_EMBEDDINGS.equals(name)) {
        return name;
      }
      if (LAST_HIDDEN_STATE.equals(name)) {
        return LAST_HIDDEN_STATE;
      }
    }
    return SENTENCE_EMBEDDING;
  }
}
