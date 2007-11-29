/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2006 The eXist team
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.scheduler;

/**
 * Class to represent a User's Job
 * Should be extended by all classes wishing to
 * schedule as a Job that perform user defined functions
 * 
 * Classes extending UserJob may have multiple
 * instances executing within the scheduler at once
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public abstract class UserJob implements JobDescription, org.quartz.Job
{
	public static String JOB_GROUP = "eXist.User";
	
	public final String getGroup()
	{
		return JOB_GROUP;
	}
}
