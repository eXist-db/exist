/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
 *  http://exist.sourceforge.net
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
 *  $Id:
 */
package org.exist.storage.serializers;

import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Iterator;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.sax.*;
import javax.xml.transform.stream.*;
import org.exist.dom.*;
import org.exist.security.*;
import org.exist.storage.DBBroker;
import org.exist.storage.DBConnectionPool;
import org.exist.util.Configuration;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 *  Specialized implementation of Serializer for PostgreSQL. Since loading all
 *  the document's nodes is quite slow with the default serializer, this
 *  implementation just calls a stored procedure to do the job.
 *
 *@author     wolf
 *@created    3. Juni 2002
 */
public class PostgresSerializer extends Serializer {
    protected final static String sql = "select exist_node_to_str(?, ?, ?)";
    protected SAXParserFactory saxFactory = null;

    protected PreparedStatement stmt;


    /**
     *  Constructor for the PostgresSerializer object
     *
     *@param  broker  Description of the Parameter
     *@param  pool    Description of the Parameter
     */
    public PostgresSerializer( DBBroker broker, Configuration config,
    	DBConnectionPool pool ) {
        super( broker, config );
        Connection con = pool.get();
        try {
            stmt = con.prepareStatement( sql );
        } catch ( SQLException sqe ) {
            LOG.debug( sqe );
        }
        pool.release( con );
        saxFactory = SAXParserFactory.newInstance();
        saxFactory.setValidating(false);
				saxFactory.setNamespaceAware(true);
    }


    /**
     *  Description of the Method
     *
     *@param  set               Description of the Parameter
     *@param  start             Description of the Parameter
     *@param  howmany           Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    protected void serializeToSAX( NodeSet set, int start, int howmany ) throws SAXException {
        toSAX( set, start, howmany, 0 );
    }


    /**
     *  Description of the Method
     *
     *@param  set               Description of the Parameter
     *@param  start             Description of the Parameter
     *@param  howmany           Description of the Parameter
     *@param  queryTime         Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    protected void serializeToSAX( NodeSet set, int start,
                                   int howmany, long queryTime ) throws SAXException {
        SAXParser sax = null;
        try {
            sax = saxFactory.newSAXParser();
        } catch ( ParserConfigurationException e ) {
            LOG.warn( e );
            throw new SAXException( e );
        }
        XMLReader reader = sax.getXMLReader();
        reader.setContentHandler( contentHandler );
        try {
            reader.setProperty( "http://xml.org/sax/properties/lexical-handler", lexicalHandler );
        } catch ( SAXNotRecognizedException e ) {
            LOG.debug( e );
        } catch ( SAXNotSupportedException e ) {
            LOG.debug( e );
        }
        String xml = serializeToString( set, start, howmany );
        try {
            InputSource src = new InputSource( new StringReader( xml ) );
            reader.parse( src );
        } catch ( IOException ioe ) {
            throw new SAXException( ioe );
        }
    }


    /**
     *  Description of the Method
     *
     *@param  doc               Description of the Parameter
     *@param  docEvent          Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    protected void serializeToSAX( Document doc, boolean docEvent ) throws SAXException {
        serializeToSAX( doc );
    }


    /**
     *  Description of the Method
     *
     *@param  doc               Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    protected void serializeToSAX( Document doc ) throws SAXException {
        long startTime = System.currentTimeMillis();
        NodeList children = doc.getChildNodes();
        SAXParser sax = null;

        try {
            sax = saxFactory.newSAXParser();
        } catch ( ParserConfigurationException e ) {
            LOG.warn( e );
            throw new SAXException( e );
        }
        XMLReader reader = sax.getXMLReader();
        reader.setContentHandler( contentHandler );
        try {
            if ( lexicalHandler != null )
                reader.setProperty( "http://xml.org/sax/properties/lexical-handler",
                    lexicalHandler );
        } catch ( SAXNotRecognizedException e ) {
            LOG.debug( e );
        } catch ( SAXNotSupportedException e ) {
            LOG.debug( e );
        }
        String xml = serializeToString( doc );
        //LOG.debug( xml );

				try {
					java.io.FileWriter fw=new java.io.FileWriter("testoutput.xml");
					fw.write(xml);
					fw.close();
				} catch (Exception exc) {
					System.err.println("Error: "+exc);
					exc.printStackTrace();
				}
				
        try {
            InputSource src = new InputSource( new StringReader( xml ) );
						reader.parse( src );
        } catch ( IOException ioe ) {
					throw new SAXException( ioe );
        }
        LOG.debug( "serializing document took " + ( System.currentTimeMillis() - startTime ) );
    }


    /**
     *  Description of the Method
     *
     *@param  n                 Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    protected void serializeToSAX( Node n ) throws SAXException {
        SAXParser sax = null;
        try {
            sax = saxFactory.newSAXParser();
        } catch ( ParserConfigurationException e ) {
            LOG.warn( e );
            throw new SAXException( e );
        }
        XMLReader reader = sax.getXMLReader();
        reader.setContentHandler( contentHandler );
        if ( lexicalHandler != null )
            try {
                reader.setProperty( "http://xml.org/sax/properties/lexical-handler", lexicalHandler );
            } catch ( SAXNotRecognizedException e ) {
                LOG.debug( e );
            } catch ( SAXNotSupportedException e ) {
                LOG.debug( e );
            }

        String xml = serializeToString( n );

				

        try {
            InputSource src = new InputSource( new StringReader( xml ) );
            reader.parse( src );
        } catch ( IOException ioe ) {
            throw new SAXException( ioe );
        }
    }


    /**
     *  Description of the Method
     *
     *@param  set               Description of the Parameter
     *@param  start             Description of the Parameter
     *@param  howmany           Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  SAXException  Description of the Exception
     */
    protected String serializeToString( NodeSet set, int start, int howmany )
         throws SAXException {
        return serializeToString( set, start, howmany, -1 );
    }


    /**
     *  Description of the Method
     *
     *@param  set               Description of the Parameter
     *@param  start             Description of the Parameter
     *@param  howmany           Description of the Parameter
     *@param  queryTime         Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  SAXException  Description of the Exception
     */
    protected String serializeToString( NodeSet set, int start,
                                        int howmany, long queryTime )
         throws SAXException {
        Iterator iter = set.iterator();
        for ( int i = 0; i < start - 1; i++ )
            iter.next();
        NodeProxy p;
        StringBuffer result = new StringBuffer();
        String sql;
        ResultSet r;
        long startTime = System.currentTimeMillis();
        for ( int i = 0; i < howmany && iter.hasNext(); i++ ) {
            p = (NodeProxy) iter.next();
            try {
                stmt.setInt( 1, p.doc.getDocId() );
                stmt.setLong( 2, p.gid );
                stmt.setBoolean( 3, true );

								System.err.println("STATEMENT IS: "+stmt);
								
                r = stmt.executeQuery();
                if ( r.next() )
                    result.append( r.getString( 1 ) );
                r.close();
            } catch ( SQLException sqe ) {
                LOG.debug( sqe );
            }
        }
        String header =
            "<?xml version=\"1.0\" encoding=\"" + encoding + "\"?>" +
            "<exist:result hitCount=\"" +
            set.getLength() + "\" ";
        if ( queryTime >= 0 )
            header += "queryTime=\"" + queryTime + "\" ";
        header += "retrieveTime=\"" + ( System.currentTimeMillis() - startTime ) +
            "\" xmlns:exist=\"http://exist.sourceforge.net/NS/exist\">";
        result.insert( 0, header );
        result.append( "</exist:result>" );
        return result.toString();
    }


    /**
     *  Description of the Method
     *
     *@param  doc               Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  SAXException  Description of the Exception
     */
    protected String serializeToString( Document doc ) throws SAXException {
        NodeList children = doc.getChildNodes();
        StringBuffer buf = new StringBuffer();
        buf.append( "<?xml version=\"1.0\" encoding=\"" + encoding + "\"?>" );
				for ( int i = 0; i < children.getLength(); i++ ) {
            buf.append( serializeToString( children.item( i ), false ) );
				}
        return buf.toString();
    }


    /**
     *  Description of the Method
     *
     *@param  n                 Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  SAXException  Description of the Exception
     */
    protected String serializeToString( Node n ) throws SAXException {
        return serializeToString( n, true );
    }


    /**
     *  Description of the Method
     *
     *@param  n                 Description of the Parameter
     *@param  addInfo           Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  SAXException  Description of the Exception
     */
    protected String serializeToString( Node n, boolean addInfo ) throws SAXException {
        if ( !( n instanceof NodeImpl ) )
            throw new RuntimeException( "wrong implementation" );
        StringBuffer buf = new StringBuffer();
        try {
            stmt.setInt( 1, ( (DocumentImpl) n.getOwnerDocument() ).getDocId() );
            stmt.setLong( 2, ( (NodeImpl) n ).getGID() );
            stmt.setBoolean( 3, addInfo );

						System.err.println("STATEMENT IS: "+stmt);

            ResultSet r = stmt.executeQuery();
            if ( r.next() )
							buf.append( r.getString( 1 ) );
            r.close();
        } catch ( SQLException sqe ) {
					LOG.debug( sqe );
            throw new SAXException( "database error" );
        }
        return buf.toString();
    }


    /**
     *  Sets the stylesheet attribute of the PostgresSerializer object
     *
     *@param  stylesheet                             The new stylesheet value
     *@exception  SAXException                       Description of the
     *      Exception
     *@exception  TransformerConfigurationException  Description of the
     *      Exception
     *@exception  PermissionDeniedException          Description of the
     *      Exception
     */
    public void setStylesheet( String stylesheet ) {
        if ( stylesheet == null ) {
            templates = null;
            return;
        }
        long start = System.currentTimeMillis();
        try {
			// does stylesheet point to an external resource?
			if ( stylesheet.indexOf( ":" ) > -1 ) {
			    StreamSource source = new StreamSource( stylesheet );
			    templates = factory.newTemplates( source );
			}
			else {
			try {
			// load stylesheet from eXist
			DocumentImpl doc = (DocumentImpl) broker.getDocument( stylesheet );
			if ( doc == null )
			    throw new SAXException( "document not found!" );
			String xml = serializeToString( doc );
			StreamSource source = new StreamSource( new StringReader( xml ) );
			templates = factory.newTemplates( source );
			    } catch ( PermissionDeniedException e ) {
			        throw new SAXException( "permission denied" );
			    }
			}
			LOG.debug( "compiling stylesheet took " + ( System.currentTimeMillis() - start ) );
			xslHandler =
			    ( (SAXTransformerFactory) factory ).newTransformerHandler( templates );
		} catch (TransformerConfigurationException e) {
			LOG.warn("error compiling stylesheet", e);
		} catch (SAXException e) {
        	LOG.warn("error compiling stylesheet", e);
		}
    }
}

