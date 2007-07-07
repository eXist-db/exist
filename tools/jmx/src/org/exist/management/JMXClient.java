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
 * $Id$
 */
package org.exist.management;

import org.apache.avalon.excalibur.cli.CLArgsParser;
import org.apache.avalon.excalibur.cli.CLOption;
import org.apache.avalon.excalibur.cli.CLOptionDescriptor;
import org.apache.avalon.excalibur.cli.CLUtil;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.*;

/**
 */
public class JMXClient {

    private MBeanServerConnection connection;

    public JMXClient() {
    }

    public void connect(int port) throws IOException {
        JMXServiceURL url =
                new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + port + "/jmxrmi");
        Map env = new HashMap();
        String[] creds = {"guest", "guest"};
        env.put(JMXConnector.CREDENTIALS, creds);

        JMXConnector jmxc = JMXConnectorFactory.connect(url, env);
        connection = jmxc.getMBeanServerConnection();
        echo("Connected to MBean server.");
    }

    public void generalStats() {
        try {
            ObjectName name = new ObjectName("java.lang:type=Memory");
            CompositeData composite = (CompositeData) connection.getAttribute(name, "HeapMemoryUsage");
            if (composite != null) {
                echo(String.format("Current heap size: %,12d kbytes", ((Long)composite.get("used")) / 1024));
                echo(String.format("Committed memory:  %,12d kbytes", ((Long)composite.get("committed")) / 1024));
                echo(String.format("Max memory:       %,12d kbytes", ((Long)composite.get("max")) / 1024));
            }
        } catch (Exception e) {
            error(e);
        }
    }

    public void cacheStats() {
        String cols[] = {"Type", "FileName", "Size", "Used", "Hits", "Fails"};
        echo(String.format("\n%10s %20s %10s %10s %10s %10s", cols[0], cols[1], cols[2], cols[3], cols[4], cols[5]));
        try {
            Set beans = connection.queryNames(new ObjectName("org.exist.management.*:type=CacheManager.Cache,*"), null);
            for (Iterator i = beans.iterator(); i.hasNext();) {
                ObjectName name = (ObjectName) i.next();
                AttributeList attrs = connection.getAttributes(name, cols);
                Object values[] = getValues(attrs);
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
    private final static int STATS_OPT = 's';
    private final static int WAIT_OPT = 'w';
    private final static int LOCK_OPT = 'l';
    private final static int PORT_OPT = 'p';
    
    private final static CLOptionDescriptor OPTIONS[] = new CLOptionDescriptor[] {
        new CLOptionDescriptor( "help", CLOptionDescriptor.ARGUMENT_DISALLOWED,
            HELP_OPT, "print help on command line options and exit." ),
        new CLOptionDescriptor( "stats", CLOptionDescriptor.ARGUMENT_DISALLOWED,
            STATS_OPT, "displays server statistics on cache and memory usage." ),
        new CLOptionDescriptor( "wait", CLOptionDescriptor.ARGUMENT_REQUIRED,
            WAIT_OPT, "while displaying server statistics: keep retrieving statistics, but wait the " +
                "specified number of ms. between calls." ),
        new CLOptionDescriptor( "locks", CLOptionDescriptor.ARGUMENT_DISALLOWED,
            LOCK_OPT, "lock manager: display locking information on all threads currently waiting for a lock on a resource " +
                "or collection. Useful to debug deadlocks. During normal operation, the list will usually be empty (means: no " +
                "blocked threads)." ),
        new CLOptionDescriptor( "port", CLOptionDescriptor.ARGUMENT_REQUIRED,
            PORT_OPT, "RMI port of the server")
    };

    private final static int MODE_STATS = 0;
    private final static int MODE_LOCKS = 1;
    
    public static void main(String[] args) {
        JMXClient stats = new JMXClient();

        CLArgsParser optParser = new CLArgsParser( args, OPTIONS );
        if(optParser.getErrorString() != null) {
            System.err.println( "ERROR: " + optParser.getErrorString());
            return;
        }
        long waitTime = 0;
        List opt = optParser.getArguments();
        int size = opt.size();
        CLOption option;
        int mode = -1;
        int port = 1099;
        for(int i = 0; i < size; i++) {
            option = (CLOption)opt.get(i);
            switch(option.getId()) {
                case HELP_OPT :
                    System.out.println(CLUtil.describeOptions(OPTIONS).toString());
                    return;
                case WAIT_OPT :
                    try {
                        waitTime = Integer.parseInt( option.getArgument() );
                    } catch( NumberFormatException e ) {
                        System.err.println("option -w|--wait requires a numeric argument");
                        return;
                    }
                    break;
                case STATS_OPT :
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
            }
        }
        try {
            stats.connect(port);
            stats.generalStats();
            while (true) {
                switch (mode) {
                    case MODE_STATS :
                        stats.cacheStats();
                        break;
                    case MODE_LOCKS :
                        stats.lockTable();
                        break;
                }

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
