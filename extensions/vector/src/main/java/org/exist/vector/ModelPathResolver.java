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
import org.exist.util.ConfigurationHelper;

import javax.annotation.Nonnull;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Resolves model path or HuggingFace URL to a local directory.
 * When pathOrUrl is an http(s) URL, downloads model.onnx and tokenizer.json on first use.
 */
public final class ModelPathResolver {

  private static final Logger LOG = LogManager.getLogger(ModelPathResolver.class);

  private static final String MODEL_FILE = "model.onnx";
  private static final String TOKENIZER_FILE = "tokenizer.json";

  private ModelPathResolver() {
  }

  /**
   * Resolves path or URL to a local directory containing model.onnx and tokenizer.json.
   *
   * @param modelId   model identifier (e.g. "all-MiniLM-L6-v2") used for cache directory name
   * @param pathOrUrl local path (relative to exist.home) or HuggingFace base URL
   * @return path to directory with model files, or null if resolution fails
   */
  @javax.annotation.Nullable
  public static Path resolve(@Nonnull final String modelId, @Nonnull final String pathOrUrl) {
    final String trimmed = pathOrUrl.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      return resolveUrl(modelId, trimmed);
    }
    return resolveLocal(trimmed);
  }

  private static Path resolveLocal(final String pathStr) {
    try {
      return ConfigurationHelper.lookup(pathStr);
    } catch (final Exception e) {
      LOG.warn("Failed to resolve model path {}: {}", pathStr, e.getMessage());
      return null;
    }
  }

  private static Path resolveUrl(final String modelId, final String baseUrl) {
    final Path cacheDir = getCacheDir(modelId);
    if (cacheDir == null) {
      return null;
    }
    try {
      Files.createDirectories(cacheDir);
    } catch (final IOException e) {
      LOG.warn("Failed to create cache dir {}: {}", cacheDir, e.getMessage());
      return null;
    }
    final Path modelPath = cacheDir.resolve(MODEL_FILE);
    final Path tokenizerPath = cacheDir.resolve(TOKENIZER_FILE);
    if (Files.isRegularFile(modelPath) && Files.isRegularFile(tokenizerPath)) {
      LOG.debug("Using cached model {} at {}", modelId, cacheDir);
      return cacheDir;
    }
    final String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    if (!downloadFile(base + MODEL_FILE, modelPath) || !downloadFile(base + TOKENIZER_FILE, tokenizerPath)) {
      return null;
    }
    LOG.info("Downloaded ONNX model {} to {}", modelId, cacheDir);
    return cacheDir;
  }

  private static Path getCacheDir(final String modelId) {
    try {
      final Path root = ConfigurationHelper.lookup("onnx-models");
      return root.resolve(sanitizeDirName(modelId));
    } catch (final Exception e) {
      LOG.warn("Failed to resolve cache root: {}", e.getMessage());
      return null;
    }
  }

  private static String sanitizeDirName(final String modelId) {
    return modelId.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static boolean downloadFile(final String urlStr, final Path target) {
    try {
      final URL url = URI.create(urlStr).toURL();
      try (InputStream in = new BufferedInputStream(url.openStream())) {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      }
      return true;
    } catch (final Exception e) {
      LOG.warn("Failed to download {} to {}: {}", urlStr, target, e.getMessage());
      return false;
    }
  }
}
