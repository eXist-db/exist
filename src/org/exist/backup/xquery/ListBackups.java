/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 * $Id$
 */
package org.exist.backup.xquery;

import org.xml.sax.helpers.AttributesImpl;

import org.exist.Namespaces;
import org.exist.backup.BackupDescriptor;
import org.exist.backup.BackupDirectory;
import org.exist.backup.FileSystemBackupDescriptor;
import org.exist.backup.ZipArchiveBackupDescriptor;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.BrokerPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.io.File;
import java.io.IOException;

import java.util.Iterator;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ListBackups extends BasicFunction
{
    private final static String           returnText        = "an XML fragment listing all eXist backups found in the specified backup directory: " + "<directory> " + "<backup file=\"filename\"> " + "<key>value</key>" + "<key>value</key>" + "</backup> " + "<backup file=\"filename\"> " + "<key>value</key>" + "<key>value</key>" + "</backup> " + "</directory> " + "Where key is a property name and value is a property value for the given .zip file.";

    public final static FunctionSignature signature         = new FunctionSignature( new QName( "list", BackupModule.NAMESPACE_URI, BackupModule.PREFIX ), "Returns an XML fragment listing all eXist backups found in a specified backup directory.", new SequenceType[] {
            new FunctionParameterSequenceType( "directory", Type.STRING, Cardinality.EXACTLY_ONE, "The path to the directory to show the list of backups on." )
        }, new FunctionReturnSequenceType( Type.NODE, Cardinality.EXACTLY_ONE, returnText ) );

    public final static QName             DIRECTORY_ELEMENT = new QName( "directory", Namespaces.EXIST_NS, "" );
    public final static QName             BACKUP_ELEMENT    = new QName( "backup", Namespaces.EXIST_NS, "" );

    public ListBackups( XQueryContext context )
    {
        super( context, signature );
    }

    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
    {
        if(!context.getEffectiveUser().hasDbaRole()) {
            throw new XPathException("You must be a DBA to list available backups");
        }

        final String exportDir = args[0].getStringValue();
        File   dir       = new File( exportDir );

        if( !dir.isAbsolute() ) {
            dir = new File( (String)context.getBroker().getConfiguration().getProperty( BrokerPool.PROPERTY_DATA_DIR ), exportDir );
        }

        context.pushDocumentContext();

        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            final int            nodeNr  = builder.startElement( DIRECTORY_ELEMENT, null );

            if( dir.isDirectory() && dir.canRead() ) {
                final Pattern pattern = Pattern.compile( BackupDirectory.FILE_REGEX );
                final Matcher matcher = pattern.matcher( "" );
                final File[]  files   = dir.listFiles();

                for( int i = 0; i < files.length; i++ ) {
                    matcher.reset( files[i].getName() );

                    if( matcher.matches() ) {
                        BackupDescriptor descriptor;

                        try {

                            if( files[i].getName().endsWith( ".zip" ) ) {
                                descriptor = new ZipArchiveBackupDescriptor( files[i] );
                            } else {
                            	final File descriptorFile = new File(new File(files[i], "db"), BackupDescriptor.COLLECTION_DESCRIPTOR);
                                descriptor = new FileSystemBackupDescriptor( descriptorFile );
                            }
                            final Properties properties = descriptor.getProperties();

                            if( properties != null ) {
                                final AttributesImpl attrs = new AttributesImpl();
                                attrs.addAttribute( "", "file", "file", "CDATA", files[i].getName() );
                                builder.startElement( BACKUP_ELEMENT, attrs );

                                for( final Iterator<Object> iter = properties.keySet().iterator(); iter.hasNext(); ) {
                                    final String key = iter.next().toString();
                                    builder.startElement( new QName( key, Namespaces.EXIST_NS, "" ), null );
                                    builder.characters( (String)properties.get( key ) );
                                    builder.endElement();
                                }
                                builder.endElement();
                            }
                        }
                        catch( final IOException e ) {
                        }
                    }
                }
            }
            builder.endElement();
            return( builder.getDocument().getNode( nodeNr ) );
        }
        finally {
            context.popDocumentContext();
        }
    }
}
