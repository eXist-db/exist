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

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared model diagnostics for {@code vector:diagnostics()} and the {@code VectorEmbedding} JMX MBean.
 */
public final class VectorModelDiagnostics {

    /** Default TTL for cached model snapshots (JMX polling). */
    private static final long CACHE_TTL_MS = 60_000L;

    private static volatile CachedSnapshot cache;

    private VectorModelDiagnostics() {
    }

    private record CachedSnapshot(List<VectorModelInfo> models, long timestampMs) {
    }

    /**
     * Collect diagnostic information for registry entries and built-in models.
     * Results are cached briefly to avoid repeated filesystem probes under JMX polling.
     *
     * @return model rows sorted by id
     */
    public static List<VectorModelInfo> collectModels() {
        final long now = System.currentTimeMillis();
        final CachedSnapshot current = cache;
        if (current != null && now - current.timestampMs() < CACHE_TTL_MS) {
            return current.models();
        }
        final List<VectorModelInfo> models = collectModelsUncached();
        final CachedSnapshot snapshot = new CachedSnapshot(List.copyOf(models), now);
        cache = snapshot;
        return snapshot.models();
    }

    /**
     * Returns model count from a single cached snapshot.
     *
     * @return total model count
     */
    public static int getModelCount() {
        return collectModels().size();
    }

    /**
     * Returns ready model count from a single cached snapshot.
     *
     * @return ready model count
     */
    public static int getReadyModelCount() {
        return countReadyModels(collectModels());
    }

    /**
     * Clears the cached model snapshot. The next {@link #collectModels()} rebuilds it.
     */
    public static void invalidateCache() {
        cache = null;
    }

    /**
     * Forces a fresh model snapshot and returns it.
     *
     * @return fresh model information rows
     */
    public static List<VectorModelInfo> refreshModels() {
        invalidateCache();
        return collectModels();
    }

    /**
     * Counts how many models are in available status.
     *
     * @param models the model list to scan
     * @return number of available models
     */
    public static int countReadyModels(final List<VectorModelInfo> models) {
        int ready = 0;
        for (final VectorModelInfo model : models) {
            if ("available".equals(model.getStatus())) {
                ready++;
            }
        }
        return ready;
    }

    private static List<VectorModelInfo> collectModelsUncached() {
        final ModelRegistry registry = ModelRegistry.getInstance();
        final Set<String> registryIds = registry.getModelIds();
        final Set<String> allIds = new HashSet<>(registryIds);
        allIds.addAll(VectorModelConstants.getKnownModelIds());

        final List<VectorModelInfo> models = new ArrayList<>(allIds.size());
        for (final String id : allIds.stream().sorted().toList()) {
            models.add(describeModel(id, registryIds));
        }
        return models;
    }

    private static VectorModelInfo describeModel(final String id, final Set<String> registryIds) {
        final String source;
        if (registryIds.contains(id) && VectorModelConstants.getKnownModelIds().contains(id)) {
            source = "registry+builtin";
        } else if (registryIds.contains(id)) {
            source = "registry";
        } else {
            source = "builtin";
        }

        final ModelRegistry.Resolved resolved = ModelRegistry.getInstance().resolve(id, null, VectorModelConstants.getDefaultDimension(id));
        final String path = resolved.path;
        final int dim = resolved.dimension;

        final String status;
        final String message;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            status = "http";
            message = "HTTP/API model; availability depends on remote endpoint and API key configuration.";
        } else {
            final Path p = ModelPathResolver.resolve(id, path);
            if (p != null) {
                status = "available";
                message = null;
            } else {
                status = "missing";
                message = "Model directory not found under exist.home; ensure " + path
                        + " exists with model.onnx and tokenizer.json or adjust conf.xml/vector-field model-path.";
            }
        }

        return new VectorModelInfo(id, source, path, dim, status, message, deriveProvider(status, path));
    }

    private static String deriveProvider(final String status, final String path) {
        if ("http".equals(status) || path.startsWith("http://") || path.startsWith("https://")) {
            return "HTTP";
        }
        return "ONNX";
    }
}
