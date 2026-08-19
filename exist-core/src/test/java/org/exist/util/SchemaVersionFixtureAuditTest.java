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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Visibility check for test/sample fixture copies of the canonical config templates (the ones
 * named e.g. {@code conf.xml} scattered across module test resources, each a hand-trimmed,
 * per-module subset of the real {@code exist-distribution/.../conf.xml} -- never literal copies,
 * so they can't be mechanically regenerated from canonical without destroying intentional
 * per-module customization).
 * <p>
 * Originally none of these ~39 fixtures carried {@link SchemaVersion#ATTRIBUTE}. Several have
 * since been normalized -- the attribute was added as part of stripping an accidentally-added
 * LGPL header and other boilerplate drift from a subset of them. {@link #REMAINING_WITHOUT_VERSION}
 * is the known, explicit list of what's still missing it. This test fails if that set changes --
 * either grows (a new undocumented fixture appeared) or shrinks without updating the list (a
 * fixture got fixed but this tracker wasn't updated) -- so it stays an honest, current map of
 * what's left, not a one-time snapshot.
 */
public class SchemaVersionFixtureAuditTest {

    private static final Set<String> FIXTURE_FILE_NAMES = Set.of("conf.xml", "controller-config.xml", "collection.xconf.init");

    /** The canonical instances themselves are not fixtures -- excluded from the scan. */
    private static final Set<String> CANONICAL_PATHS = Set.of(
            "exist-distribution/src/main/config/conf.xml",
            "exist-distribution/src/main/config/collection.xconf.init",
            "exist-jetty-config/src/main/resources/webapp/WEB-INF/controller-config.xml");

    /**
     * Fixtures not yet normalized -- update this list (not the assertion) as more are fixed.
     * {@code vector-it} and {@code http-client}'s {@code conf.xml} are deliberate, modern,
     * hand-written-from-scratch minimal configs, not drift victims, left alone on purpose.
     * {@code exist-core-jmh}'s hand-maintained {@code conf.xml} was replaced by one generated
     * from canonical (see {@code exist-core-jmh/src/main/resources-filtered/conf-fixture.xsl}),
     * so it carries schemaVersion like canonical and dropped off this list.
     */
    private static final Set<String> REMAINING_WITHOUT_VERSION = Set.of(
            "extensions/indexes/vector-it/src/test/resources-filtered/conf.xml",
            "extensions/modules/http-client/src/test/resources/conf.xml");

    @Test
    public void reportFixturesMissingSchemaVersion() throws Exception {
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

        assertEquals(REMAINING_WITHOUT_VERSION, Set.copyOf(missing),
                "set of fixtures missing " + SchemaVersion.ATTRIBUTE + " changed -- if you fixed one, "
                        + "remove it from REMAINING_WITHOUT_VERSION; if a new undocumented fixture appeared, "
                        + "add it there (or better, give it schemaVersion to begin with): " + missing);
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
