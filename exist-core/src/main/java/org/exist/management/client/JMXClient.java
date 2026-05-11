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
package org.exist.management.client;

import org.exist.start.CompatibleJavaVersionCheck;
import org.exist.start.StartException;
import org.exist.util.OSUtil;
import org.exist.util.SystemExitCodes;
import se.softhouse.jargo.Argument;
import se.softhouse.jargo.ArgumentException;
import se.softhouse.jargo.CommandLineParser;
import se.softhouse.jargo.ParsedArguments;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.*;

import static org.exist.util.ArgumentUtil.getBool;
import static se.softhouse.jargo.Arguments.*;

/**
 * Command-line JMX client for monitoring a running eXist-db instance.
 * <p>
 * Connects to an eXist-db JMX endpoint via RMI and prints statistics about
 * memory usage, cache state, lock contention, sanity reports, and running jobs.
 * Intended to be invoked via {@code bin/jmxclient.sh}.
 * </p>
 */
public class JMXClient {

    private MBeanServerConnection connection;
    private String instance;

    /**
     * Creates a new JMXClient targeting the named eXist-db database instance.
     *
     * @param instanceName the JMX instance name used in MBean object names
     *                     (e.g. {@code "exist"} for {@code org.exist.management.exist:…})
     */
    public JMXClient(String instanceName) {
        this.instance = instanceName;
    }

    /**
     * Opens a JMX/RMI connection to the specified server.
     *
     * @param address hostname or IP address of the eXist-db server
     * @param port    RMI registry port (typically {@code 1099})
     * @throws IOException if the connection cannot be established
     */
    public void connect(String address,int port) throws IOException {
        final JMXServiceURL url =
                new JMXServiceURL("service:jmx:rmi:///jndi/rmi://"+address+":" + port + "/jmxrmi");
        final Map<String, String[]> env = new HashMap<>();
        final String[] creds = {"guest", "guest"};
        env.put(JMXConnector.CREDENTIALS, creds);

        final JMXConnector jmxc = JMXConnectorFactory.connect(url, env);
        connection = jmxc.getMBeanServerConnection();
        echo("Connected to MBean server.");
    }

    /**
     * Prints JVM heap memory statistics (used, committed, and max heap) to stdout.
     * Reads the {@code java.lang:type=Memory} MBean.
     */
    public void memoryStats() {
        try {
            final ObjectName name = new ObjectName("java.lang:type=Memory");
            final CompositeData composite = (CompositeData) connection.getAttribute(name, "HeapMemoryUsage");
            if (composite != null) {
                echo("\nMEMORY:");
                echo("Current heap: %,12d k        Committed memory:  %,12d k".formatted(
                        ((Long)composite.get("used")) / 1024, ((Long)composite.get("committed")) / 1024));
                echo("Max memory:   %,12d k".formatted(((Long)composite.get("max")) / 1024));
            }
        } catch (final Exception e) {
            error(e);
        }
    }

    /**
     * Prints eXist-db instance statistics to stdout, including reserved/cache memory,
     * broker pool counts, and any currently active broker threads.
     * Reads the {@code org.exist.management.&lt;instance&gt;:type=Database} MBean.
     */
    public void instanceStats() {
        try {
            echo("\nINSTANCE:");
            final ObjectName name = new ObjectName("org.exist.management." + instance + ":type=Database");
            final Long memReserved = (Long) connection.getAttribute(name, "ReservedMem");
            echo("%25s: %10d k".formatted("Reserved memory", memReserved != null ? memReserved / 1024 : 0L));
            final Long memCache = (Long) connection.getAttribute(name, "CacheMem");
            echo("%25s: %10d k".formatted("Cache memory", memCache != null ? memCache / 1024 : 0L));
            final Long memCollCache = (Long) connection.getAttribute(name, "CollectionCacheMem");
            echo("%25s: %10d k".formatted("Collection cache memory", memCollCache != null ? memCollCache / 1024 : 0L));

            final String cols[] = { "MaxBrokers", "AvailableBrokers", "ActiveBrokers" };
            echo("\n%17s %17s %17s".formatted(cols[0], cols[1], cols[2]));
            final AttributeList attrs = connection.getAttributes(name, cols);
            final Object values[] = getValues(attrs, cols);
            echo("%17d %17d %17d".formatted(
                    values[0] != null ? values[0] : 0,
                    values[1] != null ? values[1] : 0,
                    values[2] != null ? values[2] : 0));

            final Object activeBrokersRaw = connection.getAttribute(name, "ActiveBrokersMap");
            final CompositeData[] activeBrokers = activeBrokersRaw instanceof CompositeData[]
                    ? (CompositeData[]) activeBrokersRaw
                    : activeBrokersRaw instanceof TabularData
                        ? ((TabularData) activeBrokersRaw).values().toArray(new CompositeData[0])
                        : new CompositeData[0];
            if (activeBrokers.length > 0) {
                echo("\nCurrently active threads:");
            }

            for (final CompositeData data : activeBrokers) {
                echo("\t%20s: %3d".formatted(data.get("owner"), data.get("referenceCount")));
            }
        } catch (final Exception e) {
            error(e);
        }
    }

    /**
     * Prints cache statistics to stdout, including per-cache type, file name, size,
     * usage, hits, and failures, as well as collection-cache totals.
     * Reads the {@code CacheManager} and {@code CollectionCacheManager} MBeans.
     */
    public void cacheStats() {
        try {
            ObjectName name = new ObjectName("org.exist.management." + instance + ":type=CacheManager");
            String cols[] = { "MaxTotal", "CurrentSize" };
            AttributeList attrs = connection.getAttributes(name, cols);
            Object values[] = getValues(attrs, cols);
            echo("\nCACHE [%8d pages max. / %8d pages allocated]".formatted(
                    values[0] != null ? values[0] : 0,
                    values[1] != null ? values[1] : 0));

            final Set<ObjectName> beans = connection.queryNames(new ObjectName("org.exist.management." + instance + ":type=CacheManager.Cache,*"), null);
            cols = new String[] {"Type", "CacheName", "Size", "Used", "Hits", "Fails"};
            echo("%10s %20s %10s %10s %10s %10s".formatted(cols[0], "FileName", cols[2], cols[3], cols[4], cols[5]));
            final List<Object[]> cacheRows = new ArrayList<>();
            for (ObjectName bean : beans) {
                attrs = connection.getAttributes(bean, cols);
                cacheRows.add(getValues(attrs, cols));
            }
            cacheRows.sort(Comparator
                    .comparing((Object[] r) -> r[1] != null ? r[1].toString() : "")
                    .thenComparing(r -> r[0] != null ? r[0].toString() : ""));
            for (final Object[] row : cacheRows) {
                echo("%10s %20s %,10d %,10d %,10d %,10d".formatted(
                        row[0] != null ? row[0] : "N/A",
                        row[1] != null ? row[1] : "N/A",
                        row[2] != null ? row[2] : 0L,
                        row[3] != null ? row[3] : 0L,
                        row[4] != null ? row[4] : 0L,
                        row[5] != null ? row[5] : 0L));
            }
            
            echo("");
            name = new ObjectName("org.exist.management." + instance + ":type=CollectionCache");
            cols = new String[] { "MaxCacheSize" };
            attrs = connection.getAttributes(name, cols);
            values = getValues(attrs, cols);
            echo("Collection Cache: %10d k max".formatted(
                    values[0] != null ? (Long)values[0] / 1024 : 0L));
        } catch (final Exception e) {
            error(e);
        }
    }

    /**
     * Prints the list of threads currently waiting for a lock to stdout.
     * Useful for diagnosing deadlocks. During normal operation the list is empty.
     * Reads the {@code org.exist.management.&lt;instance&gt;:type=LockTable} MBean.
     */
    public void lockTable() {
        echo("\nList of threads currently waiting for a lock:");
        echo("-----------------------------------------------");
        try {
            final ObjectName name = new ObjectName("org.exist.management." + instance + ":type=LockTable");
            // Over JMX remote, Map<String, Map<LockType, List<LockModeOwner>>> is serialized as nested TabularData:
            //   outer TabularData: key=lockId (String), value=TabularData
            //     inner TabularData: key=LockType name (String), value=CompositeData[]
            //       each CompositeData: fields lockMode, ownerThread, trace
            final TabularData attempting = (TabularData) connection.getAttribute(name, "Attempting");
            if (attempting == null || attempting.isEmpty()) {
                echo("(none)");
                return;
            }
            for (final Object outerObj : attempting.values()) {
                final CompositeData outerRow = (CompositeData) outerObj;
                final String lockId = (String) outerRow.get("key");
                final TabularData innerTable = (TabularData) outerRow.get("value");
                for (final Object innerObj : innerTable.values()) {
                    final CompositeData innerRow = (CompositeData) innerObj;
                    final String lockType = (String) innerRow.get("key");
                    final Object[] modeOwners = (Object[]) innerRow.get("value");
                    for (final Object modeOwnerObj : modeOwners) {
                        final CompositeData modeOwner = (CompositeData) modeOwnerObj;
                        echo("%20s: %s".formatted("Lock id", lockId));
                        echo("%20s: %s".formatted("Lock type", lockType));
                        echo("%20s: %s".formatted("Lock mode", modeOwner.get("lockMode")));
                        echo("%20s: %s".formatted("Waiting thread", modeOwner.get("ownerThread")));
                    }
                }
            }
        } catch (final MBeanException | AttributeNotFoundException | InstanceNotFoundException | ReflectionException | IOException | MalformedObjectNameException e) {
            error(e);
        }
    }

    /**
     * Prints the latest sanity-check report to stdout, including status,
     * start/end timestamps, duration, and any reported errors.
     * Reads the {@code org.exist.management.&lt;instance&gt;.tasks:type=SanityReport} MBean.
     */
    public void sanityReport() {
        echo("\nSanity report");
        echo("-----------------------------------------------");
        try {
            final ObjectName name = new ObjectName("org.exist.management." + instance + ".tasks:type=SanityReport");
            final String status = (String) connection.getAttribute(name, "Status");
            final Date lastCheckStart = (Date) connection.getAttribute(name, "LastCheckStart");
            final Date lastCheckEnd = (Date) connection.getAttribute(name, "LastCheckEnd");
            echo("%22s: %s".formatted("Status", status));
            echo("%22s: %s".formatted("Last check start", lastCheckStart != null ? lastCheckStart : "N/A"));
            echo("%22s: %s".formatted("Last check end", lastCheckEnd != null ? lastCheckEnd : "N/A"));
            if (lastCheckStart != null && lastCheckEnd != null)
                {echo("%22s: %dms".formatted("Check took", (lastCheckEnd.getTime() - lastCheckStart.getTime())));}

            final CompositeData[] errors = (CompositeData[])
                    connection.getAttribute(name, "Errors");
            if (errors != null) {
                for (final CompositeData data : errors) {
                    echo("%22s: %s".formatted("Error code", data.get("errcode")));
                    echo("%22s: %s".formatted("Description", data.get("description")));
                }
            }
        } catch (final MBeanException | AttributeNotFoundException | InstanceNotFoundException | ReflectionException | IOException | MalformedObjectNameException e) {
            error(e);
        }
    }

    /**
     * Prints the currently running jobs and XQuery processes to stdout.
     * Reads the {@code org.exist.management.&lt;instance&gt;:type=ProcessReport} MBean.
     */
    public void jobReport() {
        echo("\nRunning jobs report");
        echo("-----------------------------------------------");
        try {
            final ObjectName name = new ObjectName("org.exist.management." + instance + ":type=ProcessReport");

            TabularData table = (TabularData)
                    connection.getAttribute(name, "RunningJobs");
            String[] cols = new String[] { "ID", "Action", "Info" };
            echo("%15s %30s %30s".formatted(cols[0], cols[1], cols[2]));
            if (table.isEmpty()) {
                echo("(none)");
            } else {
                for (Object value : table.values()) {
                    final CompositeData row = (CompositeData) value;
                    final CompositeData data = (CompositeData) row.get("value");
                    echo("%15s %30s %30s".formatted(data.get("id"), data.get("action"), data.get("info")));
                }
            }

            echo("\nRunning queries");
            echo("-----------------------------------------------");
            table = (TabularData)
                    connection.getAttribute(name, "RunningQueries");
            cols = new String[] { "ID", "Type", "Key", "Terminating" };
            echo("%10s %10s %30s %s".formatted(cols[0], cols[1], cols[2], cols[3]));
            if (table.isEmpty()) {
                echo("(none)");
            } else {
                for (Object o : table.values()) {
                    final CompositeData row = (CompositeData) o;
                    final CompositeData data = (CompositeData) row.get("value");
                    echo("%15s %15s %30s %6s".formatted(data.get("id"), data.get("sourceType"), data.get("sourceKey"), data.get("terminating")));
                }
            }
        } catch (final MBeanException | AttributeNotFoundException | InstanceNotFoundException | ReflectionException | IOException | MalformedObjectNameException e) {
            error(e);
        }
    }

    /**
     * Extracts attribute values from an {@link AttributeList} in positional order.
     *
     * @param attribs the attribute list returned by
     *                {@link MBeanServerConnection#getAttributes}
     * @return an array of attribute values in the same order as {@code attribs}
     */
    private Object[] getValues(AttributeList attribs) {
        final Object[] v = new Object[attribs.size()];
        for (int i = 0; i < attribs.size(); i++) {
            v[i] = ((Attribute)attribs.get(i)).getValue();
        }
        return v;
    }

    /**
     * Extracts attribute values from an {@link AttributeList} by name, returning
     * a result array aligned to {@code cols}.
     * <p>
     * If the server did not return a value for a requested attribute name the
     * corresponding element in the result array will be {@code null} rather than
     * causing an {@link ArrayIndexOutOfBoundsException}.
     * </p>
     *
     * @param attribs the attribute list returned by
     *                {@link MBeanServerConnection#getAttributes}
     * @param cols    the attribute names whose values should be extracted,
     *                in the desired output order
     * @return an array of the same length as {@code cols} with each element
     *         holding the corresponding attribute value, or {@code null} if absent
     */
    private Object[] getValues(AttributeList attribs, String[] cols) {
        final Map<String, Object> byName = new LinkedHashMap<>();
        for (int i = 0; i < attribs.size(); i++) {
            final Attribute a = (Attribute) attribs.get(i);
            byName.put(a.getName(), a.getValue());
        }
        final Object[] v = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            v[i] = byName.get(cols[i]);
        }
        return v;
    }

    /**
     * Writes a line of output to {@link System#out}.
     *
     * @param msg the message to print
     */
    private void echo(String msg) {
        System.out.println(msg);
    }
    
    /**
     * Writes an error message and stack trace to {@link System#err}.
     *
     * @param e the exception to report
     */
    private void error(Exception e) {
        System.err.println("ERROR: " + e.getMessage());
        e.printStackTrace();
    }

    private static final int DEFAULT_PORT = 1099;
    private static final int DEFAULT_WAIT_TIME = 0;

    /* general arguments */
    private static final Argument<?> helpArg = helpArgument("-h", "--help");

    /* connection arguments */
    private static final Argument<String> addressArg = stringArgument("-a", "--address")
            .description("RMI address of the server")
            .defaultValue("localhost")
            .build();
    private static final Argument<Integer> portArg = integerArgument("-p", "--port")
            .description("RMI port of the server")
            .defaultValue(DEFAULT_PORT)
            .build();
    private static final Argument<String> instanceArg = stringArgument("-i", "--instance")
            .description("The ID of the database instance to connect to")
            .defaultValue("exist")
            .build();
    private static final Argument<Integer> waitArg = integerArgument("-w", "--wait")
            .description("while displaying server statistics: keep retrieving statistics, but wait the specified number of seconds between calls.")
            .defaultValue(DEFAULT_WAIT_TIME)
            .build();

    /* display mode options */
    private static final Argument<Boolean> cacheDisplayArg = optionArgument("-c", "--cache")
            .description("displays server statistics on cache and memory usage.")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> locksDisplayArg = optionArgument("-l", "--locks")
            .description("lock manager: display locking information on all threads currently waiting for a lock on a resource or collection. Useful to debug deadlocks. During normal operation, the list will usually be empty (means: no blocked threads).")
            .defaultValue(false)
            .build();

    /* display info options */
    private static final Argument<Boolean> dbInfoArg = optionArgument("-d", "--db")
            .description("display general info about the db instance.")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> memoryInfoArg = optionArgument("-m", "--memory")
            .description("display info on free and total memory. Can be combined with other parameters.")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> sanityCheckInfoArg = optionArgument("-s", "--report")
            .description("retrieve sanity check report from the db")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> jobsInfoArg = optionArgument("-j", "--jobs")
            .description("retrieve sanity check report from the db")
            .defaultValue(false)
            .build();

    private enum Mode {
        STATS,
        LOCKS
    }

    /**
     * Entry point for the {@code jmxclient.sh} command.
     * Parses command-line arguments and delegates to {@link #process(ParsedArguments)}.
     *
     * @param args command-line arguments
     */
    @SuppressWarnings("unchecked")
	public static void main(final String[] args) {
        try {
            CompatibleJavaVersionCheck.checkForCompatibleJavaVersion();

            final ParsedArguments arguments = CommandLineParser
                    .withArguments(addressArg, portArg, instanceArg, waitArg)
                    .andArguments(cacheDisplayArg, locksDisplayArg)
                    .andArguments(dbInfoArg, memoryInfoArg, sanityCheckInfoArg, jobsInfoArg)
                    .andArguments(helpArg)
                    .programName("jmxclient" + (OSUtil.isWindows() ? ".bat" : ".sh"))
                    .parse(args);

            process(arguments);
        } catch (final StartException e) {
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                System.err.println(e.getMessage());
            }
            System.exit(e.getErrorCode());
        } catch (final ArgumentException e) {
            System.out.println(e.getMessageAndUsage());
            System.exit(SystemExitCodes.INVALID_ARGUMENT_EXIT_CODE);
        }

    }

    /**
     * Processes parsed command-line arguments: connects to the JMX server and
     * repeatedly prints the requested statistics until the optional wait interval
     * expires or a single pass completes.
     *
     * @param arguments the parsed command-line arguments
     */
    private static void process(final ParsedArguments arguments) {
        final String address = arguments.get(addressArg);
        final int port = Optional.ofNullable(arguments.get(portArg)).orElse(DEFAULT_PORT);
        final String dbInstance = arguments.get(instanceArg);
        final long waitTime = Optional.ofNullable(arguments.get(waitArg)).orElse(DEFAULT_WAIT_TIME);

        Mode mode = Mode.STATS;
        if(getBool(arguments, cacheDisplayArg)) {
            mode = Mode.STATS;
        }
        if(getBool(arguments, locksDisplayArg)) {
            mode = Mode.LOCKS;
        }

        final boolean displayInstance = getBool(arguments, dbInfoArg);
        final boolean displayMem = getBool(arguments, memoryInfoArg);
        final boolean displayReport = getBool(arguments, sanityCheckInfoArg);
        final boolean jobReport = getBool(arguments, jobsInfoArg);

        try {
            final JMXClient stats = new JMXClient(dbInstance);
            stats.connect(address,port);
            stats.memoryStats();
            while (true) {
                switch (mode) {
                    case STATS :
                        stats.cacheStats();
                        break;
                    case LOCKS :
                        stats.lockTable();
                        break;
                }
                if (displayInstance) {stats.instanceStats();}
                if (displayMem) {stats.memoryStats();}
                if (displayReport) {stats.sanityReport();}
                if (jobReport) {stats.jobReport();}
                if (waitTime > 0) {
                    synchronized (stats) {
                        try {
                            stats.wait(waitTime);
                        } catch (final InterruptedException e) {
                            System.err.println("INTERRUPTED: " + e.getMessage());
                        }
                    }
                } else
                    {return;}
            }
        } catch (final IOException e) {
            e.printStackTrace(); 
        } 
    }
}
