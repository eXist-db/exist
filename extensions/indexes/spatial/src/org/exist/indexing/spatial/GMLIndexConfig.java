/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2007 The eXist Project
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
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 *  
 *  @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
package org.exist.indexing.spatial;

import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

/**
 */
public class GMLIndexConfig {

	private static final Logger LOG = Logger.getLogger(GMLIndexConfig.class);
	
	private final static String FLUSH_AFTER = "flushAfter";	
	private int flushAfter = -1;
	
	public GMLIndexConfig(Map namespaces, Element node) throws DatabaseConfigurationException {       
    	String param = ((Element)node).getAttribute(FLUSH_AFTER);
        if (param != null && !"".equals(param)) {
        	try {
        		flushAfter = Integer.parseInt(param);
        	} catch (NumberFormatException e) {
        		LOG.info("Invalid value for '" + FLUSH_AFTER + "'", e);
        	}
        }	    	
    }
	
	public int getFlushAfter() {
		return flushAfter;
	}
}
