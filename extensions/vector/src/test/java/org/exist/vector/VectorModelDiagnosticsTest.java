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

import org.exist.storage.vector.VectorOperationMetrics;
import org.junit.After;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class VectorModelDiagnosticsTest {

    @After
    public void tearDown() {
        VectorModelDiagnostics.invalidateCache();
    }

    @Test
    public void cachesModelSnapshotWithinTtl() {
        final var first = VectorModelDiagnostics.collectModels();
        final var second = VectorModelDiagnostics.collectModels();
        assertSame(first, second);
    }

    @Test
    public void refreshModelsRebuildsSnapshot() {
        final var first = VectorModelDiagnostics.collectModels();
        final var refreshed = VectorModelDiagnostics.refreshModels();
        assertEquals(first.size(), refreshed.size());
    }

    @Test
    public void modelCountUsesSingleSnapshot() {
        final int count = VectorModelDiagnostics.getModelCount();
        final int ready = VectorModelDiagnostics.getReadyModelCount();
        assertTrue(count >= ready);
    }

    @Test
    public void diagnosticsMatchesModelsFunctionIds() {
        final Set<String> diagnosticIds = new HashSet<>();
        for (final VectorModelInfo model : VectorModelDiagnostics.collectModels()) {
            diagnosticIds.add(model.getId());
        }
        final Set<String> registryIds = new HashSet<>(ModelRegistry.getInstance().getModelIds());
        registryIds.addAll(VectorModelConstants.getKnownModelIds());
        assertEquals(registryIds, diagnosticIds);
    }
}
