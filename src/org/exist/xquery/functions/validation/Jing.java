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
 *  $Id: Validation.java 9042 2009-05-17 18:06:40Z wolfgang_m $
 */
package org.exist.xquery.functions.validation;

import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.SchemaReader;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.ValidationDriver;
import com.thaiopensource.validate.rng.CompactSchemaReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;


import javax.xml.transform.stream.StreamSource;
import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.io.ExistIOException;
import org.exist.validation.ValidationReport;
import org.exist.validation.ValidationReportItem;

import org.exist.validation.internal.node.NodeInputStream;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.AttributesImpl;

/**
 *   xQuery function for validation of XML instance documents
 * using jing for grammars like XSD, Relaxng, onvdl and schematron.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class Jing extends BasicFunction  {
    
    
    private static final String extendedFunctionTxt=
        "Validate document using Jing. " +
        "Based on functionality provided by  com.thaiopensource.validate.ValidationDriver";
        

    private final BrokerPool brokerPool;
    
    // Setup function signature
    public final static FunctionSignature signatures[] = {        
        
        new FunctionSignature(
                new QName("jing", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
                extendedFunctionTxt,
                new SequenceType[]{
                    new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
                        "Document referenced as xs:anyURI or a node (element or returned by fn:doc())"),
                    new FunctionParameterSequenceType("grammar", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                            "Supported grammar documents extensions are \".xsd\" "+
                            "\".rng\" \".rnc\" \".sch\" and \".nvdl\".")
                },
                new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
            ),
        
        
        new FunctionSignature(
                new QName("jing-report", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
                extendedFunctionTxt+" A simple report is returned.",
                new SequenceType[]{
                   new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
                        "Document referenced as xs:anyURI or a node (element or returned by fn:doc())"),
                    new FunctionParameterSequenceType("grammar", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                            "Supported grammar documents extensions are \".xsd\" "+
                            "\".rng\" \".rnc\" \".sch\" and \".nvdl\".")
                   },
                new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)
            )
                        
    };
    
    
    public Jing(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
        brokerPool = context.getBroker().getBrokerPool();
    }

    /**
     * @throws org.exist.xquery.XPathException 
     * @see BasicFunction#eval(Sequence[], Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {

        // Check input parameters
        if (args.length != 2) {
            return Sequence.EMPTY_SEQUENCE;
        }

        InputStream is = null;
        ValidationReport report = new ValidationReport();

        try {
            // Get inputstream of XML instance document
            is=Shared.getInputStream(args[0], context);

            // Validate using resource speciefied in second parameter
            String grammarUrl = Shared.getUrl(args[1]);

            report.start();

            // Setup validation properties. see Jing interface
            PropertyMapBuilder properties = new PropertyMapBuilder();
            ValidateProperty.ERROR_HANDLER.put(properties, report);

            // Special setup for compact notation
            SchemaReader schemaReader = grammarUrl.endsWith(".rnc") ? CompactSchemaReader.getInstance() : null;

            // Setup driver
            ValidationDriver driver = new ValidationDriver(properties.toPropertyMap(), schemaReader);

            // Load schema
            driver.loadSchema(new InputSource(grammarUrl));

            // Validate XML instance
            InputSource instance = new InputSource(is);
            driver.validate(instance);
            
        } catch (MalformedURLException ex) {
            LOG.error(ex);
            throw new XPathException(this, "Invalid resource URI", ex);

        } catch (ExistIOException ex) {
            LOG.error(ex.getCause());
            throw new XPathException(this, "eXistIOexception", ex.getCause());

        } catch (Throwable ex) {
            LOG.error(ex);
            throw new XPathException(this, "Exception: "+ex.getMessage());

        } finally {
            // Force release stream
            report.stop();
            
            try {
                if(is!=null)
                    is.close();
            } catch (IOException ex) {
                LOG.debug("Attemted to close stream. ignore.", ex);
            }
        }

        // Create response
        if (isCalledAs("jing")) {
            Sequence result = new ValueSequence();
            result.add(new BooleanValue(report.isValid()));
            return result;

        } else  /* isCalledAs("jing-report") */{
            MemTreeBuilder builder = context.getDocumentBuilder();
            NodeImpl result = Shared.writeReport(report, builder);
            return result;
        } 
    }

}
