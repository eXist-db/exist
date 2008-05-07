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
 * Interface defined requirements for a Scheduleable job
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public interface JobDescription
{
	/**
	 * Get the name of the job
	 * 
	 *  @return The job's name
	 */
	public String getName();

    /**
     * Set the name of the job
     * 
     * @param name The job's new name
     */
    public void setName(String name);
    
	/**
	 * Get the name group for the job
	 * 
	 * @return The job's group name
	 */
	public String getGroup();
}
