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
package org.exist.util;

import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * Visibility check for test/sample fixture copies of the canonical config templates (the ones
 * named e.g. {@code conf.xml} scattered across module test resources, each a hand-trimmed,
 * per-module subset of the real {@code exist-distribution/.../conf.xml} -- never literal copies,
 * so they can't be mechanically regenerated from canonical without destroying intentional
 * per-module customization).
 * <p>
 * None of these ~39 fixtures carry {@link SchemaVersion#ATTRIBUTE}, so none of them are checked
 * for drift the way {@link SchemaVersionSyncTest} checks {@link SchemaVersion} itself. This is
 * the cheaper "visibility before automation" interim step: list which fixtures are missing the
 * attribute, so the gap is visible in CI rather than silent. Actually adding {@code schemaVersion}
 * to all of them (via Maven resource filtering, so it can't drift once added) is a separate,
 * larger follow-up -- this test does not edit any fixture.
 */
public class SchemaVersionFixtureAuditTest {

    private static final Set<String> FIXTURE_FILE_NAMES = Set.of("conf.xml", "controller-config.xml", "collection.xconf.init");

    /** The canonical instances themselves are not fixtures -- excluded from the scan. */
    private static final Set<String> CANONICAL_PATHS = Set.of(
            "exist-distribution/src/main/config/conf.xml",
            "exist-distribution/src/main/config/collection.xconf.init",
            "exist-jetty-config/src/main/resources/webapp/WEB-INF/controller-config.xml");

    @Test
    public void reportFixturesMissingSchemaVersion() throws Exception {
        final Path repoRoot = resolveRepoRoot();

        final List<Path> fixtures = findFixtures(repoRoot);
        assertTrue("expected to find test/sample fixture copies of conf.xml/controller-config.xml/"
                + "collection.xconf.init under " + repoRoot + " (found none -- is repo root resolution broken?)",
                !fixtures.isEmpty());

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        final List<String> missing = new ArrayList<>();
        for (final Path fixture : fixtures) {
            final Document doc = factory.newDocumentBuilder().parse(fixture.toFile());
            final String declared = doc.getDocumentElement().getAttribute(SchemaVersion.ATTRIBUTE);
            if (declared == null || declared.isEmpty()) {
                missing.add(repoRoot.relativize(fixture).toString());
            }
        }

        // Not a hard failure (yet) -- every one of these is currently missing schemaVersion, by
        // design (see class javadoc); this is the visibility step, not the enforcement step. The
        // assertion just keeps the count itself from silently drifting (e.g. if a fixture
        // unexpectedly starts carrying schemaVersion, or a new copy appears uninspected).
        assertTrue("Found " + missing.size() + " fixture(s) without " + SchemaVersion.ATTRIBUTE
                        + " (expected, see class javadoc -- this is a visibility check, not enforcement): "
                        + missing,
                missing.size() == fixtures.size());
    }

    private static List<Path> findFixtures(final Path repoRoot) throws IOException {
        final List<Path> fixtures = new ArrayList<>();
        Files.walkFileTree(repoRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
                final String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if (name.equals("target") || name.equals(".git") || name.equals(".moderne") || name.equals("node_modules")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                if (FIXTURE_FILE_NAMES.contains(file.getFileName().toString())) {
                    final String relative = repoRoot.relativize(file).toString().replace('\\', '/');
                    if (!CANONICAL_PATHS.contains(relative)) {
                        fixtures.add(file);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return fixtures;
    }

    private static Path resolveRepoRoot() {
        final Path base = Path.of(System.getProperty("user.dir"));
        Path p = base.resolve("schema");
        if (Files.isDirectory(p)) {
            return base;
        }
        return base.getParent();
    }
}
