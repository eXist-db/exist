
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *  $Id$
 */
package org.exist.storage;

import org.apache.log4j.Category;
import java.sql.*;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;
import sun.misc.BASE64Encoder;
import sun.misc.BASE64Decoder;
import org.w3c.dom.*;
import org.exist.util.*;

/**
 *  Postgres-specific subclass of ElementIndex. ElementIndex collects all
 *  element occurrences. It uses the name of the element and the current doc_id
 *  as keys and stores all occurrences of this element in a blob. This means
 *  that the blob just contains an array of gid's which may be compressed if
 *  useCompression is true. Storing all occurrences in one large blob is much
 *  faster than storing each of them in a single table row.
 *
 * @author     wolf
 * @created    25. Juli 2002
 */
public class PgElementIndex extends ElementIndex {

    private static Category LOG = Category.getInstance(ElementIndex.class.getName());


    /**
     *  Constructor for the PgElementIndex object
     *
     * @param  broker    Description of the Parameter
     * @param  config    Description of the Parameter
     * @param  pool      Description of the Parameter
     * @param  compress  Description of the Parameter
     */
    public PgElementIndex(DBBroker broker, Configuration config, DBConnectionPool pool, boolean compress) {
        super(broker, config, pool, compress);
    }


    /**
     *  Constructor for the PgElementIndex object
     *
     * @param  broker  Description of the Parameter
     * @param  config  Description of the Parameter
     * @param  pool    Description of the Parameter
     */
    public PgElementIndex(DBBroker broker, Configuration config, DBConnectionPool pool) {
        super(broker, config, pool);
    }


    /**  Description of the Method */
    public void flush() {
        if (elementIds.size() == 0)
            return;
        try {
            m_insert.getConnection().commit();
        } catch (SQLException e) {
            LOG.debug(e);
        }
        ProgressIndicator progress = new ProgressIndicator(elementIds.size());
        int count = 0;
        LOG.debug("flushing PgElementIndex");
        for (Iterator i = elementIds.keySet().iterator(); i.hasNext(); count++) {
            Integer id = (Integer) i.next();
            LOG.debug("flushing " + id);
            ArrayList list = (ArrayList) elementIds.get(id);
            long gid;
            StringBuffer buf = new StringBuffer();
            try {
                m_insert.setInt(1, doc.getDocId());
                m_insert.setInt(2, id.intValue());
                buf.append("{");
                for (Iterator iter = list.iterator(); iter.hasNext(); ) {
                    gid = ((Long) iter.next()).longValue();
                    buf.append(gid);
                    if (iter.hasNext())
                        buf.append(",");
                }
                buf.append("}");
                m_insert.setString(3, buf.toString());
                LOG.debug(buf.toString());
                m_insert.setInt(4, 0);
                m_insert.executeUpdate();
            } catch (SQLException e) {
                LOG.warn(e);
            } catch (IndexOutOfBoundsException b) {
                // happens sometimes, don't know why.
                LOG.warn(b);
            }
            progress.setValue(count);
            //((RelationalBroker)broker).setChanged();
            ((RelationalBroker) broker).notifyObservers(progress);
        }
        elementIds.clear();

        try {
            m_insert.getConnection().commit();
        } catch (SQLException e) {
            LOG.debug(e);
        }
    }
}

