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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton service that provides {@link VectorEmbeddingProvider} instances by model ID.
 * Loads providers lazily and caches them.
 */
public final class VectorEmbeddingService {

  private static final Logger LOG = LogManager.getLogger(VectorEmbeddingService.class);

  private static volatile VectorEmbeddingService instance;

  private final Map<String, VectorEmbeddingProvider> cache = new ConcurrentHashMap<>();

  private VectorEmbeddingService() {
  }

  /**
   * Returns the singleton instance.
   */
  @Nonnull
  public static VectorEmbeddingService getInstance() {
    VectorEmbeddingService s = instance;
    if (s == null) {
      synchronized (VectorEmbeddingService.class) {
        s = instance;
        if (s == null) {
          s = instance = new VectorEmbeddingService();
        }
      }
    }
    return s;
  }

  /**
   * Returns a provider for the given model ID. Delegates to
   * {@link #getProvider(String, String, int, String)} with null apiKey.
   */
  @Nullable
  public VectorEmbeddingProvider getProvider(@Nonnull final String modelId,
      @Nullable final String pathOrUrl,
      final int dimension) {
    return getProvider(modelId, pathOrUrl, dimension, null);
  }

  /**
   * Returns a provider for the given model ID. For HTTP API URLs (OpenAI, Cohere),
   * creates an HttpVectorProvider. Otherwise resolves path or URL (downloads from
   * HuggingFace on first use) and creates an ONNX provider.
   *
   * @param modelId    model identifier (e.g. "all-MiniLM-L6-v2", "text-embedding-3-small")
   * @param pathOrUrl  local path, HuggingFace base URL, or API base URL; null or empty to use
   *                   conf.xml registry or default "onnx-models/{modelId}"
   * @param dimension  expected embedding dimension (for validation)
   * @param apiKey     optional API key for HTTP APIs; when non-null, used instead of env vars
   *                   (provider is not cached for security)
   * @return provider, or null if the model could not be loaded
   */
  @Nullable
  public VectorEmbeddingProvider getProvider(@Nonnull final String modelId,
      @Nullable final String pathOrUrl,
      final int dimension,
      @Nullable final String apiKey) {
    final ModelRegistry.Resolved resolved = ModelRegistry.getInstance().resolve(modelId, pathOrUrl, dimension);
    final String path = resolved.path;
    final int dim = resolved.dimension;
    if (HttpVectorProvider.isHttpApiUrl(path)) {
      final boolean useCache = apiKey == null || apiKey.isEmpty();
      if (useCache) {
        final String cacheKey = httpCacheKey(modelId, path);
        final VectorEmbeddingProvider cached = cache.get(cacheKey);
        if (cached != null) return cached;
      }
      final VectorEmbeddingProvider p = HttpVectorProvider.create(modelId, path, apiKey, dim);
      if (p != null) {
        if (useCache) {
          final String cacheKey = httpCacheKey(modelId, path);
          cache.put(cacheKey, p);
          LOG.info("Loaded HTTP embedding provider: {} from {}", modelId, path);
        }
        return p;
      }
      return null;
    }
    final Path modelPath = ModelPathResolver.resolve(modelId, path);
    if (modelPath == null) {
      LOG.warn("Model '{}' could not be resolved (path='{}'). Ensure the directory exists under exist.home and contains model.onnx and tokenizer.json, or configure <vector-models> in conf.xml, or use an absolute path or URL.",
          modelId, path);
      return null;
    }
    return getProviderByPath(modelId, modelPath, dim);
  }

  /**
   * Returns a provider for the given model ID and resolved path.
   *
   * @param modelId   model identifier
   * @param modelPath path to the model directory containing model.onnx and tokenizer.json
   * @param dimension expected embedding dimension
   * @return provider, or null if the model could not be loaded
   */
  @Nullable
  public VectorEmbeddingProvider getProviderByPath(@Nonnull final String modelId,
      @Nonnull final Path modelPath,
      final int dimension) {
    final String cacheKey = modelId + ":" + modelPath.toAbsolutePath();
    return cache.computeIfAbsent(cacheKey, k -> {
      try {
        final VectorEmbeddingProvider p = OnnxVectorProvider.create(modelPath, dimension);
        if (p != null) {
          LOG.info("Loaded ONNX embedding model: {} from {}", modelId, modelPath);
          return p;
        }
      } catch (final Exception e) {
        LOG.warn("Failed to load ONNX model {} from {}: {}", modelId, modelPath, e.getMessage());
      }
      return null;
    });
  }

  @Nonnull
  private static String httpCacheKey(@Nonnull final String modelId, @Nonnull final String path) {
    return "http:" + modelId + ":" + path;
  }

  /**
   * Removes a provider from the cache (e.g. when config changes).
   *
   * @param modelId   model identifier
   * @param modelPath path used when the provider was created
   */
  public void evict(@Nonnull final String modelId, @Nonnull final Path modelPath) {
    final String cacheKey = modelId + ":" + modelPath.toAbsolutePath();
    cache.remove(cacheKey);
  }
}
