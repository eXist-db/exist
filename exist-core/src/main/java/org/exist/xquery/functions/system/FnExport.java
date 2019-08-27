/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012-2013 The eXist Project
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
package org.exist.xquery.functions.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.storage.txn.Txn;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import org.exist.backup.SystemExport;

public class FnExport extends BasicFunction {

	protected final static Logger logger = LogManager.getLogger(FnExport.class);
	
	protected final static QName NAME = 
		new QName("export", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
	
	protected final static String DESCRIPTION =
		"Export a backup of the database (admin user only).";

	protected final static FunctionParameterSequenceType DIRorFILE = 
		new FunctionParameterSequenceType("dir", Type.STRING, Cardinality.EXACTLY_ONE,
		"This is an absolute path to where the backup will be written. Must be writeable by the eXist process.");

	protected final static FunctionParameterSequenceType INCREMENTAL = 
		new FunctionParameterSequenceType("incremental", Type.BOOLEAN, Cardinality.ZERO_OR_ONE,
		"Flag to do incremental export.");

	protected final static FunctionParameterSequenceType ZIP = 
		new FunctionParameterSequenceType("zip", Type.BOOLEAN, Cardinality.ZERO_OR_ONE,
		"Flag to do export to zip file.");

	protected final static  FunctionReturnSequenceType RESULT = 
		new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "the export results");

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			NAME,
			DESCRIPTION,
			new SequenceType[] {
				DIRorFILE,
				INCREMENTAL,
				ZIP
			},
			new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "the export results")
		),
		new FunctionSignature(
			new QName("export-silently", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			DESCRIPTION +
			" Messagers from exporter reroute to logs.",
			new SequenceType[] {
				DIRorFILE,
				INCREMENTAL,
				ZIP
			}, 
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "the export results")
		)
	};

	public final static QName EXPORT_ELEMENT = new QName("export", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);

	public FnExport(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		if( !context.getSubject().hasDbaRole() )
			{throw( new XPathException( this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to kill a running xquery" ) );}

    	final String dirOrFile = args[0].getStringValue();
        boolean incremental  = false;
        if (args[1].hasOne())
        	{incremental = args[1].effectiveBooleanValue();}
        boolean zip = false;
        if (args[2].hasOne())
        	{zip = args[2].effectiveBooleanValue();}
        
        MemTreeBuilder builder = null;
        if (NAME.equals( mySignature.getName() )) {
	        builder = context.getDocumentBuilder();
	        builder.startDocument();
	        builder.startElement(EXPORT_ELEMENT, null);
        }
        
        try (final Txn transaction = context.getBroker().continueOrBeginTransaction()) {
        	final SystemExport export = new SystemExport(context.getBroker(), transaction, new Callback(builder), null, true);
            export.export(dirOrFile, incremental, zip, null);
            transaction.commit();
        } catch (final Exception e) {
            throw new XPathException(this, "export failed with exception: " + e.getMessage(), e);
        }
        if (builder == null) {
        	return Sequence.EMPTY_SEQUENCE;
        } else {
	        builder.endElement();
	        builder.endDocument();
	        return (NodeValue) builder.getDocument().getDocumentElement();
        }
    }
    
    private static class Callback implements SystemExport.StatusCallback {

        public final static QName COLLECTION_ELEMENT = new QName("collection", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
        public final static QName RESOURCE_ELEMENT = new QName("resource", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
//        public final static QName INFO_ELEMENT = new QName("info", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
//        public final static QName WARN_ELEMENT = new QName("warn", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
        public final static QName ERROR_ELEMENT = new QName("error", SystemModule.NAMESPACE_URI, SystemModule.PREFIX);
	
        private final MemTreeBuilder builder;
        
        public Callback(MemTreeBuilder builder) {
        	this.builder = builder;			
		}
    	
		@Override
		public void startCollection(String path) throws TerminatedException {
			if (builder == null) {
				SystemExport.LOG.info("Collection "+path);
			} else {
	            builder.startElement(COLLECTION_ELEMENT, null);
	            builder.characters(path);
	            builder.endElement();
			}
		}

		@Override
		public void startDocument(String name, int current, int count) throws TerminatedException {
			if (builder == null) {
				SystemExport.LOG.info("Document "+name);
			} else {
	            builder.startElement(RESOURCE_ELEMENT, null);
	            builder.characters(name);
	            builder.endElement();
			}
		}

		@Override
		public void error(String message, Throwable exception) {
			if (builder == null) {
				SystemExport.LOG.error(message, exception);
			} else {
	            builder.startElement(ERROR_ELEMENT, null);
	            builder.characters(message);
	            builder.endElement();
			}
		}
    }
}