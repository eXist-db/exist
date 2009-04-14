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
package org.exist.management.impl;

import org.exist.scheduler.ScheduledJobInfo;
import org.exist.scheduler.Scheduler;
import org.exist.storage.BrokerPool;
import org.exist.storage.ProcessMonitor;
import org.exist.xquery.XQueryWatchDog;
import org.apache.log4j.Logger;

import javax.management.openmbean.TabularData;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularType;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.CompositeDataSupport;

public class ProcessReport implements ProcessReportMBean {

    private final static Logger LOG = Logger.getLogger(ProcessReport.class);

    private static String[] pItemNames = { "id", "action", "info" };
    private static String[] pItemDescriptions = {
            "Process ID",
            "Description of the current action",
            "Additional info provided by thread"
    };
    private static String[] pIndexNames = { "id" };


    private static String[] qItemNames = { "id", "sourceType", "sourceKey", "terminating" };
    private static String[] qItemDescriptions = {
            "XQuery ID",
            "Type of the query source",
            "Description of the source",
            "Is query terminating?"
    };
    private static String[] qIndexNames = { "id" };

    private ProcessMonitor processMonitor;

    private Scheduler scheduler;

    public ProcessReport(BrokerPool pool) {
        processMonitor = pool.getProcessMonitor();
        scheduler = pool.getScheduler();
    }

    public TabularData getScheduledJobs() {
        OpenType[] itemTypes = { SimpleType.STRING, SimpleType.STRING, SimpleType.STRING };
        CompositeType infoType;
        try {
            infoType = new CompositeType("scheduledJobs", "Lists currently scheduled jobs in eXist",
                    pItemNames, pItemDescriptions, itemTypes);
            TabularType tabularType = new TabularType("jobList", "List of currently scheduled jobs", infoType, pIndexNames);
            TabularDataSupport data = new TabularDataSupport(tabularType);
            ScheduledJobInfo[] jobs = scheduler.getScheduledJobs();
            for (int i = 0; i < jobs.length; i++) {
                Object[] itemValues = { jobs[i].getName(), jobs[i].getGroup(),
                        jobs[i].getTriggerExpression() };
                data.put(new CompositeDataSupport(infoType, pItemNames, itemValues));
            }
            return data;
        } catch (OpenDataException e) {
            LOG.warn(e.getMessage(), e);
        }
        return null;
    }

    public TabularData getRunningJobs() {
        OpenType[] itemTypes = { SimpleType.STRING, SimpleType.STRING, SimpleType.STRING };
        CompositeType infoType;
        try {
            infoType = new CompositeType("runningJobs", "Lists currently running jobs in eXist",
                    pItemNames, pItemDescriptions, itemTypes);
            TabularType tabularType = new TabularType("jobList", "List of currently running jobs", infoType, pIndexNames);
            TabularDataSupport data = new TabularDataSupport(tabularType);
            ProcessMonitor.JobInfo[] jobs = processMonitor.runningJobs();
            for (int i = 0; i < jobs.length; i++) {
                Object[] itemValues = { jobs[i].getThread().getName(), jobs[i].getAction(),
                        jobs[i].getAddInfo().toString() };
                data.put(new CompositeDataSupport(infoType, pItemNames, itemValues));
            }
            return data;
        } catch (OpenDataException e) {
            LOG.warn(e.getMessage(), e);
        }
        return null;
    }

    public TabularData getRunningQueries() {
        OpenType[] itemTypes = { SimpleType.INTEGER, SimpleType.STRING, SimpleType.STRING, SimpleType.BOOLEAN };
        CompositeType infoType;
        try {
            infoType = new CompositeType("runningQueries", "Lists currently running XQueries",
                    qItemNames, qItemDescriptions, itemTypes);
            TabularType tabularType = new TabularType("jobList", "List of currently running XQueries", infoType, qIndexNames);
            TabularDataSupport data = new TabularDataSupport(tabularType);
            XQueryWatchDog[] watchdogs = processMonitor.getRunningXQueries();
            for (int i = 0; i < watchdogs.length; i++) {
                Object[] itemValues = { new Integer(watchdogs[i].getContext().hashCode()), watchdogs[i].getContext().getSourceType(),
                        watchdogs[i].getContext().getSourceKey(), Boolean.valueOf(watchdogs[i].isTerminating()) };
                data.put(new CompositeDataSupport(infoType, qItemNames, itemValues));
            }
            return data;
        } catch (OpenDataException e) {
            LOG.warn(e.getMessage(), e);
        }
        return null;
    }
}