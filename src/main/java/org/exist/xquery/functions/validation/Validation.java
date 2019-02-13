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
package org.exist.xquery.functions.validation;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.storage.BrokerPool;
import org.exist.validation.ValidationReport;
import org.exist.validation.Validator;
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

/**
 *   XQuery function for validation of XML instance documents
 * using grammars like XSDs and DTDs.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class Validation extends BasicFunction  {

    private static final String CONVENIENCE = "This is the original and oldest validation "
            + "function of eXist-db. It basically wraps the jing library for .rnc/.rnc/.sch/.nvdl "
            + "grammar files and uses the jaxp functionality otherwise. ";
    
    private static final String DEPRECATED_1 = CONVENIENCE + "It is recommended to use the validation:jaxp-parse(), "
            + "validation:jaxv() or validation:jing() functions instead.";
    
    private static final String DEPRECATED_2 = CONVENIENCE + "It is recommended to use the validation:jaxp-parse-report(), "
            + "validation:jaxv-report() or validation:jing-report() functions instead.";
    
    private static final String FUNCTION_TEXT =
            "Validate XML. "
            + "The grammar files (DTD, XML Schema) are resolved using the global "
            + "catalog file(s).";
    
    private static final String EXTENDED_FUNCTION_TEXT = "Validate XML by using a specific grammar.";
    
    private static final String GRAMMAR_DESCRIPTION = "The reference to an OASIS catalog file (.xml), "
            + "a collection (path ends with '/') or a grammar document. "
            + "Supported grammar documents extensions are \".dtd\" \".xsd\" "
            + "\".rng\" \".rnc\" \".sch\" and \".nvdl\". The parameter can be passed as an xs:anyURI or a "
            + "document node.";
    
    private static final String INSTANCE_DESCRIPTION = "The document referenced as xs:anyURI or a node (element or returned by fn:doc())";
    
    private static final String XML_REPORT_RETURN = " An XML report is returned.";
        
    
    private final Validator validator;
    private final BrokerPool brokerPool;
    
    // Setup function signature
    public final static FunctionSignature deprecated[] = {
        new FunctionSignature(
            new QName("validate", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            FUNCTION_TEXT,
            new SequenceType[]{
                new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE, INSTANCE_DESCRIPTION)
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                Shared.simplereportText), DEPRECATED_1
        ),
        
        
        new FunctionSignature(
            new QName("validate", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            EXTENDED_FUNCTION_TEXT,
            new SequenceType[]{
                new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE, INSTANCE_DESCRIPTION),
                new FunctionParameterSequenceType("grammar", Type.ANY_URI, Cardinality.EXACTLY_ONE, GRAMMAR_DESCRIPTION)
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
                    Shared.simplereportText), DEPRECATED_1
        ),
        
        new FunctionSignature(
            new QName("validate-report", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            FUNCTION_TEXT+XML_REPORT_RETURN,
            new SequenceType[]{
                new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE, INSTANCE_DESCRIPTION)
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
                    Shared.xmlreportText), DEPRECATED_2
        ),
        
        new FunctionSignature(
            new QName("validate-report", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            EXTENDED_FUNCTION_TEXT+XML_REPORT_RETURN,
            new SequenceType[]{
                new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE, INSTANCE_DESCRIPTION),
                new FunctionParameterSequenceType("grammar", Type.ITEM, Cardinality.EXACTLY_ONE, GRAMMAR_DESCRIPTION)
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
                    Shared.xmlreportText), DEPRECATED_2
        )
                        
    };
    
    
    public Validation(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
        brokerPool = context.getBroker().getBrokerPool();
        validator = new Validator(brokerPool);
    }

    /**
     * @throws org.exist.xquery.XPathException 
     * @see BasicFunction#eval(Sequence[], Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {

        // Check input parameters
        if (args.length != 1 && args.length != 2) {
            return Sequence.EMPTY_SEQUENCE;
        }

        InputStream is = null;
        ValidationReport report = new ValidationReport();

        try { // args[0]
            is = Shared.getInputStream(args[0].itemAt(0), context);

            if(args.length == 1) {
                // Validate using system catalog
                report=validator.validate(is);

            } else {
                // Validate using resource specified in second parameter
                final String url=Shared.getUrl(args[1].itemAt(0));

                report=validator.validate(is, url);
            }


        } catch (final MalformedURLException ex) {
            LOG.error(ex.getMessage());
            report.setException(ex);

        } catch (final Throwable ex) {
            LOG.error(ex);
            report.setException(ex);

        } finally {
            // Force release stream
            if(is != null){
                try {
                    is.close();
                } catch (final Exception ex) {
                    LOG.debug("Attemted to close stream. ignore.", ex);
                }
            }
        }

        // Create response
        if (isCalledAs("validate") || isCalledAs("jing")) {
            final Sequence result = new ValueSequence();
            result.add(new BooleanValue(report.isValid()));
            return result;

        } else  /* isCalledAs("validate-report") || isCalledAs("jing-report") */{
            final MemTreeBuilder builder = context.getDocumentBuilder();
            final NodeImpl result = Shared.writeReport(report, builder);
            return result;

        }
    }
}
