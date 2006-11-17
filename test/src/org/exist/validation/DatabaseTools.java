/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */

package org.exist.validation;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.validation.internal.DatabaseResources;
/**
 *
 * @author wessels
 */
public class DatabaseTools {
    
    private DatabaseResources dr = null;
    private BrokerPool brokerPool = null;
    
    /** Local logger */
    private final static Logger logger = Logger.getLogger(DatabaseResources.class);
    
    /** Creates a new instance of DatabaseTools */
    public DatabaseTools(BrokerPool pool) {
        this.brokerPool = pool;
        dr = new DatabaseResources(pool);
    }
    
    public byte[] readFile(File file){
        
        byte result[] = null;
        
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            FileInputStream fis = new FileInputStream(file);
            
            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = fis.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }
            fis.close();
            baos.close();
            
            result=baos.toByteArray();
        } catch (FileNotFoundException ex) {
            logger.error(ex);
        } catch (IOException ex) {
            logger.error(ex);
        }
        
        return result;
    }
   
}
