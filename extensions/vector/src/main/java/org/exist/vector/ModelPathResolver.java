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
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Resolves model path or HuggingFace URL to a local directory.
 * When pathOrUrl is an http(s) URL, downloads model.onnx and tokenizer.json on first use.
 * Local paths are resolved against a single, explicit base (exist.home or user.dir) and
 * validated so failures are clear and environment-dependent behaviour is reduced.
 */
public final class ModelPathResolver {

  private static final Logger LOG = LogManager.getLogger(ModelPathResolver.class);

  private static final String MODEL_FILE = "model.onnx";
  private static final String TOKENIZER_FILE = "tokenizer.json";
  private static final String DEFAULT_ONNX_SUBDIR = "onnx-models";

  private ModelPathResolver() {
  }

  /**
   * Base directory for resolving relative model paths and for the ONNX cache.
   * Uses BrokerPool exist home if available, then exist.home, then user.dir.
   */
  @Nonnull
  static Path getVectorBase() {
    final Optional<Path> home = ConfigurationHelper.getExistHome();
    return home.orElse(Paths.get(System.getProperty("user.dir", "."))).normalize().toAbsolutePath();
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
    final Path base = getVectorBase();
    Path resolved;
    try {
      resolved = base.resolve(pathStr).normalize().toAbsolutePath();
    } catch (final Exception e) {
      LOG.warn("Invalid model path '{}' (base={}): {}", pathStr, base, e.getMessage());
      return null;
    }
    if (!resolved.startsWith(base)) {
      LOG.warn("Model path '{}' resolved outside exist.home base {} (resolved={}); refusing to load model.", pathStr, base, resolved);
      return null;
    }
    if (hasModelFiles(resolved)) {
      LOG.debug("Resolved model path '{}' to {}", pathStr, resolved);
      return resolved;
    }
    LOG.warn("Model directory not found at {} (resolved from '{}'; base={}). Directory must contain {} and {}.",
        resolved, pathStr, base, MODEL_FILE, TOKENIZER_FILE);
    if (pathStr.startsWith(DEFAULT_ONNX_SUBDIR + "/") || pathStr.equals(DEFAULT_ONNX_SUBDIR)) {
      final Path fallback = base.resolve("target").resolve(pathStr);
      if (hasModelFiles(fallback)) {
        LOG.info("Using fallback path for tests/build layout: {}", fallback);
        return fallback;
      }
    }
    return null;
  }

  private static boolean hasModelFiles(final Path dir) {
    return dir != null
        && Files.isDirectory(dir)
        && Files.isRegularFile(dir.resolve(MODEL_FILE))
        && Files.isRegularFile(dir.resolve(TOKENIZER_FILE));
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
    final Path base = getVectorBase();
    final Path root = base.resolve(DEFAULT_ONNX_SUBDIR);
    final Path cacheDir = root.resolve(sanitizeDirName(modelId));
    try {
      Files.createDirectories(cacheDir);
      return cacheDir;
    } catch (final IOException e) {
      LOG.warn("Failed to create ONNX cache dir {} (base={}): {}", cacheDir, base, e.getMessage());
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
