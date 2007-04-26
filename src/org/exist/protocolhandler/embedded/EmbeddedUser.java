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
 * $Id: EmbeddedUser.java 188 2007-03-30 14:59:28Z dizzzz $
 */

package org.exist.protocolhandler.embedded;

import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.BrokerPool;

/**
 * Authenticate user with embedded eXist. 
 *
 * @author @author Dannes Wessels
 */
public class EmbeddedUser {
    
    /**
     *  Authenticate user specified in URL with embedded database.
     *
     * @param xmldbURL URL formatted as xmldb:exist://username:passwd@......
     * @param pool     Exist broker pool, provides access to database.
     * @return         USER when user exists and password is OK, or NULL
     */
    public static User authenticate(XmldbURL xmldbURL, BrokerPool pool){
        
        if(!xmldbURL.hasUserInfo()){
            return null;
        }
        
        SecurityManager secman = pool.getSecurityManager();
        User user = secman.getUser(xmldbURL.getUsername());
        if(user == null) {
            return null;  // user does not exist
            
        } else if (!user.validate(xmldbURL.getPassword())) {
            return null;  // wrong password
        }
        
        return user;      // user does exist and password is valid
    }
    
    /**
     *  Get user GUEST from database.
     *
     * @param pool  Exist broker pool, provides access to database.
     * @return      eXist GUEST user.
     */
    public static User getUserGuest(BrokerPool pool){
        return pool.getSecurityManager().getUser(SecurityManager.GUEST_USER);
    }
    

    
}
