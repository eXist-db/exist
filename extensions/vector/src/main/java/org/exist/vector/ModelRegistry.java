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
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Global model registry. Loads from the {@code <vector-models>} section in conf.xml.
 * Overrides per-collection: when vector-field or vector:embed omits model-path,
 * the registry supplies path and dimension for known model IDs.
 */
public final class ModelRegistry {

  private static final Logger LOG = LogManager.getLogger(ModelRegistry.class);
  private static final String CONF_XML = "conf.xml";

  private static volatile ModelRegistry instance;
  private final Map<String, ModelEntry> entries = new HashMap<>();

  public static final class ModelEntry {
    public final String path;
    public final int dimension;

    public ModelEntry(final String path, final int dimension) {
      this.path = path;
      this.dimension = dimension;
    }
  }

  private ModelRegistry() {
    load();
  }

  @Nonnull
  public static ModelRegistry getInstance() {
    ModelRegistry r = instance;
    if (r == null) {
      synchronized (ModelRegistry.class) {
        r = instance;
        if (r == null) {
          r = instance = new ModelRegistry();
        }
      }
    }
    return r;
  }

  private void load() {
    try {
      final Optional<Path> home = ConfigurationHelper.getExistHome();
      final Path confFile = home.map(h -> h.resolve(CONF_XML).normalize().toAbsolutePath())
          .orElseGet(() -> Paths.get(System.getProperty("user.dir", ".")).resolve(CONF_XML).normalize().toAbsolutePath());
      if (!Files.isRegularFile(confFile) || !Files.isReadable(confFile)) {
        LOG.debug("conf.xml not found or not readable at {} (vector-models registry empty)", confFile);
        return;
      }
      final org.w3c.dom.Document doc = parseXml(confFile);
      if (doc == null) return;
      final org.w3c.dom.NodeList vectorModelsList = doc.getElementsByTagName("vector-models");
      if (vectorModelsList.getLength() == 0) {
        LOG.debug("No <vector-models> in {}", confFile);
        return;
      }
      final org.w3c.dom.Element vectorModels = (org.w3c.dom.Element) vectorModelsList.item(0);
      final org.w3c.dom.NodeList models = vectorModels.getElementsByTagName("model");
      for (int i = 0; i < models.getLength(); i++) {
        final org.w3c.dom.Element el = (org.w3c.dom.Element) models.item(i);
        final String id = el.getAttribute("id");
        final String path = el.getAttribute("path");
        final String dimStr = el.getAttribute("dimension");
        if (id == null || id.isEmpty() || path == null || path.isEmpty()) continue;
        final int dim = parseDimension(dimStr, 384);
        entries.put(id.trim(), new ModelEntry(path.trim(), dim));
      }
      if (!entries.isEmpty()) {
        LOG.info("Loaded {} model(s) from <vector-models> in {}", entries.size(), confFile);
      }
    } catch (final Exception e) {
      LOG.debug("Could not load vector-models from conf.xml: {}", e.getMessage());
    }
  }

  private static org.w3c.dom.Document parseXml(final Path path) {
    try {
      final javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setFeature("http://xml.org/sax/features/external-general-entities", false);
      f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      return f.newDocumentBuilder().parse(path.toFile());
    } catch (final Exception e) {
      LOG.warn("Failed to parse {}: {}", path, e.getMessage());
      return null;
    }
  }

  private static int parseDimension(final String s, final int defaultVal) {
    if (s == null || s.isEmpty()) return defaultVal;
    try {
      return Integer.parseInt(s.trim());
    } catch (final NumberFormatException e) {
      return defaultVal;
    }
  }

  /**
   * Returns the registry entry for the model ID, or null if not configured.
   */
  @Nullable
  public ModelEntry get(@Nonnull final String modelId) {
    return entries.get(modelId.trim());
  }

  /**
   * Returns all registered model IDs.
   */
  @Nonnull
  public Set<String> getModelIds() {
    return Collections.unmodifiableSet(entries.keySet());
  }

  /**
   * Resolves path and dimension. When pathOrUrl is null/empty and the registry
   * has an entry for modelId, use the registry path and dimension. Otherwise
   * use the given path (or default "onnx-models/" + modelId) and defaultDimension.
   */
  public Resolved resolve(@Nonnull final String modelId, @Nullable final String pathOrUrl, final int defaultDimension) {
    final boolean useDefaultPath = pathOrUrl == null || pathOrUrl.isEmpty();
    final ModelEntry reg = useDefaultPath ? get(modelId) : null;
    if (reg != null) {
      return new Resolved(reg.path, reg.dimension);
    }
    final String path = useDefaultPath ? "onnx-models/" + modelId : pathOrUrl;
    return new Resolved(path, defaultDimension);
  }

  public static final class Resolved {
    public final String path;
    public final int dimension;

    Resolved(final String path, final int dimension) {
      this.path = path;
      this.dimension = dimension;
    }
  }
}
