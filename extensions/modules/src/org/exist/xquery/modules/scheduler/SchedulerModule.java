/*
 *  eXist Scheduler Module Extension
 *  Copyright (C) 2006 Adam Retter <adam.retter@devon.gov.uk>
 *  www.adamretter.co.uk
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

package org.exist.xquery.modules.scheduler;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;


/**
 * eXist Scheduler Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows
 * Jobs to be Scheduled with eXist's Scheduler  
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-11-15
 * @version 1.0
 *
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[])
 */
public class SchedulerModule extends AbstractInternalModule
{
	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/scheduler";
	
	public final static String PREFIX = "scheduler";
	
	private final static FunctionDef[] functions = {
		new FunctionDef(ScheduleFunctions.signatures[0], ScheduleFunctions.class),
		new FunctionDef(ScheduleFunctions.signatures[1], ScheduleFunctions.class),
		new FunctionDef(GetScheduledJobs.signature, GetScheduledJobs.class),
		new FunctionDef(DeleteScheduledJob.signature, DeleteScheduledJob.class),
		new FunctionDef(PauseScheduledJob.signature, PauseScheduledJob.class),
		new FunctionDef(ResumeScheduledJob.signature, ResumeScheduledJob.class)
	};
	
	public SchedulerModule()
	{
		super(functions);
	}

	public String getNamespaceURI()
	{
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix()
	{
		return PREFIX;
	}

	public String getDescription()
	{
		return "A module for Scheduling Jobs";
	}
}
