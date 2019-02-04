/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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

package org.exist.backup;

import org.exist.util.XMLReaderPool;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.DateTimeValue;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.util.Date;
import java.util.Properties;


public abstract class AbstractBackupDescriptor implements BackupDescriptor {

    protected long numberOfFiles = 0;
    protected Date date;

    public Date getDate() {
        if (date == null) {

            try {
                final Properties properties = getProperties();
                final String dateStr = properties.getProperty("date");

                if (dateStr != null) {
                    final DateTimeValue dtv = new DateTimeValue(dateStr);
                    date = dtv.getDate();
                }
            } catch (final IOException | XPathException e) {
            }

            if (date == null) {

                // catch unexpected issues by setting the backup time as early as possible
                date = new Date(0);
            }
        }
        return (date);
    }


    public boolean before(final long timestamp) {
        return (timestamp > getDate().getTime());
    }

    @Override
    public void parse(final XMLReaderPool parserPool, final ContentHandler handler) throws IOException, SAXException {
        XMLReader reader = null;
        try {
            reader = parserPool.borrowXMLReader();
            reader.setContentHandler(handler);
            reader.parse(getInputSource());
        } finally {
            if (reader != null) {
                parserPool.returnXMLReader(reader);
            }
        }
    }

    @Override
    public long getNumberOfFiles(){
        return numberOfFiles;
    }
}
