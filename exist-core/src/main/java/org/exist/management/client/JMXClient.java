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
 */
public class JMXClient {

    private MBeanServerConnection connection;
    private String instance;

    public JMXClient(String instanceName) {
        this.instance = instanceName;
    }

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

    public void memoryStats() {
        try {
            final ObjectName name = new ObjectName("java.lang:type=Memory");
            final CompositeData composite = (CompositeData) connection.getAttribute(name, "HeapMemoryUsage");
            if (composite != null) {
                echo("\nMEMORY:");
                echo(String.format("Current heap: %,12d k        Committed memory:  %,12d k",
                        ((Long)composite.get("used")) / 1024, ((Long)composite.get("committed")) / 1024));
                echo(String.format("Max memory:   %,12d k", ((Long)composite.get("max")) / 1024));
            }
        } catch (final Exception e) {
            error(e);
        }
    }

    public void instanceStats() {
        try {
            echo("\nINSTANCE:");
            final ObjectName name = new ObjectName("org.exist.management." + instance + ":type=Database");
            final Long memReserved = (Long) connection.getAttribute(name, "ReservedMem");
            echo(String.format("%25s: %10d k", "Reserved memory", memReserved / 1024));
            final Long memCache = (Long) connection.getAttribute(name, "CacheMem");
            echo(String.format("%25s: %10d k", "Cache memory", memCache / 1024));
            final Long memCollCache = (Long) connection.getAttribute(name, "CollectionCacheMem");
            echo(String.format("%25s: %10d k", "Collection cache memory", memCollCache / 1024));

            final String cols[] = { "MaxBrokers", "AvailableBrokers", "ActiveBrokers" };
            echo(String.format("\n%17s %17s %17s", cols[0], cols[1], cols[2]));
            final AttributeList attrs = connection.getAttributes(name, cols);
            final Object values[] = getValues(attrs);
            echo(String.format("%17d %17d %17d", values[0], values[1], values[2]));

            final TabularData table = (TabularData) connection.getAttribute(name, "ActiveBrokersMap");
            if (!table.isEmpty()) {
                echo("\nCurrently active threads:");
            }

            for (Object o : table.values()) {
                final CompositeData data = (CompositeData) o;
                echo(String.format("\t%20s: %3d", data.get("owner"), data.get("referenceCount")));
            }
        } catch (final Exception e) {
            error(e);
        }
    }

    public void cacheStats() {
        try {
            ObjectName name = new ObjectName("org.exist.management." + instance + ":type=CacheManager");
            String cols[] = { "MaxTotal", "CurrentSize" };
            AttributeList attrs = connection.getAttributes(name, cols);
            Object values[] = getValues(attrs);
            echo(String.format("\nCACHE [%8d pages max. / %8d pages allocated]", values[0], values[1]));

            final Set<ObjectName> beans = connection.queryNames(new ObjectName("org.exist.management." + instance + ":type=CacheManager.Cache,*"), null);
            cols = new String[] {"Type", "FileName", "Size", "Used", "Hits", "Fails"};
            echo(String.format("%10s %20s %10s %10s %10s %10s", cols[0], cols[1], cols[2], cols[3], cols[4], cols[5]));
            for (ObjectName bean : beans) {
                name = bean;
                attrs = connection.getAttributes(name, cols);
                values = getValues(attrs);
                echo(String.format("%10s %20s %,10d %,10d %,10d %,10d", values[0], values[1], values[2], values[3], values[4], values[5]));
            }
            
            echo("");
           name = new ObjectName("org.exist.management." + instance + ":type=CollectionCacheManager");
            cols = new String[] { "MaxTotal", "CurrentSize" };
            attrs = connection.getAttributes(name, cols);
            values = getValues(attrs);
           echo(String.format("Collection Cache: %10d k max / %10d k allocated",
               ((Long)values[0] / 1024), ((Long)values[1] / 1024)));
        } catch (final Exception e) {
            error(e);
        }
    }

    public void lockTable() {
        echo("\nList of threads currently waiting for a lock:");
        echo("-----------------------------------------------");
        try {
            final TabularData table = (TabularData) connection.getAttribute(new ObjectName("org.exist.management:type=LockManager"), "WaitingThreads");
            for (Object o : table.values()) {
                final CompositeData data = (CompositeData) o;
                echo("Thread " + data.get("waitingThread"));
                echo(String.format("%20s: %s", "Lock type", data.get("lockType")));
                echo(String.format("%20s: %s", "Lock mode", data.get("lockMode")));
                echo(String.format("%20s: %s", "Lock id", data.get("id")));
                echo(String.format("%20s: %s", "Held by", Arrays.toString((String[]) data.get("owner"))));
                final String[] readers = (String[]) data.get("waitingForRead");
                if (readers.length > 0) {
                    echo(String.format("%20s: %s", "Wait for read", Arrays.toString(readers)));
                }
                final String[] writers = (String[]) data.get("waitingForWrite");
                if (writers.length > 0) {
                    echo(String.format("%20s: %s", "Wait for write", Arrays.toString(writers)));
                }
            }
        } catch (final MBeanException | AttributeNotFoundException | InstanceNotFoundException | ReflectionException | IOException | MalformedObjectNameException e) {
            error(e);
        }
    }

    public void sanityReport() {
        echo("\nSanity report");
        echo("-----------------------------------------------");
        try {
            final ObjectName name = new ObjectName("org.exist.management." + instance + ".tasks:type=SanityReport");
            final String status = (String) connection.getAttribute(name, "Status");
            final Date lastCheckStart = (Date) connection.getAttribute(name, "LastCheckStart");
            final Date lastCheckEnd = (Date) connection.getAttribute(name, "LastCheckEnd");
            echo(String.format("%22s: %s", "Status", status));
            echo(String.format("%22s: %s", "Last check start", lastCheckStart));
            echo(String.format("%22s: %s", "Last check end", lastCheckEnd));
            if (lastCheckStart != null && lastCheckEnd != null)
                {echo(String.format("%22s: %dms", "Check took", (lastCheckEnd.getTime() - lastCheckStart.getTime())));}

            final TabularData table = (TabularData)
                    connection.getAttribute(name, "Errors");
            for (Object o : table.values()) {
                final CompositeData data = (CompositeData) o;
                echo(String.format("%22s: %s", "Error code", data.get("errcode")));
                echo(String.format("%22s: %s", "Description", data.get("description")));
            }
        } catch (final MBeanException | AttributeNotFoundException | InstanceNotFoundException | ReflectionException | IOException | MalformedObjectNameException e) {
            error(e);
        }
    }

    public void jobReport() {
        echo("\nRunning jobs report");
        echo("-----------------------------------------------");
        try {
            final ObjectName name = new ObjectName("org.exist.management." + instance + ":type=ProcessReport");

            TabularData table = (TabularData)
                    connection.getAttribute(name, "RunningJobs");
            String[] cols = new String[] { "ID", "Action", "Info" };
            echo(String.format("%15s %30s %30s", cols[0], cols[1], cols[2]));
            for (Object value : table.values()) {
                final CompositeData data = (CompositeData) value;
                echo(String.format("%15s %30s %30s", data.get("id"), data.get("action"), data.get("info")));
            }

            echo("\nRunning queries");
            echo("-----------------------------------------------");
            table = (TabularData)
                    connection.getAttribute(name, "RunningQueries");
            cols = new String[] { "ID", "Type", "Key", "Terminating" };
            echo(String.format("%10s %10s %30s %s", cols[0], cols[1], cols[2], cols[3]));
            for (Object o : table.values()) {
                final CompositeData data = (CompositeData) o;
                echo(String.format("%15s %15s %30s %6s", data.get("id"), data.get("sourceType"), data.get("sourceKey"), data.get("terminating")));
            }
        } catch (final MBeanException | AttributeNotFoundException | InstanceNotFoundException | ReflectionException | IOException | MalformedObjectNameException e) {
            error(e);
        }
    }

    private Object[] getValues(AttributeList attribs) {
        final Object[] v = new Object[attribs.size()];
        for (int i = 0; i < attribs.size(); i++) {
            v[i] = ((Attribute)attribs.get(i)).getValue();
        }
        return v;
    }

    private void echo(String msg) {
        System.out.println(msg);
    }
    
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
