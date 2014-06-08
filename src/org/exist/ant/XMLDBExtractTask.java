/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.ExtendedResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.util.Properties;

import javax.xml.transform.OutputKeys;


/**
 * an ant task to extract the content of a collection or resource.
 *
 * @author  peter.klotz@blue-elephant-systems.com
 * @author  jim.fuller at webcomposite.com to handle binary file extraction
 */
public class XMLDBExtractTask extends AbstractXMLDBTask
{
    private String  resource          = null;
    private File    destFile          = null;
    private File    destDir           = null;
    private String  type              = "xml";
    private boolean createdirectories = false;
    private boolean subcollections    = false;
    private boolean overwrite         = false;

    // output encoding
    private String  encoding          = "UTF-8";


    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
    public void execute() throws BuildException
    {
        if( uri == null ) {

            if( failonerror ) {
                throw( new BuildException( "You need to specify an XMLDB collection URI" ) );
            }

        } else {
            registerDatabase();

            try {
                final Collection base = DatabaseManager.getCollection( uri, user, password );

                if( base == null ) {
                    throw( new BuildException( "Collection " + uri + " could not be found." ) );
                }

                if( ( resource != null ) && ( destDir == null ) ) {

                    // extraction of a single resource
                    log( "Extracting resource: " + resource + " to " + destFile.getAbsolutePath(), Project.MSG_INFO );
                    final Resource res = base.getResource( resource );

                    if( res == null ) {
                        final String msg = "Resource " + resource + " not found.";

                        if( failonerror ) {
                            throw( new BuildException( msg ) );
                        } else {
                            log( msg, Project.MSG_ERR );
                        }
                    } else {
                        writeResource( res, destFile );
                    }

                } else {

                    // extraction of a collection
                    extractResources( base, null );

                    if( subcollections ) {
                        extractSubCollections( base, null );
                    }
                }

            }
            catch( final XMLDBException e ) {
                final String msg = "XMLDB exception caught while executing query: " + e.getMessage();

                if( failonerror ) {
                    throw( new BuildException( msg, e ) );
                } else {
                    log( msg, e, Project.MSG_ERR );
                }

            }
            catch( final IOException e ) {
                final String msg = "XMLDB exception caught while writing destination file: " + e.getMessage();

                if( failonerror ) {
                    throw( new BuildException( msg, e ) );
                } else {
                    log( msg, e, Project.MSG_ERR );
                }
            }
        }
    }


    /**
     * Create directory from a collection.
     *
     * @param   base  DOCUMENT ME!
     * @param   path  DOCUMENT ME!
     *
     * @throws  XMLDBException  DOCUMENT ME!
     * @throws  IOException     DOCUMENT ME!
     */
    private void extractResources( Collection base, String path ) throws XMLDBException, IOException
    {
        Resource res       = null;
        final String[] resources = base.listResources();

        if( resources != null ) {
            File dir = destDir;

            log( "Extracting to directory " + destDir.getAbsolutePath(), Project.MSG_DEBUG );

            if( path != null ) {
                dir = new File( destDir, path );
            }

            for( final String resource : resources ) {
                res = base.getResource( resource );
                log( "Extracting resource: " + res.getId(), Project.MSG_DEBUG );

                if( !dir.exists() && createdirectories ) {
                    dir.mkdirs();
                }

                if( dir.exists() ) {
                    writeResource( res, dir );
                }

            }
        }
    }


    /**
     * Extract multiple resources from a collection.
     *
     * @param   base  DOCUMENT ME!
     * @param   path  DOCUMENT ME!
     *
     * @throws  XMLDBException  DOCUMENT ME!
     * @throws  IOException     DOCUMENT ME!
     */
    private void extractSubCollections( Collection base, String path ) throws XMLDBException, IOException
    {
        final String[] childCols = base.listChildCollections();

        if( childCols != null ) {
            Collection col = null;

            for( String childCol : childCols ) {
                col = base.getChildCollection( childCol );

                if( col != null ) {
                    log( "Extracting collection: " + col.getName(), Project.MSG_DEBUG );
                    File   dir    = destDir;
                    String subdir;

                    if( path != null ) {
                        dir    = new File( destDir, path + File.separator + childCol );
                        subdir = path + File.separator + childCol;
                    } else {
                        subdir = childCol;
                    }

                    if( !dir.exists() && ( createdirectories == true ) ) {
                        dir.mkdirs();
                    }

                    extractResources( col, subdir );

                    if( subcollections == true ) {
                        extractSubCollections( col, subdir );
                    }
                }
            }
        }
    }


    /**
     * Extract single resource.
     *
     * @param   res   DOCUMENT ME!
     * @param   dest  DOCUMENT ME!
     *
     * @throws  XMLDBException                DOCUMENT ME!
     * @throws  IOException                   DOCUMENT ME!
     */
    private void writeResource( Resource res, File dest ) throws XMLDBException, IOException
    {
        if( res instanceof XMLResource ) {
            writeXMLResource( (XMLResource)res, dest );

        } else if( res instanceof ExtendedResource ) {
            writeBinaryResource( res, dest );
        }
    }


    /**
     * Extract XML resource.
     *
     * @param   res   DOCUMENT ME!
     * @param   dest  DOCUMENT ME!
     *
     * @throws  IOException     DOCUMENT ME!
     * @throws  XMLDBException  DOCUMENT ME!
     */
    private void writeXMLResource( XMLResource res, File dest ) throws IOException, XMLDBException
    {
        if( createdirectories == true ) {
            final File parentDir = new File( dest.getParent() );

            if( !parentDir.exists() ) {
                parentDir.mkdirs();
            }
        }

        if( ( dest != null ) || ( overwrite == true ) ) {
            final Properties outputProperties = new Properties();
            outputProperties.setProperty( OutputKeys.INDENT, "yes" );
            final SAXSerializer serializer = (SAXSerializer)SerializerPool.getInstance().borrowObject( SAXSerializer.class );

            Writer        writer     = null;

            if( dest.isDirectory() ) {
                String fname = res.getId();

                if( !fname.endsWith( "." + type ) ) {
                    fname += "." + type;
                }
                final File file = new File( dest, fname );
                writer = new OutputStreamWriter( new FileOutputStream( file ), encoding );

            } else {

                writer = new OutputStreamWriter( new FileOutputStream( dest ), encoding );
            }

            log( "Writing resource " + res.getId() + " to destination " + dest.getAbsolutePath(), Project.MSG_DEBUG );
            serializer.setOutput( writer, outputProperties );
            res.getContentAsSAX( serializer );
            SerializerPool.getInstance().returnObject( serializer );
            writer.close();

        } else {
            final String msg = "Destination xml file " + ( ( dest != null ) ? ( dest.getAbsolutePath() + " " ) : "" ) + "exists. Use " + "overwrite property to overwrite this file.";

            if( failonerror ) {
                throw( new BuildException( msg ) );
            } else {
                log( msg, Project.MSG_ERR );
            }
        }
    }


    /**
     * Extract single binary resource.
     *
     * @param   res   DOCUMENT ME!
     * @param   dest  DOCUMENT ME!
     *
     * @throws  XMLDBException                DOCUMENT ME!
     * @throws  IOException                   DOCUMENT ME!
     */
    private void writeBinaryResource( Resource res, File dest ) throws XMLDBException, IOException
    {
        if( createdirectories == true ) {
            final File parentDir = new File( dest.getParent() );

            if( !parentDir.exists() ) {
                parentDir.mkdirs();
            }
        }

        //dest != null && ( !dest.exists() ||
        if( ( dest != null ) || ( overwrite == true ) ) {

            if( dest.isDirectory() ) {

                String fname = res.getId();

                if( !fname.endsWith( "." + type ) ) {
                    fname += "";
                }
                dest = new File( dest, fname );

            }
            FileOutputStream os;
            os = new FileOutputStream( dest );

            ( (ExtendedResource)res ).getContentIntoAStream( os );


        } else {
            final String msg = "Dest binary file " + ( ( dest != null ) ? ( dest.getAbsolutePath() + " " ) : "" ) + "exists. Use " + "overwrite property to overwrite file.";

            if( failonerror ) {
                throw( new BuildException( msg ) );
            } else {
                log( msg, Project.MSG_ERR );
            }
        }
    }


    public void setResource( String resource )
    {
        this.resource = resource;
    }


    public void setDestFile( File destFile )
    {
        this.destFile = destFile;
    }


    public void setDestDir( File destDir )
    {
        this.destDir = destDir;
    }


    public void setType( String type )
    {
        this.type = type;

        if( !"xml".equalsIgnoreCase( type ) & !"binary".equalsIgnoreCase( type ) ) {
            throw( new BuildException( "non-xml or non-binary resource types are not supported currently" ) );
        }
    }


    public void setCreatedirectories( boolean createdirectories )
    {
        this.createdirectories = createdirectories;
    }


    public void setSubcollections( boolean subcollections )
    {
        this.subcollections = subcollections;
    }


    public void setOverwrite( boolean overwrite )
    {
        this.overwrite = overwrite;
    }
}
