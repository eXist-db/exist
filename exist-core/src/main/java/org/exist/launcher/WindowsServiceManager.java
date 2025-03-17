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

package org.exist.launcher;

import com.evolvedbinary.j8fu.OptionalUtil;
import com.evolvedbinary.j8fu.lazy.LazyValE;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import net.jcip.annotations.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.ConfigurationHelper;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.evolvedbinary.j8fu.Either.Left;
import static com.evolvedbinary.j8fu.Either.Right;
import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.launcher.ConfigurationUtility.LAUNCHER_PROPERTY_MAX_MEM;
import static org.exist.launcher.ConfigurationUtility.LAUNCHER_PROPERTY_MIN_MEM;

@NotThreadSafe
class WindowsServiceManager implements ServiceManager {

    /**
     * See <a href="https://docs.oracle.com/javase/8/docs/technotes/tools/windows/java.html#BABHDABI">Java - Non-Standard Options</a>.
     */
    private static final Pattern JAVA_CMDLINE_MEMORY_STRING = Pattern.compile("([0-9]+)(g|G|m|M|k|K)?.*");

    private static final Logger LOG = LogManager.getLogger(WindowsServiceManager.class);
    private static final String PROCRUN_SRV_EXE = "prunsrv-x86_64.exe";
    private static final String SC_EXE = "sc.exe";

    private static final String SERVICE_NAME = "eXist-db";

    private final Path existHome;
    private final LazyValE<Path, ServiceManagerException> prunsrvExe;

    private enum WindowsServiceState {
        UNINSTALLED,
        RUNNING,
        STOPPED,
        PAUSED
    }

    WindowsServiceManager() {
        this.prunsrvExe = new LazyValE<>(() ->
            OptionalUtil.toRight(() -> new ServiceManagerException("Could not detect EXIST_HOME when trying to find Procrun exe"), ConfigurationHelper.getExistHome())
                .map(base -> base.resolve("bin").resolve(PROCRUN_SRV_EXE))
                .flatMap(exe -> Files.exists(exe) ? Right(exe) : Left(new ServiceManagerException("Could not find Procrun at: " + exe)))
                .flatMap(exe -> Files.isExecutable(exe) ? Right(exe) : Left(new ServiceManagerException("Procrun is not executable at: " + exe)))
        );

        this.existHome = ConfigurationHelper.getExistHome().orElse(Paths.get("."));
    }

    @Override
    public void install() throws ServiceManagerException {
        if (getState() != WindowsServiceState.UNINSTALLED) {
            throw new ServiceManagerException("Service is already installed");
        }

        final Path configFile = ConfigurationHelper.getFromSystemProperty()
                .orElse(existHome.resolve("etc").resolve("conf.xml"));

        final Properties launcherProperties = ConfigurationUtility.loadProperties();
        final Optional<String> maxMemory = Optional.ofNullable(launcherProperties.getProperty(LAUNCHER_PROPERTY_MAX_MEM)).flatMap(WindowsServiceManager::asJavaCmdlineMemoryString);
        final Optional<String> minMemory = asJavaCmdlineMemoryString(launcherProperties.getProperty(LAUNCHER_PROPERTY_MIN_MEM, "128"));

        final StringBuilder jvmOptions = new StringBuilder();
        jvmOptions.append("-Dfile.encoding=UTF-8");
        for (final String propertyName : System.getProperties().stringPropertyNames()) {
            if (propertyName.startsWith("exist.") ||
                    propertyName.startsWith("jetty.") ||
                    propertyName.startsWith("log4j.")) {
                final String propertyValue = System.getProperty(propertyName);
                if (propertyValue != null) {
                    jvmOptions
                            .append(";-D").append(propertyName)
                            .append('=')
                            .append(propertyValue);
                }
            }
        }
        final Path exe = prunsrvExe.get();
        final List<String> args = newList(exe.toAbsolutePath().toString(), "install", SERVICE_NAME,
                "--DisplayName=" + SERVICE_NAME,
                "--Description=eXist-db NoSQL Database Server",
                "--StdError=auto",
                "--StdOutput=auto",
                "--LogPath=\"" + existHome.resolve("logs").toAbsolutePath() + "\"",
                "--LogPrefix=service",
                "--PidFile=service.pid",
                "--Startup=auto",
                "--ServiceUser=LocalSystem",  // TODO(AR) this changed from `LocalSystem` to `NT Authority\LocalService` in procrun 1.2.0, however our service won't seem to start under that account... we need to investigate!
                "--Jvm=" + findJvm().orElse("auto"),
                "--Classpath=\"" + existHome.resolve("lib").toAbsolutePath().toString().replace('\\', '/') + "/*\"",
                "--StartMode=jvm",
                "--StartClass=org.exist.service.ExistDbDaemon",
                "--StartMethod=start",
                "--StopMode=jvm",
                "--StopClass=org.exist.service.ExistDbDaemon",
                "--StopMethod=stop",
                "--JvmOptions=\"" + jvmOptions + "\"",
                "--StartParams=\"" + configFile.toAbsolutePath() + "\""
        );
        minMemory.flatMap(WindowsServiceManager::asPrunSrvMemoryString).ifPresent(xms -> args.add("--JvmMs=" + xms));
        maxMemory.flatMap(WindowsServiceManager::asPrunSrvMemoryString).ifPresent(xmx -> args.add("--JvmMx=" + xmx));

        try {
            final Tuple2<Integer, String> execResult = run(args, true);
            final int exitCode = execResult._1;
            final String result = execResult._2;

            if (exitCode != 0) {
                LOG.error("Could not install service, exitCode={}, output='{}'", exitCode, result);
                throw new ServiceManagerException("Could not install service, exitCode=" + exitCode + ", output='" + result + "'");
            }
        } catch (final IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.error("Could not install service: {}", e.getMessage(), e);
            throw new ServiceManagerException("Could not install service: " + e.getMessage(), e);
        }
    }

    private static <T> List<T> newList(final T... items) {
        final List<T> list = new ArrayList<>(items.length);
        list.addAll(Arrays.asList(items));
        return list;
    }


    @Override
    public boolean isInstalled() {
        try {
            return getState() != WindowsServiceState.UNINSTALLED;
        } catch (final ServiceManagerException e) {
            LOG.error("Could not determine if service is installed: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void uninstall() throws ServiceManagerException {
        if (getState() == WindowsServiceState.UNINSTALLED) {
            throw new ServiceManagerException("Service is already uninstalled");
        }

        final Path exe = prunsrvExe.get();
        final List<String> args = Arrays.asList(exe.toAbsolutePath().toString(), "delete", SERVICE_NAME);
        try {
            final Tuple2<Integer, String> execResult = run(args, true);
            final int exitCode = execResult._1;
            final String result = execResult._2;

            if (exitCode != 0) {
                LOG.error("Could not uninstall service, exitCode={}, output='{}'", exitCode, result);
                throw new ServiceManagerException("Could not uninstall service, exitCode=" + exitCode + ", output='" + result + "'");
            }
        } catch (final IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.error("Could not uninstall service: {}", e.getMessage(), e);
            throw new ServiceManagerException("Could not uninstall service: " + e.getMessage(), e);
        }
    }

    @Override
    public void start() throws ServiceManagerException {
        final WindowsServiceState state = getState();
        if (state == WindowsServiceState.RUNNING || state == WindowsServiceState.PAUSED) {
            return;
        }

        if (state == WindowsServiceState.UNINSTALLED) {
            throw new ServiceManagerException("Cannot start service which is not yet installed");
        }

        final Path exe = prunsrvExe.get();
        final List<String> args = Arrays.asList(exe.toAbsolutePath().toString(), "start", SERVICE_NAME);
        try {
            final Tuple2<Integer, String> execResult = run(args, true);
            final int exitCode = execResult._1;
            final String result = execResult._2;

            if (exitCode != 0) {
                LOG.error("Could not start service, exitCode={}, output='{}'", exitCode, result);
                throw new ServiceManagerException("Could not start service, exitCode=" + exitCode + ", output='" + result + "'");
            }
        } catch (final IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.error("Could not start service: {}", e.getMessage(), e);
            throw new ServiceManagerException("Could not start service: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isRunning() {
        try {
            return getState() == WindowsServiceState.RUNNING;
        } catch (final ServiceManagerException e) {
            LOG.error("Could not determine if service is running: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void stop() throws ServiceManagerException {
        final WindowsServiceState state = getState();
        if (state == WindowsServiceState.UNINSTALLED) {
            throw new ServiceManagerException("Cannot stop service which is not yet installed");
        }

        if (state != WindowsServiceState.RUNNING) {
            return;
        }

        final Path exe = prunsrvExe.get();
        final List<String> args = Arrays.asList(exe.toAbsolutePath().toString(), "stop", SERVICE_NAME);
        try {
            final Tuple2<Integer, String> execResult = run(args, true);
            final int exitCode = execResult._1;
            final String result = execResult._2;

            if (exitCode != 0) {
                LOG.error("Could not stop service, exitCode={}, output='{}'", exitCode, result);
                throw new ServiceManagerException("Could not stop service, exitCode=" + exitCode + ", output='" + result + "'");
            }
        } catch (final IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.error("Could not stop service: {}", e.getMessage(), e);
            throw new ServiceManagerException("Could not stop service: " + e.getMessage(), e);
        }
    }

    @Override
    public void showNativeServiceManagementConsole() throws UnsupportedOperationException, ServiceManagerException {
        final List<String> args = Arrays.asList("cmd.exe", "/c", "services.msc");
        final ProcessBuilder pb = new ProcessBuilder(args);
        try {
            pb.start();
        } catch (final IOException e) {
            throw new ServiceManagerException(e.getMessage(), e);
        }
    }

    /**
     * Try to find jvm.dll, which should either reside in `bin/client` or `bin/server` below
     * JAVA_HOME. Autodetection does not seem to work with OpenJDK-based Java distributions.
     *
     * @return Path to jvm.dll or empty Optional
     */
    private Optional<String> findJvm() {
        final Path javaHome = Paths.get(System.getProperty("java.home")).toAbsolutePath();
        Path jvm = javaHome.resolve("bin").resolve("client").resolve("jvm.dll");
        if (Files.exists(jvm)) {
            return Optional.of(jvm.toString());
        }
        jvm = javaHome.resolve("bin").resolve("server").resolve("jvm.dll");
        if (Files.exists(jvm)) {
            return Optional.of(jvm.toString());
        }
        return Optional.empty();
    }

    private WindowsServiceState getState() throws ServiceManagerException {
        try {
            final List<String> args = Arrays.asList(SC_EXE, "query", SERVICE_NAME);
            final Tuple2<Integer, String> execResult = run(args, false);
            final int exitCode = execResult._1;
            final String result = execResult._2;

            if (exitCode == 1060) {
                return WindowsServiceState.UNINSTALLED;
            }
            if (exitCode != 0) {
                throw new ServiceManagerException("Could not query service status, exitCode=" + exitCode + ", output='" + result + "'");
            }

            if (result.contains("STOPPED")) {
                return WindowsServiceState.STOPPED;
            }
            if (result.contains("RUNNING")) {
                return WindowsServiceState.RUNNING;
            }
            if (result.contains("PAUSED")) {
                return WindowsServiceState.PAUSED;
            }

            throw new ServiceManagerException("Could not determine service status, exitCode=" + exitCode + ", output='" + result + "'");

        } catch (final IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ServiceManagerException(e);
        }
    }

    private Tuple2<Integer, String> run(List<String> args, final boolean elevated) throws IOException, InterruptedException {

        if (elevated) {
            final List<String> elevatedArgs = new ArrayList<>();
            elevatedArgs.add("cmd.exe");
            elevatedArgs.add("/c");
            elevatedArgs.addAll(args);

            args = elevatedArgs;
        }

        if (LOG.isDebugEnabled()) {
            final StringBuilder buf = new StringBuilder("Executing: [");
            for (int i = 0; i < args.size(); i++) {
                buf.append('"');
                buf.append(args.get(i));
                buf.append('"');
                if (i != args.size() - 1) {
                    buf.append(", ");
                }
            }
            buf.append(']');
            LOG.debug(buf.toString());
        }

        final ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(existHome.toFile());
        pb.redirectErrorStream(true);

        final Process process = pb.start();
        final StringBuilder output = new StringBuilder();
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),
                UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(System.getProperty("line.separator")).append(line);
            }
        }
        final int exitValue = process.waitFor();
        return Tuple(exitValue, output.toString());
    }

    /**
     * Transform the supplied memory string into a string
     * that is compatible with the Java command line arguments for -Xms and -Xmx.
     *
     * See <a href="https://docs.oracle.com/javase/8/docs/technotes/tools/windows/java.html#BABHDABI">Java - Non-Standard Options</a>.
     *
     * @param memoryString the memory string.
     *
     * @return a memory string compatible with java.exe.
     */
    static Optional<String> asJavaCmdlineMemoryString(final String memoryString) {
        // should optionally end in g|G|m|M|k|K
        final Matcher mtcJavaCmdlineMemoryString = JAVA_CMDLINE_MEMORY_STRING.matcher(memoryString);
        if (!mtcJavaCmdlineMemoryString.matches()) {
            // invalid java cmdline memory string
            return Optional.empty();
        }

        final String value = mtcJavaCmdlineMemoryString.group(1);
        @Nullable final String mnemonic = mtcJavaCmdlineMemoryString.group(2);

        // no mnemonic supplied, assume `m` for megabytes
        return Optional.of(value + Objects.requireNonNullElse(mnemonic, "m"));

        // valid mnemonic supplied, so return as is (excluding any additional cruft)
    }

    /**
     * Converts a memory string for the Java command line arguments -Xms or -Xmx, into
     * a memory string that is understood by prunsrv.exe.
     * prunsrv.exe expects an integer in megabytes.
     *
     * @param javaCmdlineMemoryString the memory strig as would be given to the Java command line.
     *
     * @return a memory string suitable for use with prunsrv.exe.
     */
    static Optional<String> asPrunSrvMemoryString(final String javaCmdlineMemoryString) {
        // should optionally end in g|G|m|M|k|K
        final Matcher mtcJavaCmdlineMemoryString = JAVA_CMDLINE_MEMORY_STRING.matcher(javaCmdlineMemoryString);
        if (!mtcJavaCmdlineMemoryString.matches()) {
            // invalid java cmdline memory string
            return Optional.empty();
        }

        long value = Integer.valueOf(mtcJavaCmdlineMemoryString.group(1)).longValue();
        @Nullable String mnemonic = mtcJavaCmdlineMemoryString.group(2);
        if (mnemonic == null) {
            mnemonic = "m";
        }

        switch (mnemonic.toLowerCase()) {
            case "k":
                value = value / 1024;
                break;

            case "g":
                value = value * 1024;
                break;

            case "m":
            default:
                // do nothing, megabytes is the default!
                break;
        }

        return Optional.of(Long.toString(value));
    }
}
