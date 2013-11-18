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

import org.apache.log4j.Logger;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.dom.*;
import org.exist.management.Agent;
import org.exist.management.AgentFactory;
import org.exist.security.ACLPermission;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.internal.AccountImpl;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.DataBackup;
import org.exist.storage.NativeBroker;
import org.exist.storage.ProcessMonitor;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.Value;
import org.exist.storage.index.CollectionStore;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.UTF8;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.DateTimeValue;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.NamespaceSupport;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import java.io.*;
import java.util.*;


/**
 * Embedded database export tool class. Tries to export as much data as possible, even if parts of the collection hierarchy are corrupted or documents
 * are no longer readable. Features:
 *
 * <ul>
 * <li>Descendant collections will be exported properly even if their ancestor collection is corrupted.</li>
 * <li>Documents which are intact but belong to a destroyed collection will be stored into a special collection /db/__lost_and_found__.</li>
 * <li>Damaged documents are detected by ConsistencyCheck and are removed from the backup.</li>
 * <li>The format of the exported data is compatible with backups generated via the standard backup tool (Java admin client).</li>
 * </ul>
 *
 * <p>The class should be used in combination with {@link ConsistencyCheck}. The error lists returned by ConsistencyCheck can be passed to {@link
 * #export(BackupHandler, org.exist.collections.Collection, BackupWriter, java.util.Date, BackupDescriptor, java.util.List, org.exist.dom.MutableDocumentSet)}.</p>
 */
public class SystemExport
{
    public final static Logger     LOG                     = Logger.getLogger( SystemExport.class );

    private static final XmldbURI   TEMP_COLLECTION         = XmldbURI.createInternal( XmldbURI.TEMP_COLLECTION );
    private static final XmldbURI   CONTENTS_URI            = XmldbURI.createInternal( "__contents__.xml" );
    private static final XmldbURI   LOST_URI                = XmldbURI.createInternal( "__lost_and_found__" );

    private static final int        currVersion             = 1;

    private int                     collectionCount         = -1;

    public Properties               defaultOutputProperties = new Properties();

    public Properties               contentsOutputProps     = new Properties();

    private DBBroker                broker;
    private StatusCallback          callback                = null;
    private boolean                 directAccess            = false;
    private ProcessMonitor.Monitor  monitor                 = null;
    private BackupHandler bh = null;

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

    public SystemExport( DBBroker broker, StatusCallback callback, ProcessMonitor.Monitor monitor, boolean direct )
    {
        this.broker       = broker;
        this.callback     = callback;
        this.monitor      = monitor;
        this.directAccess = direct;
        
    	bh = broker.getDatabase().getPluginsManager().getBackupHandler(LOG);
    }

    public File export( String targetDir, boolean incremental, boolean zip, List<ErrorReport> errorList )
    {
        return( export( targetDir, incremental, -1, zip, errorList ) );
    }


    /**
     * Export the contents of the database, trying to preserve as much data as possible. To be effective, this method should be used in combination
     * with class {@link ConsistencyCheck}.
     *
     * @param   targetDir    the output directory or file to which data will be written. Output will be written to a zip file if target ends with
     *                       .zip.
     * @param   incremental  DOCUMENT ME!
     * @param   maxInc       DOCUMENT ME!
     * @param   zip          DOCUMENT ME!
     * @param   errorList    a list of {@link ErrorReport} objects as returned by methods in {@link ConsistencyCheck}.
     *
     * @return  DOCUMENT ME!
     */
    public File export( String targetDir, boolean incremental, int maxInc, boolean zip, List<ErrorReport> errorList )
    {
        File backupFile = null;

        try {
            final BackupDirectory  directory  = new BackupDirectory( targetDir );
            BackupDescriptor prevBackup = null;

            if( incremental ) {
                prevBackup = directory.lastBackupFile();
                LOG.info( "Creating incremental backup. Prev backup: " + ( ( prevBackup == null ) ? "none" : prevBackup.getSymbolicPath() ) );
            }

            final Properties properties = new Properties();
            int        seqNr      = 1;

            if( incremental ) {
                properties.setProperty( BackupDescriptor.PREVIOUS_PROP_NAME, ( prevBackup == null ) ? "" : prevBackup.getName() );

                if( prevBackup != null ) {
                    final Properties prevProp = prevBackup.getProperties();

                    if( prevProp != null ) {
                        final String seqNrStr = prevProp.getProperty( BackupDescriptor.NUMBER_IN_SEQUENCE_PROP_NAME, "1" );

                        try {
                            seqNr = Integer.parseInt( seqNrStr );

                            if( seqNr == maxInc ) {
                                seqNr       = 1;
                                incremental = false;
                                prevBackup  = null;
                            } else {
                                ++seqNr;
                            }
                        }
                        catch( final NumberFormatException e ) {
                            LOG.warn( "Bad sequence number in backup descriptor: " + prevBackup.getName() );
                        }
                    }
                }
            }
            properties.setProperty( BackupDescriptor.NUMBER_IN_SEQUENCE_PROP_NAME, Integer.toString( seqNr ) );
            properties.setProperty( BackupDescriptor.INCREMENTAL_PROP_NAME, incremental ? "yes" : "no" );

            try {
                properties.setProperty( BackupDescriptor.DATE_PROP_NAME, new DateTimeValue( new Date() ).getStringValue() );
            }
            catch( final XPathException e ) {
            }

            backupFile = directory.createBackup( incremental && ( prevBackup != null ), zip );
            BackupWriter output;

            if( zip ) {
                output = new ZipWriter( backupFile, XmldbURI.ROOT_COLLECTION );
            } else {
                output = new FileSystemWriter( backupFile );
            }
            output.setProperties( properties );

//            File repoBackup = RepoBackup.backup(broker);
//            output.addToRoot(RepoBackup.REPO_ARCHIVE, repoBackup);
//
//
//            FileUtils.forceDelete(repoBackup);

            final Date date = ( prevBackup == null ) ? null : prevBackup.getDate();
            final CollectionCallback cb   = new CollectionCallback( output, date, prevBackup, errorList, true );
            broker.getCollectionsFailsafe( cb );

            exportOrphans( output, cb.getDocs(), errorList );

            output.close();
            return( backupFile );
        }
        catch( final IOException e ) {
            reportError( "A write error occurred while exporting data: '" + e.getMessage() + "'. Aborting export.", e );
            return( null );
        }
        catch( final TerminatedException e ) {

            if( backupFile != null ) {
                backupFile.delete();
            }
            return( null );
        }
    }


    private void reportError( String message, Throwable e )
    {
        if( callback != null ) {
            callback.error( "EXPORT: " + message, e );
        }
        
        LOG.error("EXPORT: " + message, e);
    }


    private static boolean isDamaged( DocumentImpl doc, List<ErrorReport> errorList )
    {
        if( errorList == null ) {
            return( false );
        }

        for( final org.exist.backup.ErrorReport report : errorList ) {

            if( ( report.getErrcode() == org.exist.backup.ErrorReport.RESOURCE_ACCESS_FAILED ) && ( ( (ErrorReport.ResourceError)report ).getDocumentId() == doc.getDocId() ) ) {
                return( true );
            }
        }
        return( false );
    }


    @SuppressWarnings( "unused" )
    private static boolean isDamaged( Collection collection, List<ErrorReport> errorList )
    {
        if( errorList == null ) {
            return( false );
        }

        for( final ErrorReport report : errorList ) {

            if( ( report.getErrcode() == org.exist.backup.ErrorReport.CHILD_COLLECTION ) && ( ( (ErrorReport.CollectionError)report ).getCollectionId() == collection.getId() ) ) {
                return( true );
            }
        }
        return( false );
    }


    private static boolean isDamagedChild( XmldbURI uri, List<ErrorReport> errorList )
    {
        if( errorList == null ) {
            return( false );
        }

        for( final org.exist.backup.ErrorReport report : errorList ) {

            if( ( report.getErrcode() == org.exist.backup.ErrorReport.CHILD_COLLECTION ) && ( (org.exist.backup.ErrorReport.CollectionError)report ).getCollectionURI().equalsInternal( uri ) ) {
                return( true );
            }
        }
        return( false );
    }


    /**
     * Scan all document records in collections.dbx and try to find orphaned documents whose parent collection got destroyed or is damaged.
     *
     * @param  output     the backup writer
     * @param  docs       a document set containing all the documents which were exported regularily. the method will ignore those.
     * @param  errorList  a list of {@link org.exist.backup.ErrorReport} objects as returned by methods in {@link ConsistencyCheck}
     */
    private void exportOrphans( BackupWriter output, DocumentSet docs, List<ErrorReport> errorList )
    {
        output.newCollection( "/db/__lost_and_found__" );

        try {
            final Writer        contents   = output.newContents();

            // serializer writes to __contents__.xml
            final SAXSerializer serializer = (SAXSerializer)SerializerPool.getInstance().borrowObject( SAXSerializer.class );
            serializer.setOutput( contents, contentsOutputProps );

            serializer.startDocument();
            serializer.startPrefixMapping( "", Namespaces.EXIST_NS );
            final AttributesImpl attr = new AttributesImpl();
            attr.addAttribute( Namespaces.EXIST_NS, "name", "name", "CDATA", "/db/__lost_and_found__" );
            attr.addAttribute( Namespaces.EXIST_NS, "version", "version", "CDATA", String.valueOf( currVersion ) );
            attr.addAttribute( Namespaces.EXIST_NS, "owner", "owner", "CDATA", org.exist.security.SecurityManager.DBA_USER );
            attr.addAttribute( Namespaces.EXIST_NS, "group", "group", "CDATA", org.exist.security.SecurityManager.DBA_GROUP );
            attr.addAttribute( Namespaces.EXIST_NS, "mode", "mode", "CDATA", "0771" );
            serializer.startElement( Namespaces.EXIST_NS, "collection", "collection", attr );

            final DocumentCallback docCb = new DocumentCallback( output, serializer, null, null, docs, true );
            broker.getResourcesFailsafe( docCb, directAccess );

            serializer.endElement( Namespaces.EXIST_NS, "collection", "collection" );
            serializer.endPrefixMapping( "" );
            serializer.endDocument();
            output.closeContents();
        }
        catch( final Exception e ) {
            e.printStackTrace();

            if( callback != null ) {
                callback.error( e.getMessage(), e );
            }
        }
        finally {
            output.closeCollection();
        }
    }


    /**
     * Export a collection. Write out the collection metadata and save the resources stored in the collection.
     *
     * @param   current     the collection
     * @param   output      the output writer
     * @param   date
     * @param   prevBackup  DOCUMENT ME!
     * @param   errorList   a list of {@link org.exist.backup.ErrorReport} objects as returned by methods in {@link org.exist.backup.ConsistencyCheck}
     * @param   docs        a document set to keep track of all written documents.
     *
     * @throws  IOException
     * @throws  SAXException
     * @throws  TerminatedException  DOCUMENT ME!
     */
    private void export( BackupHandler bh, Collection current, BackupWriter output, Date date, BackupDescriptor prevBackup, List<ErrorReport> errorList, MutableDocumentSet docs ) throws IOException, SAXException, TerminatedException, PermissionDeniedException
    {
//        if( callback != null ) {
//            callback.startCollection( current.getURI().toString() );
//        }

        if( ( monitor != null ) && !monitor.proceed() ) {
            throw( new TerminatedException( "system export terminated by db" ) );
        }

//        if( !current.getURI().equalsInternal( XmldbURI.ROOT_COLLECTION_URI ) ) {
            output.newCollection( Backup.encode( URIUtils.urlDecodeUtf8( current.getURI() ) ) );
//        }

        try {
            final Writer        contents   = output.newContents();

            // serializer writes to __contents__.xml
            final SAXSerializer serializer = (SAXSerializer)SerializerPool.getInstance().borrowObject( SAXSerializer.class );
            serializer.setOutput( contents, contentsOutputProps );

            final Permission perm = current.getPermissionsNoLock();
            
            serializer.startDocument();
            serializer.startPrefixMapping( "", Namespaces.EXIST_NS );
            final XmldbURI       uri  = current.getURI();
            final AttributesImpl attr = new AttributesImpl();
            attr.addAttribute( Namespaces.EXIST_NS, "name", "name", "CDATA", uri.toString() );
            attr.addAttribute( Namespaces.EXIST_NS, "version", "version", "CDATA", String.valueOf( currVersion ) );
            Backup.writeUnixStylePermissionAttributes(attr, perm);
            try {
                attr.addAttribute( Namespaces.EXIST_NS, "created", "created", "CDATA", new DateTimeValue( new Date( current.getCreationTime() ) ).getStringValue() );
            }
            catch( final XPathException e ) {
                e.printStackTrace();
            }
            
            bh.backup(current, attr);
            
            serializer.startElement( Namespaces.EXIST_NS, "collection", "collection", attr );

            if(perm instanceof ACLPermission) {
            	Backup.writeACLPermission(serializer, (ACLPermission)perm);
            }
            
            bh.backup(current, serializer);

            final int docsCount = current.getDocumentCountNoLock(broker);
            int count     = 0;

            for( final Iterator<DocumentImpl> i = current.iteratorNoLock( broker ); i.hasNext(); count++ ) {
                final DocumentImpl doc = i.next();

                if( isDamaged( doc, errorList ) ) {
                    reportError( "Skipping damaged document " + doc.getFileURI(), null );
                    continue;
                }

                if( doc.getFileURI().equalsInternal( CONTENTS_URI ) || doc.getFileURI().equalsInternal( LOST_URI ) ) {
                    continue; // skip __contents__.xml documents
                }
                exportDocument( bh, output, date, prevBackup, serializer, docsCount, count, doc );
                docs.add( doc, false );
            }

            for( final Iterator<XmldbURI> i = current.collectionIteratorNoLock(broker); i.hasNext(); ) {
                final XmldbURI childUri = i.next();

                if( childUri.equalsInternal( TEMP_COLLECTION ) ) {
                    continue;
                }

                if( isDamagedChild( childUri, errorList ) ) {
                    reportError( "Skipping damaged child collection " + childUri, null );
                    continue;
                }
                attr.clear();
                attr.addAttribute( Namespaces.EXIST_NS, "name", "name", "CDATA", childUri.toString() );
                attr.addAttribute( Namespaces.EXIST_NS, "filename", "filename", "CDATA", Backup.encode( URIUtils.urlDecodeUtf8( childUri.toString() ) ) );
                serializer.startElement( Namespaces.EXIST_NS, "subcollection", "subcollection", attr );
                serializer.endElement( Namespaces.EXIST_NS, "subcollection", "subcollection" );
            }

            if( prevBackup != null ) {

                // Check which collections and resources have been deleted since
                // the
                // last backup
                final CheckDeletedHandler check = new CheckDeletedHandler( current, serializer );

                try {
                    prevBackup.parse( check );
                }
                catch( final Exception e ) {
                    LOG.error( "Caught exception while trying to parse previous backup descriptor: " + prevBackup.getSymbolicPath(), e );
                }
            }

            // close <collection>
            serializer.endElement( Namespaces.EXIST_NS, "collection", "collection" );
            serializer.endPrefixMapping( "" );
            serializer.endDocument();
            output.closeContents();
        }
        finally {

//            if( !current.getURI().equalsInternal( XmldbURI.ROOT_COLLECTION_URI ) ) {
                output.closeCollection();
//            }
        }
    }


    private void exportDocument( BackupHandler bh, BackupWriter output, Date date, BackupDescriptor prevBackup, SAXSerializer serializer, int docsCount, int count, DocumentImpl doc ) throws IOException, SAXException, TerminatedException
    {
        if( callback != null ) {
            callback.startDocument( doc.getFileURI().toString(), count, docsCount );
        }

        if( ( monitor != null ) && !monitor.proceed() ) {
            throw( new TerminatedException( "system export terminated by db" ) );
        }
        final boolean needsBackup = ( prevBackup == null ) || ( date.getTime() < doc.getMetadata().getLastModified() );

        if( needsBackup ) {
            final OutputStream os = output.newEntry( Backup.encode( URIUtils.urlDecodeUtf8( doc.getFileURI() ) ) );

            try {

                if( doc.getResourceType() == DocumentImpl.BINARY_FILE ) {
                    broker.readBinaryResource( (BinaryDocument)doc, os );
                } else {
                    final BufferedWriter writer            = new BufferedWriter( new OutputStreamWriter( os, "UTF-8" ) );

                    // write resource to contentSerializer
                    final SAXSerializer  contentSerializer = (SAXSerializer)SerializerPool.getInstance().borrowObject( SAXSerializer.class );
                    contentSerializer.setOutput( writer, defaultOutputProperties );
                    writeXML( doc, contentSerializer );
                    SerializerPool.getInstance().returnObject( contentSerializer );
                    writer.flush();
                }
            }
            catch( final Exception e ) {
                reportError( "A write error occurred while exporting document: '" + doc.getFileURI() + "'. Continuing with next document.", e );
                return;
            }
            finally {
                output.closeEntry();
            }
        }
        
        final Permission perms = doc.getPermissions();

        // store permissions
        final AttributesImpl attr = new AttributesImpl();
        attr.addAttribute( Namespaces.EXIST_NS, "type", "type", "CDATA", ( doc.getResourceType() == DocumentImpl.BINARY_FILE ) ? "BinaryResource" : "XMLResource" );
        attr.addAttribute( Namespaces.EXIST_NS, "name", "name", "CDATA", doc.getFileURI().toString() );
        attr.addAttribute( Namespaces.EXIST_NS, "skip", "skip", "CDATA", ( needsBackup ? "no" : "yes" ) );
        Backup.writeUnixStylePermissionAttributes(attr, perms);

        // be careful when accessing document metadata: it is stored in a
        // different place than the
        // main document info and could thus be damaged
        DocumentMetadata metadata = null;

        try {
            metadata = doc.getMetadata();
        }
        catch( final Exception e ) {
            // LOG.warn(e.getMessage(), e);
        }

        try {
            String created;
            String modified;

            // metadata could be damaged
            if( metadata != null ) {
                created  = new DateTimeValue( new Date( metadata.getCreated() ) ).getStringValue();
                modified = new DateTimeValue( new Date( metadata.getLastModified() ) ).getStringValue();
            } else {
                created  = new DateTimeValue().getStringValue();
                modified = created;
            }
            attr.addAttribute( Namespaces.EXIST_NS, "created", "created", "CDATA", created );
            attr.addAttribute( Namespaces.EXIST_NS, "modified", "modified", "CDATA", modified );
        }
        catch( final XPathException e ) {
            LOG.warn( e.getMessage(), e );
        }

        attr.addAttribute( Namespaces.EXIST_NS, "filename", "filename", "CDATA", Backup.encode( URIUtils.urlDecodeUtf8( doc.getFileURI() ) ) );
        String mimeType = "application/xml";

        if( ( metadata != null ) && ( metadata.getMimeType() != null ) ) {
            mimeType = Backup.encode( metadata.getMimeType() );
        }
        attr.addAttribute( Namespaces.EXIST_NS, "mimetype", "mimetype", "CDATA", mimeType );

//output by serializer
//        if( ( doc.getResourceType() == DocumentImpl.XML_FILE ) && ( metadata != null ) && ( doc.getDoctype() != null ) ) {
//
//            if( doc.getDoctype().getName() != null ) {
//                attr.addAttribute( Namespaces.EXIST_NS, "namedoctype", "namedoctype", "CDATA", doc.getDoctype().getName() );
//            }
//
//            if( doc.getDoctype().getPublicId() != null ) {
//                attr.addAttribute( Namespaces.EXIST_NS, "publicid", "publicid", "CDATA", doc.getDoctype().getPublicId() );
//            }
//
//            if( doc.getDoctype().getSystemId() != null ) {
//                attr.addAttribute( Namespaces.EXIST_NS, "systemid", "systemid", "CDATA", doc.getDoctype().getSystemId() );
//            }
//        }
        
    	bh.backup(doc, attr);
        
        serializer.startElement( Namespaces.EXIST_NS, "resource", "resource", attr );
        if(perms instanceof ACLPermission) {
            Backup.writeACLPermission(serializer, (ACLPermission)perms);
        }

        bh.backup(doc, serializer);

        serializer.endElement( Namespaces.EXIST_NS, "resource", "resource" );
    }


    /**
     * Serialize a document to XML, based on {@link XMLStreamReader}.
     *
     * @param  doc       the document to serialize
     * @param  receiver  the output handler
     */
    private void writeXML( DocumentImpl doc, Receiver receiver )
    {
        try {
            EmbeddedXMLStreamReader   reader;
            char[]                    ch;
            int                       nsdecls;
            final NamespaceSupport          nsSupport = new NamespaceSupport();
            final NodeList                  children  = doc.getChildNodes();
            
			final DocumentType docType = doc.getDoctype();
            if (docType != null)
    			{receiver.documentType(docType.getName(), docType.getPublicId(), docType.getSystemId());}

            for( int i = 0; i < children.getLength(); i++ ) {
                final StoredNode child = (StoredNode)children.item( i );
                reader = broker.getXMLStreamReader( child, false );

                while( reader.hasNext() ) {
                    final int status = reader.next();

                    switch( status ) {

                        case XMLStreamReader.START_DOCUMENT:
                        case XMLStreamReader.END_DOCUMENT: {
                            break;
                        }

                        case XMLStreamReader.START_ELEMENT: {
                            nsdecls = reader.getNamespaceCount();
                            for( int ni = 0; ni < nsdecls; ni++ ) {
                                receiver.startPrefixMapping( reader.getNamespacePrefix( ni ), reader.getNamespaceURI( ni ) );
                            }

                            final AttrList attribs = new AttrList();
                            for( int j = 0; j < reader.getAttributeCount(); j++ ) {
                                final QName qn = new QName( reader.getAttributeLocalName( j ), reader.getAttributeNamespace( j ), reader.getAttributePrefix( j ) );
                                attribs.addAttribute( qn, reader.getAttributeValue( j ) );
                            }
                            receiver.startElement( new QName( reader.getLocalName(), reader.getNamespaceURI(), reader.getPrefix() ), attribs );
                            break;
                        }

                        case XMLStreamReader.END_ELEMENT: {
                            receiver.endElement( new QName( reader.getLocalName(), reader.getNamespaceURI(), reader.getPrefix() ) );
                            nsdecls = reader.getNamespaceCount();
                            for( int ni = 0; ni < nsdecls; ni++ ) {
                                receiver.endPrefixMapping( reader.getNamespacePrefix( ni ) );
                            }
                            break;
                        }

                        case XMLStreamReader.CHARACTERS: {
                            receiver.characters( reader.getText() );
                            break;
                        }

                        case XMLStreamReader.CDATA: {
                            ch = reader.getTextCharacters();
                            receiver.cdataSection( ch, 0, ch.length );
                            break;
                        }

                        case XMLStreamReader.COMMENT: {
                            ch = reader.getTextCharacters();
                            receiver.comment( ch, 0, ch.length );
                            break;
                        }

                        case XMLStreamReader.PROCESSING_INSTRUCTION: {
                            receiver.processingInstruction( reader.getPITarget(), reader.getPIData() );
                            break;
                        }
                    }

                    if( ( child.getNodeType() == Node.COMMENT_NODE ) || ( child.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE ) ) {
                        break;
                    }
                }
                nsSupport.reset();
            }
        }
        catch( final IOException e ) {
            e.printStackTrace();
        }
        catch( final XMLStreamException e ) {
            e.printStackTrace();
        }
        catch( final SAXException e ) {
            e.printStackTrace();
        }
    }


    public static File getUniqueFile( String base, String extension, String dir )
    {
        final String filename = base + '-' + DataBackup.creationDateFormat.format( Calendar.getInstance().getTime() );
        File   file     = new File( dir, filename + extension );
        int    version  = 0;

        while( file.exists() ) {
            file = new File( dir, filename + '_' + version++ + extension );
        }
        return( file );
    }


    public int getCollectionCount() throws TerminatedException
    {
        if( collectionCount == -1 ) {
            AccountImpl.getSecurityProperties().enableCheckPasswords(false);

            try {
                final CollectionCallback cb = new CollectionCallback( null, null, null, null, false );
                broker.getCollectionsFailsafe( cb );
                collectionCount = cb.collectionCount;
            }
            finally {
                AccountImpl.getSecurityProperties().enableCheckPasswords(true);
            }
        }
        return( collectionCount );
    }

    public static interface StatusCallback
    {
        void startCollection( String path ) throws TerminatedException;


        void startDocument( String name, int current, int count ) throws TerminatedException;


        void error( String message, Throwable exception );
    }

    private class CollectionCallback implements BTreeCallback
    {
        private BackupWriter       writer;
        private BackupDescriptor   prevBackup;
        private Date               date;
        private List<ErrorReport>  errors;
        private MutableDocumentSet docs             = new DefaultDocumentSet();
        private int                collectionCount  = 0;
        private boolean            exportCollection;
        private int                lastPercentage   = -1;
        private Agent              jmxAgent         = AgentFactory.getInstance();

        private CollectionCallback( BackupWriter writer, Date date, BackupDescriptor prevBackup, List<ErrorReport> errorList, boolean exportCollection )
        {
            this.writer           = writer;
            this.errors           = errorList;
            this.date             = date;
            this.prevBackup       = prevBackup;
            this.exportCollection = exportCollection;
        }

        public boolean indexInfo( Value value, long pointer ) throws TerminatedException
        {
            String uri = null;

            try {
                collectionCount++;

                if( exportCollection ) {
                    final CollectionStore store = (CollectionStore)( (NativeBroker)broker ).getStorage( NativeBroker.COLLECTIONS_DBX_ID );
                    uri = UTF8.decode( value.data(), value.start() + CollectionStore.CollectionKey.OFFSET_VALUE, value.getLength() - CollectionStore.CollectionKey.OFFSET_VALUE ).toString();

                    if( CollectionStore.NEXT_COLLECTION_ID_KEY.equals( uri ) || CollectionStore.NEXT_DOC_ID_KEY.equals( uri ) || CollectionStore.FREE_COLLECTION_ID_KEY.equals( uri ) || CollectionStore.FREE_DOC_ID_KEY.equals( uri ) ) {
                        return( true );
                    }

                    if( callback != null ) {
                        callback.startCollection( uri );
                    }
                    final Collection        collection = new Collection(broker, XmldbURI.createInternal( uri ) );
                    final VariableByteInput istream    = store.getAsStream( pointer );
                    collection.read( broker, istream );
                    BackupDescriptor bd = null;

                    if( prevBackup != null ) {
                        bd = prevBackup.getBackupDescriptor( uri );
                    }
                    int percentage = 100 * ( collectionCount + 1 ) / ( getCollectionCount() + 1 );

                    if( ( jmxAgent != null ) && ( percentage != lastPercentage ) ) {
                        lastPercentage = percentage;
                        jmxAgent.updateStatus( broker.getBrokerPool(), percentage );
                    }
                    export( bh, collection, writer, date, bd, errors, docs );
                }
            }
            catch( final TerminatedException e ) {
                reportError( "Terminating system export upon request", e );

                // rethrow
                throw( e );
            }
            catch( final Exception e ) {
                reportError( "Caught exception while scanning collections: " + uri, e );
            }
            return( true );
        }


        public DocumentSet getDocs()
        {
            return( docs );
        }
    }


    private class DocumentCallback implements BTreeCallback
    {
        private DocumentSet      exportedDocs;
        private Set<String>      writtenDocs = null;
        private SAXSerializer    serializer;
        private BackupWriter     output;
        private Date             date;
        private BackupDescriptor prevBackup;

        private DocumentCallback( BackupWriter output, SAXSerializer serializer, Date date, BackupDescriptor prevBackup, DocumentSet exportedDocs, boolean checkNames )
        {
            this.exportedDocs = exportedDocs;
            this.serializer   = serializer;
            this.output       = output;
            this.date         = date;
            this.prevBackup   = prevBackup;

            if( checkNames ) {
                writtenDocs = new TreeSet<String>();
            }
        }

        public boolean indexInfo( Value key, long pointer ) throws TerminatedException
        {
            final CollectionStore store = (CollectionStore)( (NativeBroker)broker ).getStorage( NativeBroker.COLLECTIONS_DBX_ID );
            final int             docId = CollectionStore.DocumentKey.getDocumentId( key );

            if( !exportedDocs.contains( docId ) ) {

                try {
                    final byte              type    = key.data()[key.start() + Collection.LENGTH_COLLECTION_ID + DocumentImpl.LENGTH_DOCUMENT_TYPE];
                    final VariableByteInput istream = store.getAsStream( pointer );
                    DocumentImpl      doc     = null;

                    if( type == DocumentImpl.BINARY_FILE ) {
                        doc = new BinaryDocument( broker.getBrokerPool() );
                    } else {
                        doc = new DocumentImpl( broker.getBrokerPool() );
                    }
                    doc.readWithMetadata( istream );
                    reportError( "Found an orphaned document: " + doc.getFileURI().toString(), null );

                    if( writtenDocs != null ) {
                        int    count   = 1;
                        String fileURI = doc.getFileURI().toString();
                        final String origURI = fileURI;

                        while( writtenDocs.contains( fileURI ) ) {
                            fileURI = origURI + "." + count++;
                        }
                        doc.setFileURI( XmldbURI.createInternal( fileURI ) );
                        writtenDocs.add( fileURI );
                    }
                    exportDocument( bh, output, date, prevBackup, serializer, 0, 0, doc );
                }
                catch( final Exception e ) {
                    reportError( "Caught an exception while scanning documents: " + e.getMessage(), e );
                }
            }
            return( true );
        }
    }


    private class CheckDeletedHandler extends DefaultHandler
    {
        private Collection    collection;
        private SAXSerializer serializer;

        private CheckDeletedHandler( Collection collection, SAXSerializer serializer )
        {
            this.collection = collection;
            this.serializer = serializer;
        }

        @Override
        public void startElement( String uri, String localName, String qName, Attributes attributes ) throws SAXException
        {
            if( uri.equals( Namespaces.EXIST_NS ) ) {

                try {
                    if( "subcollection".equals(localName) ) {
                        String name = attributes.getValue( "filename" );

                        if( name == null ) {
                            name = attributes.getValue( "name" );
                        }

                        if( !collection.hasChildCollection(broker, XmldbURI.create( name ) ) ) {
                            final AttributesImpl attr = new AttributesImpl();
                            attr.addAttribute( Namespaces.EXIST_NS, "name", "name", "CDATA", name );
                            attr.addAttribute( Namespaces.EXIST_NS, "type", "type", "CDATA", "collection" );
                            serializer.startElement( Namespaces.EXIST_NS, "deleted", "deleted", attr );
                            serializer.endElement( Namespaces.EXIST_NS, "deleted", "deleted" );
                        }
                    } else if( "resource".equals(localName) ) {
                        final String name = attributes.getValue( "name" );

                        if( !collection.hasDocument(broker, XmldbURI.create( name ) ) ) {
                            final AttributesImpl attr = new AttributesImpl();
                            attr.addAttribute( Namespaces.EXIST_NS, "name", "name", "CDATA", name );
                            attr.addAttribute( Namespaces.EXIST_NS, "type", "type", "CDATA", "resource" );
                            serializer.startElement( Namespaces.EXIST_NS, "deleted", "deleted", attr );
                            serializer.endElement( Namespaces.EXIST_NS, "deleted", "deleted" );
                        }
                    }
                } catch(final PermissionDeniedException pde) {
                    throw new SAXException("Unable to process :" + qName + ": " + pde.getMessage(), pde);
                }
            }
        }
    }
}
