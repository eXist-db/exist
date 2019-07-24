/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.scheduler;

/**
 * Interface defined requirements for a Scheduleable job.
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public interface JobDescription {

    String EXIST_INTERNAL_GROUP = "eXist.internal";

    String DATABASE = "database";
    String SYSTEM_TASK = "systemtask";
    String XQUERY_SOURCE = "xqueryresource";
    String ACCOUNT = "account";
    String PARAMS = "params";
    String UNSCHEDULE = "unschedule";

    /**
     * Get the name of the job.
     *
     * @return  The job's name
     */
    String getName();

    /**
     * Set the name of the job.
     *
     * @param  name  The job's new name
     */
    void setName(final String name );

    /**
     * Get the name group for the job.
     *
     * @return  The job's group name
     */
    String getGroup();
}
