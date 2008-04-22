/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id: JMXClient.java 7641 2008-04-20 19:39:58Z wolfgang_m $
 */
package org.exist.management.client;

import org.apache.avalon.excalibur.cli.CLArgsParser;
import org.apache.avalon.excalibur.cli.CLOption;
import org.apache.avalon.excalibur.cli.CLOptionDescriptor;
import org.apache.avalon.excalibur.cli.CLUtil;

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
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 */
public class JMXClient {

    private MBeanServerConnection connection;
    private String instance;

    public JMXClient(String instanceName) {
        this.instance = instanceName;
    }

    public void connect(String address,int port) throws IOException {
        JMXServiceURL url =
                new JMXServiceURL("service:jmx:rmi:///jndi/rmi://"+address+":" + port + "/jmxrmi");
        Map env = new HashMap();
        String[] creds = {"guest", "guest"};
        env.put(JMXConnector.CREDENTIALS, creds);

        JMXConnector jmxc = JMXConnectorFactory.connect(url, env);
        connection = jmxc.getMBeanServerConnection();
        echo("Connected to MBean server.");
    }

    public void memoryStats() {
        try {
            ObjectName name = new ObjectName("java.lang:type=Memory");
            CompositeData composite = (CompositeData) connection.getAttribute(name, "HeapMemoryUsage");
            if (composite != null) {
                echo("\nMEMORY:");
                echo(String.format("Current heap: %,12d k        Committed memory:  %,12d k",
                        ((Long)composite.get("used")) / 1024, ((Long)composite.get("committed")) / 1024));
                echo(String.format("Max memory:   %,12d k", ((Long)composite.get("max")) / 1024));
            }
        } catch (Exception e) {
            error(e);
        }
    }

    public void instanceStats() {
        try {
            echo("\nINSTANCE:");
            ObjectName name = new ObjectName("org.exist.management." + instance + ":type=Database");
            Long memReserved = (Long) connection.getAttribute(name, "ReservedMem");
            echo(String.format("%25s: %10d k", "Reserved memory", memReserved.longValue() / 1024));
            Long memCache = (Long) connection.getAttribute(name, "CacheMem");
            echo(String.format("%25s: %10d k", "Cache memory", memCache.longValue() / 1024));
            Long memCollCache = (Long) connection.getAttribute(name, "CollectionCacheMem");
            echo(String.format("%25s: %10d k", "Collection cache memory", memCollCache.longValue() / 1024));

            String cols[] = { "MaxBrokers", "AvailableBrokers", "ActiveBrokers" };
            echo(String.format("\n%17s %17s %17s", cols[0], cols[1], cols[2]));
            AttributeList attrs = connection.getAttributes(name, cols);
            Object values[] = getValues(attrs);
            echo(String.format("%17d %17d %17d", values[0], values[1], values[2]));

            TabularData table = (TabularData) connection.getAttribute(name, "ActiveBrokersMap");
            if (table.size() > 0)
                echo("\nCurrently active threads:");
            for (Iterator i = table.values().iterator(); i.hasNext(); ) {
                CompositeData data = (CompositeData) i.next();
                echo(String.format("\t%20s: %3d", data.get("owner"), data.get("referenceCount")));
            }
        } catch (Exception e) {
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

            Set beans = connection.queryNames(new ObjectName("org.exist.management." + instance + ":type=CacheManager.Cache,*"), null);
            cols = new String[] {"Type", "FileName", "Size", "Used", "Hits", "Fails"};
            echo(String.format("%10s %20s %10s %10s %10s %10s", cols[0], cols[1], cols[2], cols[3], cols[4], cols[5]));
            for (Iterator i = beans.iterator(); i.hasNext();) {
                name = (ObjectName) i.next();
                attrs = connection.getAttributes(name, cols);
                values = getValues(attrs);
                echo(String.format("%10s %20s %,10d %,10d %,10d %,10d", values[0], values[1], values[2], values[3], values[4], values[5]));
           }
        } catch (IOException e) {
            error(e);
        } catch (MalformedObjectNameException e) {
            error(e);
        } catch (InstanceNotFoundException e) {
            error(e);
        } catch (ReflectionException e) {
            error(e);
        } 
    }

    public void lockTable() {
        echo("\nList of threads currently waiting for a lock:");
        echo("-----------------------------------------------");
        try {
            TabularData table = (TabularData) connection.getAttribute(new ObjectName("org.exist.management:type=LockManager"), "WaitingThreads");
            for (Iterator i = table.values().iterator(); i.hasNext(); ) {
                CompositeData data = (CompositeData) i.next();
                echo("Thread " + data.get("waitingThread"));
                echo(String.format("%20s: %s", "Lock type", data.get("lockType")));
                echo(String.format("%20s: %s", "Lock mode", data.get("lockMode")));
                echo(String.format("%20s: %s", "Lock id", data.get("id")));
                echo(String.format("%20s: %s", "Held by", Arrays.toString((String[]) data.get("owner"))));
                String[] readers = (String[]) data.get("waitingForRead");
                if (readers.length > 0) {
                    echo(String.format("%20s: %s", "Wait for read", Arrays.toString(readers)));
                }
                String[] writers = (String[]) data.get("waitingForWrite");
                if (writers.length > 0) {
                    echo(String.format("%20s: %s", "Wait for write", Arrays.toString(writers)));
                }
            }
        } catch (MBeanException e) {
            error(e);
        } catch (AttributeNotFoundException e) {
            error(e);
        } catch (InstanceNotFoundException e) {
            error(e);
        } catch (ReflectionException e) {
            error(e);
        } catch (IOException e) {
            error(e);
        } catch (MalformedObjectNameException e) {
            error(e);
        }
    }

    public void sanityReport() {
        echo("\nSanity report");
        echo("-----------------------------------------------");
        try {
            ObjectName name = new ObjectName("org.exist.management." + instance + ".tasks:type=SanityReport");
            String status = (String) connection.getAttribute(name, "Status");
            Date lastCheckStart = (Date) connection.getAttribute(name, "LastCheckStart");
            Date lastCheckEnd = (Date) connection.getAttribute(name, "LastCheckEnd");
            echo(String.format("%22s: %s", "Status", status));
            echo(String.format("%22s: %s", "Last check start", lastCheckStart));
            echo(String.format("%22s: %s", "Last check end", lastCheckEnd));
            if (lastCheckStart != null && lastCheckEnd != null)
                echo(String.format("%22s: %dms", "Check took", (lastCheckEnd.getTime() - lastCheckStart.getTime())));

            TabularData table = (TabularData)
                    connection.getAttribute(name, "Errors");
            for (Iterator i = table.values().iterator(); i.hasNext(); ) {
                CompositeData data = (CompositeData) i.next();
                echo(String.format("%22s: %s", "Error code", data.get("errcode")));
                echo(String.format("%22s: %s", "Description", data.get("description")));
            }
        } catch (MBeanException e) {
            e.printStackTrace();
        } catch (AttributeNotFoundException e) {
            e.printStackTrace();
        } catch (InstanceNotFoundException e) {
            e.printStackTrace();
        } catch (ReflectionException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (MalformedObjectNameException e) {
            e.printStackTrace();
        }
    }

    private Object[] getValues(AttributeList attribs) {
        Object[] v = new Object[attribs.size()];
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

    private final static int HELP_OPT = 'h';
    private final static int CACHE_OPT = 'c';
    private final static int DB_OPT = 'd';
    private final static int WAIT_OPT = 'w';
    private final static int LOCK_OPT = 'l';
    private final static int MEMORY_OPT = 'm';
    private final static int PORT_OPT = 'p';
    private final static int INSTANCE_OPT = 'i';
    private final static int ADDRESS_OPT = 'a';
    private final static int SANITY_OPT = 's';
    
    private final static CLOptionDescriptor OPTIONS[] = new CLOptionDescriptor[] {
        new CLOptionDescriptor( "help", CLOptionDescriptor.ARGUMENT_DISALLOWED,
            HELP_OPT, "print help on command line options and exit." ),
        new CLOptionDescriptor( "cache", CLOptionDescriptor.ARGUMENT_DISALLOWED,
                CACHE_OPT, "displays server statistics on cache and memory usage." ),
        new CLOptionDescriptor( "db", CLOptionDescriptor.ARGUMENT_DISALLOWED,
            DB_OPT, "display general info about the db instance." ),
        new CLOptionDescriptor( "wait", CLOptionDescriptor.ARGUMENT_REQUIRED,
            WAIT_OPT, "while displaying server statistics: keep retrieving statistics, but wait the " +
                "specified number of seconds between calls." ),
        new CLOptionDescriptor( "locks", CLOptionDescriptor.ARGUMENT_DISALLOWED,
            LOCK_OPT, "lock manager: display locking information on all threads currently waiting for a lock on a resource " +
                "or collection. Useful to debug deadlocks. During normal operation, the list will usually be empty (means: no " +
                "blocked threads)." ),
        new CLOptionDescriptor( "memory", CLOptionDescriptor.ARGUMENT_DISALLOWED,
            MEMORY_OPT, "display info on free and total memory. Can be combined with other parameters." ),
        new CLOptionDescriptor( "port", CLOptionDescriptor.ARGUMENT_REQUIRED,
            PORT_OPT, "RMI port of the server"),
        new CLOptionDescriptor( "address", CLOptionDescriptor.ARGUMENT_REQUIRED,
            ADDRESS_OPT, "RMI address of the server"),
        new CLOptionDescriptor( "instance", CLOptionDescriptor.ARGUMENT_REQUIRED,
            INSTANCE_OPT, "the ID of the database instance to connect to"),
        new CLOptionDescriptor( "report", CLOptionDescriptor.ARGUMENT_DISALLOWED,
            SANITY_OPT, "retrieve sanity check report from the db")
    };

    private final static int MODE_STATS = 0;
    private final static int MODE_LOCKS = 1;
    
    public static void main(String[] args) {
        CLArgsParser optParser = new CLArgsParser( args, OPTIONS );
        if(optParser.getErrorString() != null) {
            System.err.println( "ERROR: " + optParser.getErrorString());
            return;
        }
        String dbInstance = "exist";
        long waitTime = 0;
        List opt = optParser.getArguments();
        int size = opt.size();
        CLOption option;
        int mode = -1;
        int port = 1099;
        String address = "localhost";
        boolean displayMem = false;
        boolean displayInstance = false;
        boolean displayReport = false;
        for(int i = 0; i < size; i++) {
            option = (CLOption)opt.get(i);
            switch(option.getId()) {
                case HELP_OPT :
                    System.out.println(CLUtil.describeOptions(OPTIONS).toString());
                    return;
                case WAIT_OPT :
                    try {
                        waitTime = Integer.parseInt( option.getArgument() ) * 1000;
                    } catch( NumberFormatException e ) {
                        System.err.println("option -w|--wait requires a numeric argument");
                        return;
                    }
                    break;
                case CACHE_OPT:
                    mode = MODE_STATS;
                    break;
                case LOCK_OPT :
                    mode = MODE_LOCKS;
                    break;
                case PORT_OPT :
                    try {
                        port = Integer.parseInt(option.getArgument());
                    } catch (NumberFormatException e) {
                        System.err.println("option -p|--port requires a numeric argument");
                        return;
                    }
                    break;
                case ADDRESS_OPT :
                    try {
                        address = option.getArgument();
                    } catch (NumberFormatException e) {
                        System.err.println("option -a|--address requires a numeric argument");
                        return;
                    }
                    break;
                case MEMORY_OPT :
                    displayMem = true;
                    break;
                case DB_OPT :
                    displayInstance = true;
                    break;
                case INSTANCE_OPT :
                    dbInstance = option.getArgument();
                    break;
                case SANITY_OPT :
                    displayReport = true;
                    break;
            }
        }
        try {
            JMXClient stats = new JMXClient(dbInstance);
            stats.connect(address,port);
            stats.memoryStats();
            while (true) {
                switch (mode) {
                    case MODE_STATS :
                        stats.cacheStats();
                        break;
                    case MODE_LOCKS :
                        stats.lockTable();
                        break;
                }
                if (displayInstance) stats.instanceStats();
                if (displayMem) stats.memoryStats();
                if (displayReport) stats.sanityReport();
                if (waitTime > 0) {
                    synchronized (stats) {
                        try {
                            stats.wait(waitTime);
                        } catch (InterruptedException e) {
                            System.err.println("INTERRUPTED: " + e.getMessage());
                        }
                    }
                } else
                    return;
            }
        } catch (IOException e) {
            e.printStackTrace(); 
        } 
    }
}
