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

import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Visibility check for any remaining source-tree fixture copies of the canonical config templates
 * (files named {@code conf.xml} / {@code controller-config.xml} / {@code collection.xconf.init}
 * under module test resources). Most test fixtures are now generated from canonical at build time
 * via {@code schema/generate-conf-fixture.xsl}; this test only scans committed source files (not
 * {@code target/}).
 * <p>
 * Fails if any source fixture is missing {@link SchemaVersion#ATTRIBUTE} — either give the new
 * fixture a {@code schemaVersion}, or prefer generating it from canonical so the attribute
 * comes along automatically.
 */
class SchemaVersionFixtureAuditTest {

    private static final Set<String> FIXTURE_FILE_NAMES = Set.of("conf.xml", "controller-config.xml", "collection.xconf.init");

    /** The canonical instances themselves are not fixtures -- excluded from the scan. */
    private static final Set<String> CANONICAL_PATHS = Set.of(
            "exist-distribution/src/main/config/conf.xml",
            "exist-distribution/src/main/config/collection.xconf.init",
            "exist-jetty-config/src/main/resources/webapp/WEB-INF/controller-config.xml");

    @Test
    void reportFixturesMissingSchemaVersion() throws Exception {
        final Path repoRoot = resolveRepoRoot();

        final List<Path> fixtures = findFixtures(repoRoot);
        assertTrue(!fixtures.isEmpty(), "expected to find test/sample fixture copies of conf.xml/controller-config.xml/"
                + "collection.xconf.init under " + repoRoot + " (found none -- is repo root resolution broken?)");

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        final List<String> missing = new ArrayList<>();
        for (final Path fixture : fixtures) {
            final Document doc = factory.newDocumentBuilder().parse(fixture.toFile());
            final String declared = doc.getDocumentElement().getAttribute(SchemaVersion.ATTRIBUTE);
            if (declared == null || declared.isEmpty()) {
                missing.add(repoRoot.relativize(fixture).toString().replace('\\', '/'));
            }
        }

        assertTrue(missing.isEmpty(),
                "source fixtures missing " + SchemaVersion.ATTRIBUTE + " — give them schemaVersion "
                        + "or generate from canonical via conf-fixture.xsl: " + missing);
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
