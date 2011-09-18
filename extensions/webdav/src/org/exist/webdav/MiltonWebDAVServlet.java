
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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
 *  $Id$
 */
package org.exist.webdav;

import com.bradmcevoy.http.MiltonServlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.apache.log4j.Logger;

/**
 *  Wrapper around the MiltonServlet for post-configuring the framework.
 * 
 * @author Dannes Wessels
 */
public class MiltonWebDAVServlet extends MiltonServlet {

    protected final static Logger LOG = Logger.getLogger(MiltonWebDAVServlet.class);

    @Override
    public void init(ServletConfig config) throws ServletException {
        
        LOG.info("Initializing wrapper servlet");
        
        // Initialize Milton
        super.init(config);

        // Retrieve parameters, set to FALSE if not existent
        String enableInitParameter = config.getInitParameter("enable.expect.continue");     
        if(enableInitParameter==null){
           enableInitParameter="FALSE"; 
        }
        
        // Calculate effective value
        boolean enableExpectContinue = "TRUE".equalsIgnoreCase(enableInitParameter);

        // Pass value to Milton
        httpManager.setEnableExpectContinue(enableExpectContinue);
        
        LOG.debug("Set 'Enable Expect Continue' to " + enableExpectContinue);
    }
}
