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

import java.util.List;
import java.util.Map;

/**
 * Centralized model IDs and dimensions for built-in embedding models (ONNX and HTTP API).
 * Used by Embed, EmbedBatch, Models, and HttpVectorProvider to avoid drift.
 */
public final class VectorModelConstants {

  /** OpenAI embedding models. */
  private static final Map<String, Integer> OPENAI_DIMENSIONS = Map.of(
      "text-embedding-ada-002", 1536,
      "text-embedding-3-small", 1536,
      "text-embedding-3-large", 3072
  );

  /** Cohere embedding models. */
  private static final Map<String, Integer> COHERE_DIMENSIONS = Map.of(
      "embed-english-v3.0", 1024,
      "embed-multilingual-v3.0", 1024,
      "embed-english-light-v3.0", 384,
      "embed-multilingual-light-v3.0", 384
  );

  /** Combined map: all built-in models with known dimensions. */
  private static final Map<String, Integer> KNOWN_DIMENSIONS = Map.ofEntries(
      Map.entry("all-MiniLM-L6-v2", 384),
      Map.entry("all-MiniLM-L12-v2", 384),
      Map.entry("paraphrase-MiniLM-L3-v2", 384),
      Map.entry("text-embedding-ada-002", 1536),
      Map.entry("text-embedding-3-small", 1536),
      Map.entry("text-embedding-3-large", 3072),
      Map.entry("embed-english-v3.0", 1024),
      Map.entry("embed-multilingual-v3.0", 1024),
      Map.entry("embed-english-light-v3.0", 384),
      Map.entry("embed-multilingual-light-v3.0", 384)
  );

  /** Built-in model IDs (for vector:models when not in registry). */
  public static final List<String> KNOWN_MODEL_IDS = List.of(
      "all-MiniLM-L6-v2",
      "all-MiniLM-L12-v2",
      "paraphrase-MiniLM-L3-v2",
      "text-embedding-ada-002",
      "text-embedding-3-small",
      "text-embedding-3-large",
      "embed-english-v3.0",
      "embed-multilingual-v3.0",
      "embed-english-light-v3.0",
      "embed-multilingual-light-v3.0"
  );

  private VectorModelConstants() {
  }

  /**
   * Returns the default dimension for a model ID, or 384 if unknown.
   */
  public static int getDefaultDimension(final String modelId) {
    return KNOWN_DIMENSIONS.getOrDefault(modelId, 384);
  }

  /**
   * Returns the default dimension for an OpenAI model, or 1536 if unknown.
   */
  public static int getOpenAIDefaultDimension(final String modelId) {
    return OPENAI_DIMENSIONS.getOrDefault(modelId, 1536);
  }

  /**
   * Returns the default dimension for a Cohere model, or 1024 if unknown.
   */
  public static int getCohereDefaultDimension(final String modelId) {
    return COHERE_DIMENSIONS.getOrDefault(modelId, 1024);
  }

  /**
   * Returns the immutable, ordered list of built-in model IDs.
   */
  public static List<String> getKnownModelIds() {
    return KNOWN_MODEL_IDS;
  }
}
