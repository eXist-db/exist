/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
package org.exist.http.webdav;

import org.exist.http.webdav.methods.Copy;
import org.exist.http.webdav.methods.Delete;
import org.exist.http.webdav.methods.Get;
import org.exist.http.webdav.methods.Head;
import org.exist.http.webdav.methods.Lock;
import org.exist.http.webdav.methods.Mkcol;
import org.exist.http.webdav.methods.Move;
import org.exist.http.webdav.methods.Options;
import org.exist.http.webdav.methods.Propfind;
import org.exist.http.webdav.methods.Put;
import org.exist.http.webdav.methods.Unlock;
import org.exist.storage.BrokerPool;


/**
 * Create a {@link WebDAVMethod} for the method specified in the
 * HTTP request.
 * 
 * @author wolf
 */
public class WebDAVMethodFactory {

    private static WebDAVMethodFactory theInstance = new WebDAVMethodFactory();
    
    public static WebDAVMethodFactory getInstance() {
       return theInstance;
    }
    
    protected WebDAVMethodFactory() {
    }
    
    public WebDAVMethod create(String method, BrokerPool pool) {
        if(method.equals("OPTIONS"))
            return new Options();
        else if(method.equals("GET"))
            return new Get(pool);
        else if(method.equals("HEAD"))
            return new Head(pool);
        else if(method.equals("PUT"))
            return new Put(pool);
        else if(method.equals("DELETE"))
            return new Delete(pool);
        else if(method.equals("MKCOL"))
            return new Mkcol(pool);
        else if(method.equals("PROPFIND"))
            return new Propfind(pool);
        else if(method.equals("MOVE"))
            return new Move(pool);
        else if(method.equals("COPY"))
            return new Copy(pool);
        else if(method.equals("UNLOCK"))
            return new Unlock(pool);
        else if(method.equals("LOCK"))
            return new Lock(pool);
        else return null;
    }

}
