/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist-db project
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
package org.exist.backup;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import org.exist.Namespaces;
import org.exist.security.Permission;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.ExtendedResource;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.DateTimeValue;

import java.awt.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.util.Arrays;
import java.util.Date;
import java.util.Properties;

import javax.swing.*;

import javax.xml.transform.OutputKeys;
import org.exist.security.ACLPermission;


public class Backup
{
	private static final String	EXIST_GENERATED_FILENAME_DOT_FILENAME	 = "_eXist_generated_backup_filename_dot_file_";
	private static final String	EXIST_GENERATED_FILENAME_DOTDOT_FILENAME = "_eXist_generated_backup_filename_dotdot_file_";
	
    private static final int 	currVersion             = 1;

    private String           target;
    private XmldbURI         rootCollection;
    private String           user;
    private String           pass;

    public Properties        defaultOutputProperties = new Properties();

    public Properties        contentsOutputProps     = new Properties();

    {
        defaultOutputProperties.setProperty( OutputKeys.INDENT, "no" );
        defaultOutputProperties.setProperty( OutputKeys.ENCODING, "UTF-8" );
        defaultOutputProperties.setProperty( OutputKeys.OMIT_XML_DECLARATION, "no" );
        defaultOutputProperties.setProperty( EXistOutputKeys.EXPAND_XINCLUDES, "no" );
        defaultOutputProperties.setProperty( EXistOutputKeys.PROCESS_XSL_PI, "no" );
    }

    {
        contentsOutputProps.setProperty( OutputKeys.INDENT, "yes" );
    }

    public Backup( String user, String pass, String target, XmldbURI rootCollection )
    {
        this.user           = user;
        this.pass           = pass;
        this.target         = target;
        this.rootCollection = rootCollection;
    }


    public Backup( String user, String pass, String target )
    {
        this( user, pass, target, XmldbURI.LOCAL_DB_URI );
    }


    public Backup( String user, String pass, String target, XmldbURI rootCollection, Properties property )
    {
        this( user, pass, target, rootCollection );
        this.defaultOutputProperties.setProperty( OutputKeys.INDENT, property.getProperty( "indent", "no" ) );
    }

    public static String encode( String enco )
    {
        final StringBuilder out = new StringBuilder();
        char          t;

        for( int y = 0; y < enco.length(); y++ ) {
            t = enco.charAt( y );

            if( t == '"' ) {
                out.append( "&22;" );
            } else if( t == '&' ) {
                out.append( "&26;" );
            } else if( t == '*' ) {
                out.append( "&2A;" );
            } else if( t == ':' ) {
                out.append( "&3A;" );
            } else if( t == '<' ) {
                out.append( "&3C;" );
            } else if( t == '>' ) {
                out.append( "&3E;" );
            } else if( t == '?' ) {
                out.append( "&3F;" );
            } else if( t == '\\' ) {
                out.append( "&5C;" );
            } else if( t == '|' ) {
                out.append( "&7C;" );
            } else {
                out.append( t );
            }
        }
        return( out.toString() );
    }


    public static String decode( String enco )
    {
        final StringBuilder out  = new StringBuilder();
        String        temp = "";
        char          t;

        for( int y = 0; y < enco.length(); y++ ) {
            t = enco.charAt( y );

            if( t != '&' ) {
                out.append( t );
            } else {
                temp = enco.substring( y, y + 4 );

                if( "&22;".equals(temp) ) {
                    out.append( '"' );
                } else if( "&26;".equals(temp) ) {
                    out.append( '&' );
                } else if( "&2A;".equals(temp) ) {
                    out.append( '*' );
                } else if( "&3A;".equals(temp) ) {
                    out.append( ':' );
                } else if( "&3C;".equals(temp) ) {
                    out.append( '<' );
                } else if( "&3E;".equals(temp) ) {
                    out.append( ">" );
                } else if( "&3F;".equals(temp) ) {
                    out.append( '?' );
                } else if( "&5C;".equals(temp) ) {
                    out.append( '\\' );
                } else if( "&7C;".equals(temp) ) {
                    out.append( '|' );
                } else {
                }
                y = y + 3;
            }
        }
        return( out.toString() );
    }


    public void backup( boolean guiMode, JFrame parent ) throws XMLDBException, IOException, SAXException
    {
        final Collection current = DatabaseManager.getCollection( rootCollection.toString(), user, pass );

        if( guiMode ) {
            final BackupDialog dialog = new BackupDialog( parent, false );
            dialog.setSize( new Dimension( 350, 150 ) );
            dialog.setVisible( true );
            final BackupThread thread = new BackupThread( current, dialog );
            thread.start();

            if( parent == null ) {

                // if backup runs as a single dialog, wait for it (or app will terminate)
                while( thread.isAlive() ) {

                    synchronized( this ) {

                        try {
                            wait( 20 );
                        }
                        catch( final InterruptedException e ) {
                        }
                    }
                }
            }
        } else {
            backup( current, null );
        }
    }


    private void backup( Collection current, BackupDialog dialog ) throws XMLDBException, IOException, SAXException
    {
        String cname = current.getName();

        if( cname.charAt( 0 ) != '/' ) {
            cname = "/" + cname;
        }
        final String       path   = target + encode( URIUtils.urlDecodeUtf8( cname ) );
        BackupWriter output;

        if( target.endsWith( ".zip" ) ) {
            output = new ZipWriter( target, encode( URIUtils.urlDecodeUtf8( cname ) ) );
        } else {
            output = new FileSystemWriter( path );
        }
        backup( current, output, dialog );
        output.close();
    }


    private void backup( Collection current, BackupWriter output, BackupDialog dialog ) throws XMLDBException, IOException, SAXException
    {
        if( current == null ) {
            return;
        }

        current.setProperty( OutputKeys.ENCODING, defaultOutputProperties.getProperty( OutputKeys.ENCODING ) );
        current.setProperty( OutputKeys.INDENT, defaultOutputProperties.getProperty( OutputKeys.INDENT ) );
        current.setProperty( EXistOutputKeys.EXPAND_XINCLUDES, defaultOutputProperties.getProperty( EXistOutputKeys.EXPAND_XINCLUDES ) );
        current.setProperty( EXistOutputKeys.PROCESS_XSL_PI, defaultOutputProperties.getProperty( EXistOutputKeys.PROCESS_XSL_PI ) );

        // get resources and permissions
        final String[] resources = current.listResources();

        // do not sort: order is important because permissions need to be read in the same order below
        // Arrays.sort( resources );

        final UserManagementService   mgtService   = (UserManagementService)current.getService( "UserManagementService", "1.0" );
        final Permission[]            perms        = mgtService.listResourcePermissions();
        final Permission              currentPerms = mgtService.getPermissions( current );


        if( dialog != null ) {
            dialog.setCollection( current.getName() );
            dialog.setResourceCount( resources.length );
        }
        final Writer        contents   = output.newContents();

        // serializer writes to __contents__.xml
        final SAXSerializer serializer = (SAXSerializer)SerializerPool.getInstance().borrowObject( SAXSerializer.class );
        serializer.setOutput( contents, contentsOutputProps );

        serializer.startDocument();
        serializer.startPrefixMapping( "", Namespaces.EXIST_NS );

        // write <collection> element
        final CollectionImpl cur  = (CollectionImpl)current;
        final AttributesImpl attr = new AttributesImpl();

        //The name should have come from an XmldbURI.toString() call
        attr.addAttribute( Namespaces.EXIST_NS, "name", "name", "CDATA", current.getName() );
        writeUnixStylePermissionAttributes(attr, currentPerms);
        attr.addAttribute( Namespaces.EXIST_NS, "created", "created", "CDATA", "" + new DateTimeValue( cur.getCreationTime() ) );
        attr.addAttribute( Namespaces.EXIST_NS, "version", "version", "CDATA", String.valueOf( currVersion ) );
        
        serializer.startElement( Namespaces.EXIST_NS, "collection", "collection", attr );

        if(currentPerms instanceof ACLPermission) {
            writeACLPermission(serializer, (ACLPermission)currentPerms);
        }

        // scan through resources
        Resource       resource;
        OutputStream   os;
        BufferedWriter writer;
        SAXSerializer  contentSerializer;

        for( int i = 0; i < resources.length; i++ ) {

            try {

                if( "__contents__.xml".equals(resources[i]) ) {

                    //Skipping resources[i]
                    continue;
                }
                resource = current.getResource( resources[i] );

                if( dialog != null ) {
                    dialog.setResource( resources[i] );
                    dialog.setProgress( i );
                }
                
                final String name 	= resources[i];
                String filename = encode( URIUtils.urlDecodeUtf8( resources[i] ) );
                
                // Check for special resource names which cause problems as filenames, and if so, replace the filename with a generated filename
                
                if( ".".equals(name.trim()) ) {
                	filename = EXIST_GENERATED_FILENAME_DOT_FILENAME + i;
                } else if( "..".equals(name.trim()) ) {
                	filename = EXIST_GENERATED_FILENAME_DOTDOT_FILENAME + i;
                }

                os = output.newEntry( filename );

                if( resource instanceof ExtendedResource ) {
                    ( (ExtendedResource)resource ).getContentIntoAStream( os );
                } else {
                    writer            = new BufferedWriter( new OutputStreamWriter( os, "UTF-8" ) );

                    // write resource to contentSerializer
                    contentSerializer = (SAXSerializer)SerializerPool.getInstance().borrowObject( SAXSerializer.class );
                    contentSerializer.setOutput( writer, defaultOutputProperties );
                    ( (EXistResource)resource ).setLexicalHandler( contentSerializer );
                    ( (XMLResource)resource ).getContentAsSAX( contentSerializer );
                    SerializerPool.getInstance().returnObject( contentSerializer );
                    writer.flush();
                }
                output.closeEntry();
                final EXistResource ris = (EXistResource)resource;

                //store permissions
                attr.clear();
                attr.addAttribute( Namespaces.EXIST_NS, "type", "type", "CDATA", resource.getResourceType() );
                attr.addAttribute( Namespaces.EXIST_NS, "name", "name", "CDATA", name );
                writeUnixStylePermissionAttributes(attr, perms[i]);
                Date date = ris.getCreationTime();

                if( date != null ) {
                    attr.addAttribute( Namespaces.EXIST_NS, "created", "created", "CDATA", "" + new DateTimeValue( date ) );
                }
                date = ris.getLastModificationTime();

                if( date != null ) {
                    attr.addAttribute( Namespaces.EXIST_NS, "modified", "modified", "CDATA", "" + new DateTimeValue( date ) );
                }

                attr.addAttribute( Namespaces.EXIST_NS, "filename", "filename", "CDATA", filename );
                attr.addAttribute( Namespaces.EXIST_NS, "mimetype", "mimetype", "CDATA", encode( ( (EXistResource)resource ).getMimeType() ) );

                if( !"BinaryResource".equals(resource.getResourceType()) ) {

                    if( ris.getDocType() != null ) {

                        if( ris.getDocType().getName() != null ) {
                            attr.addAttribute( Namespaces.EXIST_NS, "namedoctype", "namedoctype", "CDATA", ris.getDocType().getName() );
                        }

                        if( ris.getDocType().getPublicId() != null ) {
                            attr.addAttribute( Namespaces.EXIST_NS, "publicid", "publicid", "CDATA", ris.getDocType().getPublicId() );
                        }

                        if( ris.getDocType().getSystemId() != null ) {
                            attr.addAttribute( Namespaces.EXIST_NS, "systemid", "systemid", "CDATA", ris.getDocType().getSystemId() );
                        }
                    }
                }
                serializer.startElement( Namespaces.EXIST_NS, "resource", "resource", attr );
                if(perms[i] instanceof ACLPermission) {
                    writeACLPermission(serializer, (ACLPermission)perms[i]);
                }
                serializer.endElement( Namespaces.EXIST_NS, "resource", "resource" );
            }
            catch( final XMLDBException e ) {
                System.err.println( "Failed to backup resource " + resources[i] + " from collection " + current.getName() );
                throw e;
            }
        }

        // write subcollections
        final String[] collections = current.listChildCollections();

        for( int i = 0; i < collections.length; i++ ) {

            if( current.getName().equals( XmldbURI.SYSTEM_COLLECTION ) && "temp".equals(collections[i]) ) {
                continue;
            }
            attr.clear();
            attr.addAttribute( Namespaces.EXIST_NS, "name", "name", "CDATA", collections[i] );
            attr.addAttribute( Namespaces.EXIST_NS, "filename", "filename", "CDATA", encode( URIUtils.urlDecodeUtf8( collections[i] ) ) );
            serializer.startElement( Namespaces.EXIST_NS, "subcollection", "subcollection", attr );
            serializer.endElement( Namespaces.EXIST_NS, "subcollection", "subcollection" );
        }

        // close <collection>
        serializer.endElement( Namespaces.EXIST_NS, "collection", "collection" );
        serializer.endPrefixMapping( "" );
        serializer.endDocument();
        output.closeContents();

        SerializerPool.getInstance().returnObject( serializer );

        // descend into subcollections
        Collection child;

        for( int i = 0; i < collections.length; i++ ) {
            child = current.getChildCollection( collections[i] );

            if( child.getName().equals( XmldbURI.TEMP_COLLECTION ) ) {
                continue;
            }
            output.newCollection( encode( URIUtils.urlDecodeUtf8( collections[i] ) ) );
            backup( child, output, dialog );
            output.closeCollection();
        }
    }


    public static void main( String[] args )
    {
        try {
            final Class<?> cl       = Class.forName( "org.exist.xmldb.DatabaseImpl" );
            final Database database = (Database)cl.newInstance();
            database.setProperty( "create-database", "true" );
            DatabaseManager.registerDatabase( database );
            final Backup backup = new Backup( "admin", null, "backup", URIUtils.encodeXmldbUriFor( args[0] ) );
            backup.backup( false, null );
        }
        catch( final Throwable e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void writeUnixStylePermissionAttributes(AttributesImpl attr, Permission permission) {
        if (permission == null)
            {return;}

        try {
            attr.addAttribute(Namespaces.EXIST_NS, "owner", "owner", "CDATA", permission.getOwner().getName());
            attr.addAttribute(Namespaces.EXIST_NS, "group", "group", "CDATA", permission.getGroup().getName());
            attr.addAttribute(Namespaces.EXIST_NS, "mode", "mode", "CDATA", Integer.toOctalString(permission.getMode()));
        } catch (final Exception e) {

        }
    }

    public static void writeACLPermission(SAXSerializer serializer, ACLPermission acl) throws SAXException {
        if (acl == null)
            {return;}
        final AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(Namespaces.EXIST_NS, "entries", "entries", "CDATA", Integer.toString(acl.getACECount()));
        attr.addAttribute(Namespaces.EXIST_NS, "version", "version", "CDATA", Short.toString(acl.getVersion()));

        serializer.startElement(Namespaces.EXIST_NS, "acl", "acl", attr );

        for(int i = 0; i < acl.getACECount(); i++) {
            attr.clear();
            attr.addAttribute(Namespaces.EXIST_NS, "index", "index", "CDATA", Integer.toString(i));
            attr.addAttribute(Namespaces.EXIST_NS, "target", "target", "CDATA",  acl.getACETarget(i).name());
            attr.addAttribute(Namespaces.EXIST_NS, "who", "who", "CDATA", acl.getACEWho(i));
            attr.addAttribute(Namespaces.EXIST_NS, "access_type", "access_type", "CDATA", acl.getACEAccessType(i).name());
            attr.addAttribute(Namespaces.EXIST_NS, "mode", "mode", "CDATA", Integer.toOctalString(acl.getACEMode(i)));

            serializer.startElement(Namespaces.EXIST_NS, "ace", "ace", attr);
            serializer.endElement(Namespaces.EXIST_NS, "ace", "ace");
        }

        serializer.endElement(Namespaces.EXIST_NS, "acl", "acl");
    }

    class BackupThread extends Thread
    {
        Collection   collection_;
        BackupDialog dialog_;

        public BackupThread( Collection collection, BackupDialog dialog )
        {
            super();
            collection_ = collection;
            dialog_     = dialog;
        }

        public void run()
        {
            try {
                backup( collection_, dialog_ );
                dialog_.setVisible( false );
            }
            catch( final Exception e ) {
                e.printStackTrace();
            }
        }
    }
}
