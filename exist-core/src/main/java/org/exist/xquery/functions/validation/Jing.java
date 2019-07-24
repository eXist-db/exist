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
 *  $Id$
 */
package org.exist.xquery.functions.validation;

import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.SchemaReader;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.ValidationDriver;
import com.thaiopensource.validate.rng.CompactSchemaReader;
import java.io.IOException;

import java.net.MalformedURLException;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.storage.BrokerPool;

import org.exist.validation.ValidationReport;
import org.exist.validation.resolver.unstable.ExistResolver;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import org.xml.sax.InputSource;

/**
 *   xQuery function for validation of XML instance documents
 * using jing for grammars like XSD, Relaxng, nvdl and schematron.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class Jing extends BasicFunction  {
    
    
    private static final String extendedFunctionTxt=
            "Validate document using 'Jing'. Supported grammar documents extensions are \".xsd\" "+
             "\".rng\" \".rnc\" \".sch\" and \".nvdl\". Based on functionality provided by " +
             "'com.thaiopensource.validate.ValidationDriver'.";
        
    private static final String instanceText=
            "The document referenced as xs:anyURI, a node (element or returned by fn:doc()) " +
            "or as a Java file object.";

    private static final String grammarText=
            "The grammar document as node (element of returned by fn:doc()), xs:anyURI, " +
            "returned by util:binary-doc() or as a Java file object.";

    private final BrokerPool brokerPool;
    
    // Setup function signature
    public final static FunctionSignature signatures[] = {        
        
        new FunctionSignature(
                new QName("jing", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
                extendedFunctionTxt,
                new SequenceType[]{
                    new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
                        instanceText),
                    new FunctionParameterSequenceType("grammar", Type.ITEM, Cardinality.EXACTLY_ONE,
                        grammarText)
                },
                new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                    Shared.simplereportText)
            ),
        
        
        new FunctionSignature(
                new QName("jing-report", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
                extendedFunctionTxt+" An XML report is returned.",
                new SequenceType[]{
                   new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
                        instanceText),
                    new FunctionParameterSequenceType("grammar", Type.ITEM, Cardinality.EXACTLY_ONE,
                        grammarText)
                   },
                new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
                    Shared.xmlreportText)
            )
                        
    };
    
    
    public Jing(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
        brokerPool = context.getBroker().getBrokerPool();
    }


    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        // Check input parameters
        if (args.length != 2) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final ValidationReport report = new ValidationReport();
        InputSource instance=null;
        InputSource grammar =null;

        try {
            report.start();

            // Get inputstream of XML instance document
            instance=Shared.getInputSource(args[0].itemAt(0), context);

            // Validate using resource specified in second parameter
            grammar = Shared.getInputSource(args[1].itemAt(0), context);

            // Special setup for compact notation
            final String grammarUrl = grammar.getSystemId();
            final SchemaReader schemaReader 
                    = ( (grammarUrl != null) && (grammarUrl.endsWith(".rnc")) )
                    ? CompactSchemaReader.getInstance() : null;

            // Setup validation properties. see Jing interface
            final PropertyMapBuilder properties = new PropertyMapBuilder();
            ValidateProperty.ERROR_HANDLER.put(properties, report);
            
            // Register resolver for xmldb:exist:/// embedded URLs
            final ExistResolver resolver = new ExistResolver(brokerPool);
            ValidateProperty.URI_RESOLVER.put(properties, resolver);
            ValidateProperty.ENTITY_RESOLVER.put(properties, resolver);

            // Setup driver
            final ValidationDriver driver = new ValidationDriver(properties.toPropertyMap(), schemaReader);

            // Load schema
            driver.loadSchema(grammar);

            // Validate XML instance
            driver.validate(instance);
            
        } catch (final MalformedURLException ex) {
            LOG.error(ex.getMessage());
            report.setException(ex);

        } catch (final Throwable ex) {
            LOG.error(ex);
            report.setException(ex);

        } finally {
            Shared.closeInputSource(instance);
            Shared.closeInputSource(grammar);
            report.stop();
        }

        // Create response
        if (isCalledAs("jing")) {
            final Sequence result = new ValueSequence();
            result.add(new BooleanValue(report.isValid()));
            return result;

        } else  /* isCalledAs("jing-report") */{
            final MemTreeBuilder builder = context.getDocumentBuilder();
            final NodeImpl result = Shared.writeReport(report, builder);
            return result;
        } 
    }

}
