/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
package org.exist.replication.shared;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import org.apache.log4j.Logger;

/**
 *  Reporter of connection problems.
 * 
 * @author Dannes Wessels
 */
public class JmsConnectionExceptionListener implements ExceptionListener {
    
    private final static Logger LOG = Logger.getLogger(JmsConnectionExceptionListener.class);

    @Override
    public void onException(JMSException jmse) {
        
        // Report exception
        StringBuilder sb = new StringBuilder();
        sb.append(jmse.getMessage());
        
        String txt = jmse.getErrorCode();
        if(txt!=null){
            sb.append(" (").append(txt).append(") ");
        }
        
        LOG.error(sb.toString(), jmse);
        
        // If there is a linked exception, report it too
        Exception linkedException = jmse.getLinkedException();
        if(linkedException!=null){
            LOG.error("Linked with: " +linkedException.getMessage(), linkedException);
        }
    }
    
}
