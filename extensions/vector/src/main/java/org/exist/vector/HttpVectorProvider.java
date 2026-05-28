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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP-based vector embedding provider for external APIs (OpenAI, Cohere).
 * Implements {@link VectorEmbeddingProvider} for use with vector:embed and embedding="local".
 * <p>
 * API keys (preferred method): Set {@code OPENAI_API_KEY} or {@code COHERE_API_KEY} as
 * environment variables before starting eXist (e.g. in the shell or startup script).
 * Keys are read via {@link System#getenv(String)} in Java; this keeps keys out of XQuery
 * and avoids exposing them via {@code fn:environment-variable()} (which is DBA-only).
 */
public final class HttpVectorProvider implements VectorEmbeddingProvider {

  private static final Logger LOG = LogManager.getLogger(HttpVectorProvider.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  /**
   * Supported HTTP embedding API types.
   */
  public enum ApiType {
    OPENAI,
    COHERE
  }

  private final String modelId;
  private final String baseUrl;
  private final String apiKey;
  private final int dimension;
  private final ApiType apiType;
  private final HttpClient httpClient;

  private HttpVectorProvider(final String modelId, final String baseUrl, final String apiKey,
      final int dimension, final ApiType apiType) {
    this.modelId = modelId;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    this.apiKey = apiKey;
    this.dimension = dimension;
    this.apiType = apiType;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
  }

  @Override
  public int getDimension() {
    return dimension;
  }

  @Override
  @Nullable
  public float[] embed(final String text) {
    return embed(text, false);
  }

  @Override
  @Nullable
  public float[] embed(final String text, final boolean forQuery) {
    if (text == null || apiKey == null || apiKey.isEmpty()) {
      return null;
    }
    try {
      return switch (apiType) {
        case OPENAI -> embedOpenAI(text);
        case COHERE -> embedCohere(text, forQuery);
        default -> null;
      };
    } catch (final Exception e) {
      LOG.warn("HTTP embedding failed for {}: {}", modelId, e.getMessage());
      return null;
    }
  }

  private float[] embedOpenAI(final String text) throws IOException, InterruptedException {
    final ObjectNode body = JSON.createObjectNode();
    body.put("input", text);
    body.put("model", modelId);
    body.put("encoding_format", "float");
    if (dimension > 0) {
      body.put("dimensions", dimension);
    }
    final String url = baseUrl + "embeddings";
    final HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(60))
        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8))
        .build();
    final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() != 200) {
      LOG.warn("OpenAI API error {}: {}", response.statusCode(), response.body());
      return null;
    }
    return parseOpenAIResponse(response.body());
  }

  private float[] embedCohere(final String text, final boolean forQuery) throws IOException, InterruptedException {
    final ObjectNode body = JSON.createObjectNode();
    final ArrayNode texts = body.putArray("texts");
    texts.add(text);
    body.put("model", modelId);
    body.put("input_type", forQuery ? "search_query" : "search_document");
    final String url = baseUrl + "embed";
    final HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(60))
        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8))
        .build();
    final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() != 200) {
      LOG.warn("Cohere API error {}: {}", response.statusCode(), response.body());
      return null;
    }
    return parseCohereResponse(response.body());
  }

  private float[] parseOpenAIResponse(final String json) {
    try {
      final JsonNode root = JSON.readTree(json);
      final JsonNode data = root.path("data");
      if (!data.isArray() || data.isEmpty()) {
        return null;
      }
      final JsonNode embedding = data.get(0).path("embedding");
      if (!embedding.isArray()) {
        return null;
      }
      return toFloatArray(embedding);
    } catch (final Exception e) {
      LOG.warn("Failed to parse OpenAI response: {}", e.getMessage());
      return null;
    }
  }

  private float[] parseCohereResponse(final String json) {
    try {
      final JsonNode root = JSON.readTree(json);
      final JsonNode embeddings = root.path("embeddings");
      if (!embeddings.isArray() || embeddings.isEmpty()) {
        return null;
      }
      return toFloatArray(embeddings.get(0));
    } catch (final Exception e) {
      LOG.warn("Failed to parse Cohere response: {}", e.getMessage());
      return null;
    }
  }

  private static float[] toFloatArray(final JsonNode arr) {
    final List<Float> list = new ArrayList<>(arr.size());
    for (final JsonNode n : arr) {
      list.add((float) n.asDouble());
    }
    final float[] result = new float[list.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = list.get(i);
    }
    return result;
  }

  /**
   * Creates an HttpVectorProvider if the base URL indicates an external API.
   *
   * @param modelId   model identifier (e.g. text-embedding-3-small, embed-english-v3.0)
   * @param baseUrl   API base URL (e.g. https://api.openai.com/v1, https://api.cohere.ai/v1)
   * @param apiKey    API key; if null, resolved from OPENAI_API_KEY or COHERE_API_KEY env (preferred)
   * @param dimension expected embedding dimension
   * @return provider or null if URL is not a supported API or key is missing
   */
  @Nullable
  public static HttpVectorProvider create(@Nonnull final String modelId, @Nonnull final String baseUrl,
      @Nullable final String apiKey, final int dimension) {
    final ApiType type = detectApiType(baseUrl);
    if (type == null) return null;
    // Preferred: env vars (set before eXist starts). Fallback: explicit apiKey param.
    final String key = apiKey != null && !apiKey.isEmpty()
        ? apiKey
        : (type == ApiType.OPENAI ? System.getenv("OPENAI_API_KEY") : System.getenv("COHERE_API_KEY"));
    if (key == null || key.isEmpty()) {
      LOG.warn("API key not set for {}: set OPENAI_API_KEY or COHERE_API_KEY", type);
      return null;
    }
    return new HttpVectorProvider(modelId, baseUrl.trim(), key, dimension, type);
  }

  @Nullable
  private static ApiType detectApiType(final String url) {
    final String lower = url.toLowerCase();
    if (lower.contains("api.openai.com")) return ApiType.OPENAI;
    if (lower.contains("api.cohere")) return ApiType.COHERE;
    return null;
  }

  /**
   * Returns true if the path/URL indicates an external HTTP embedding API.
   *
   * @param pathOrUrl the path or URL to check
   * @return true if the URL targets a supported HTTP embedding API
   */
  public static boolean isHttpApiUrl(final String pathOrUrl) {
    return pathOrUrl != null && detectApiType(pathOrUrl) != null;
  }

  /**
   * Returns default dimension for known HTTP embedding models.
   *
   * @param type    the API type (OpenAI or Cohere)
   * @param modelId the model identifier
   * @return the default dimension for the model
   */
  public static int getDefaultDimension(final ApiType type, final String modelId) {
    if (type == ApiType.OPENAI) {
      return VectorModelConstants.getOpenAIDefaultDimension(modelId);
    }
    if (type == ApiType.COHERE) {
      return VectorModelConstants.getCohereDefaultDimension(modelId);
    }
    return 384;
  }
}
