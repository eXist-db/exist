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
package org.exist.repo;

import com.sun.net.httpserver.HttpServer;
import org.exist.test.ExistEmbeddedServer;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.Packages;
import org.expath.pkg.repo.Repository;
import org.expath.pkg.repo.XarFileSource;
import org.expath.pkg.repo.tui.BatchUserInteraction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Installing and removing EXPath packages from several threads at once.
 *
 * <p>All packages of a database share one registry: the package map of the repository and the files
 * {@code packages.xml} and {@code packages.txt} in its {@code .expath-pkg} directory. The library
 * rewrites those files in place (read, transform, truncate, write) outside of its own lock, so two
 * threads that install or remove packages at the same time can read a half-written file
 * ({@code Error transforming the file: .../packages.xml}) or overwrite each other's change.</p>
 */
class ExistRepositoryConcurrencyTest {

    private static final int THREADS = 6;
    private static final int ROUNDS = 15;
    private static final String PACKAGE_NAME_PREFIX = "http://exist-db.org/test/concurrent-install-";

    @RegisterExtension
    static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @TempDir
    Path tempDir;

    /**
     * Every thread installs and removes a package of its own, over and over, while the others do the same.
     * No call may fail, and afterwards no package of this test may be left in the registry.
     */
    @Test
    void concurrentInstallAndRemoveOfDifferentPackages() throws Exception {
        final ExistRepository repo = repository();
        final List<Path> xars = createPackages();

        final List<Throwable> failures = runConcurrently(THREADS, thread -> {
            for (int round = 0; round < ROUNDS; round++) {
                install(repo, xars.get(thread));
                remove(repo, packageName(thread));
            }
            return null;
        });

        assertTrue(failures.isEmpty(), () -> "failures: " + failures);
        for (int thread = 0; thread < THREADS; thread++) {
            assertFalse(registryFile().contains(packageName(thread)), "left in packages.xml: " + packageName(thread));
        }
    }

    /**
     * All threads install their package at the same moment, then all remove it at the same moment. After
     * each step the registry on disk has to list exactly the packages that were installed, which fails if one
     * thread overwrote the change of another (a lost update).
     */
    @Test
    void registryKeepsEveryPackageInstalledAtTheSameTime() throws Exception {
        final ExistRepository repo = repository();
        final List<Path> xars = createPackages();

        for (int round = 0; round < ROUNDS; round++) {
            final List<Throwable> installFailures = runConcurrently(THREADS, thread -> {
                install(repo, xars.get(thread));
                return null;
            });
            assertTrue(installFailures.isEmpty(), () -> "install failures: " + installFailures);

            final String afterInstall = registryFile();
            for (int thread = 0; thread < THREADS; thread++) {
                assertTrue(afterInstall.contains(packageName(thread)), "missing in packages.xml after install: " + packageName(thread));
            }

            final List<Throwable> removeFailures = runConcurrently(THREADS, thread -> {
                remove(repo, packageName(thread));
                return null;
            });
            assertTrue(removeFailures.isEmpty(), () -> "remove failures: " + removeFailures);

            final String afterRemove = registryFile();
            for (int thread = 0; thread < THREADS; thread++) {
                assertFalse(afterRemove.contains(packageName(thread)), "left in packages.xml after remove: " + packageName(thread));
            }
        }
    }

    /**
     * A caller that is part-way through the list of installed packages, as the module lookups are while
     * they read the files of each package, must not be disturbed by another thread installing a package.
     * The interleaving is forced here instead of waited for: the iteration is started, a package is
     * installed, then the iteration continues. The repository hands out a live view of its package map
     * after releasing its own lock, so iterating that view fails with a
     * {@link java.util.ConcurrentModificationException}.
     */
    @Test
    void listingPackagesIsNotDisturbedByAnInstallDuringTheIteration() throws Exception {
        final ExistRepository repo = repository();
        final List<Path> xars = createPackages();
        install(repo, xars.get(0));
        install(repo, xars.get(1));
        try {
            final Iterator<Packages> listing = listPackages(repo).iterator();
            assertTrue(listing.hasNext());
            listing.next();

            install(repo, xars.get(2));

            int remaining = 0;
            while (listing.hasNext()) {
                listing.next();
                remaining++;
            }
            assertTrue(remaining >= 1, "the listing ended early");
        } finally {
            for (int thread = 0; thread < 3; thread++) {
                remove(repo, packageName(thread));
            }
        }
    }

    private static Collection<Packages> listPackages(final ExistRepository repo) {
        return repo.listPackages();
    }

    /**
     * A package that is still being downloaded must not hold up other installs or readers of the registry.
     * The server of the first package sends its headers and then waits; while it waits, another package is
     * installed and the packages are listed. Both have to finish before the download does, which they
     * cannot if the registry is locked while the download is read.
     */
    @Test
    void stalledDownloadDoesNotBlockOtherInstallsOrReaders() throws Exception {
        final ExistRepository repo = repository();
        final List<Path> xars = createPackages();
        final byte[] slowXar = Files.readAllBytes(xars.get(0));

        final CountDownLatch requestArrived = new CountDownLatch(1);
        final CountDownLatch releaseBody = new CountDownLatch(1);
        final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/slow.xar", exchange -> {
            exchange.sendResponseHeaders(200, slowXar.length);
            exchange.getResponseBody().flush();
            requestArrived.countDown();
            try {
                releaseBody.await(2, TimeUnit.MINUTES);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(slowXar);
            }
        });
        server.start();

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final URI uri = URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/slow.xar");
            final Future<Void> download = executor.submit(() -> {
                repo.installPackage(uri, true, new BatchUserInteraction());
                return null;
            });
            assertTrue(requestArrived.await(1, TimeUnit.MINUTES), "the download did not start");

            assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
                install(repo, xars.get(1));
                assertTrue(listPackages(repo).stream().anyMatch(p -> p.name().equals(packageName(1))));
            }, "an install or a listing waited for the stalled download");

            assertFalse(download.isDone(), "the download should still be waiting");
            releaseBody.countDown();
            download.get(1, TimeUnit.MINUTES);

            assertTrue(registryFile().contains(packageName(0)), "the downloaded package is not in packages.xml");
            assertTrue(registryFile().contains(packageName(1)), "the other package is not in packages.xml");
        } finally {
            releaseBody.countDown();
            executor.shutdownNow();
            server.stop(0);
            for (int thread = 0; thread < 2; thread++) {
                try {
                    remove(repo, packageName(thread));
                } catch (final PackageException e) {
                    // not installed
                }
            }
        }
    }

    /**
     * A download that fails reports the same error as the repository does on its own: a
     * {@link PackageException} that carries the {@code NotFoundException} of the address, and nothing is installed.
     */
    @Test
    void failedDownloadIsReportedLikeTheRepositoryDoes() throws Exception {
        final ExistRepository repo = repository();
        final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/missing.xar", exchange -> exchange.sendResponseHeaders(404, -1));
        server.start();
        try {
            final URI uri = URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/missing.xar");
            final PackageException e = assertThrows(PackageException.class, () -> repo.installPackage(uri, true, new BatchUserInteraction()));

            Throwable cause = e;
            boolean notFound = false;
            while (cause != null && !notFound) {
                notFound = cause instanceof Repository.NotFoundException;
                cause = cause.getCause();
            }
            assertTrue(notFound, () -> "expected a NotFoundException in the causes of " + e);
        } finally {
            server.stop(0);
        }
    }

    private static void install(final ExistRepository repo, final Path xar) throws PackageException {
        repo.installPackage(new XarFileSource(xar), true, new BatchUserInteraction());
    }

    private static void remove(final ExistRepository repo, final String packageName) throws PackageException {
        repo.removePackage(packageName, true, new BatchUserInteraction());
    }

    private static ExistRepository repository() {
        return existEmbeddedServer.getBrokerPool().getExpathRepo().orElseThrow();
    }

    private static String registryFile() throws IOException {
        final Path repoDir = ExistRepository.getRepositoryDir(existEmbeddedServer.getBrokerPool().getConfiguration());
        return Files.readString(repoDir.resolve(".expath-pkg").resolve("packages.xml"), StandardCharsets.UTF_8);
    }

    private static String packageName(final int thread) {
        return PACKAGE_NAME_PREFIX + thread;
    }

    private List<Path> createPackages() throws IOException {
        final List<Path> xars = new ArrayList<>();
        for (int thread = 0; thread < THREADS; thread++) {
            final Path xar = tempDir.resolve("concurrent-" + thread + ".xar");
            try (final ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(xar))) {
                zos.putNextEntry(new ZipEntry("expath-pkg.xml"));
                zos.write("""
                        <package xmlns="http://expath.org/ns/pkg" name="%s" abbrev="concurrent-%d" version="1.0.0" spec="1.0">
                            <title>Concurrent install %d</title>
                        </package>""".formatted(packageName(thread), thread, thread).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("content/data.xml"));
                zos.write("<data/>".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            xars.add(xar);
        }
        return xars;
    }

    /**
     * Runs {@code task} on {@code threads} threads that are released together.
     *
     * @return the failures of all of them, empty if none failed
     */
    private static List<Throwable> runConcurrently(final int threads, final ThreadTask task) throws InterruptedException {
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        final CountDownLatch ready = new CountDownLatch(threads);
        final CountDownLatch go = new CountDownLatch(1);
        final List<Throwable> failures = new CopyOnWriteArrayList<>();
        try {
            final List<Future<Void>> futures = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                final int index = thread;
                futures.add(executor.submit((Callable<Void>) () -> {
                    ready.countDown();
                    go.await();
                    try {
                        return task.run(index);
                    } catch (final Throwable t) {
                        failures.add(t);
                        return null;
                    }
                }));
            }
            ready.await();
            go.countDown();
            for (final Future<Void> future : futures) {
                try {
                    future.get(2, TimeUnit.MINUTES);
                } catch (final Exception e) {
                    failures.add(e);
                }
            }
        } finally {
            executor.shutdownNow();
        }
        return failures;
    }

    @FunctionalInterface
    private interface ThreadTask {
        Void run(int thread) throws Exception;
    }
}
