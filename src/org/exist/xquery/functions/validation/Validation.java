/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.io.ExistIOException;
import org.exist.validation.ValidationReport;
import org.exist.validation.ValidationReportItem;
import org.exist.validation.Validator;
import org.exist.validation.internal.node.NodeInputStream;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xml.sax.helpers.AttributesImpl;

/**
 *   xQuery function for validation of XML instance documents
 * using grammars like XSDs and DTDs.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class Validation extends BasicFunction  {
    
    private static final String simpleFunctionTxt=
        "Validate document specified by $a. " +
        "$a is of type xs:anyURI, or a node (element or returned by fn:doc()). "+
        "The grammar files are resolved using the global catalog file(s).";
    
    private static final String extendedFunctionTxt=
        "Validate document specified by $a using $b. "+
        "$a is of type xs:anyURI, or a node (element or returned by fn:doc()). "+
        "$b can point to an OASIS catalog file, a grammar (xml schema only) "+
        "or a collection (path ends with '/').";
    
    private static final String jingFunctionTxt=
        "Validate document specified by $a using $b with jing. "+
        "$a is of type xs:anyURI, or a node (element or returned by fn:doc()). "+
        "$b points to a grammar document with document name extension "+
        ".dtd, xsd, .ndvl (Namespace-based Validation Dispatching Language), "+
        ".rng/.rnc (RelaxNG/Compact notation) or .sch (Schematron).";
    
    private final Validator validator;
    private final BrokerPool brokerPool;
    
    // Setup function signature
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("validate", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            simpleFunctionTxt,
            new SequenceType[]{
            new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE)
        },
            new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
            ),
        
        
        new FunctionSignature(
            new QName("validate", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            extendedFunctionTxt,
            new SequenceType[]{
            new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
            new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE)
        },
            new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
            ),
        
        new FunctionSignature(
            new QName("validate-report", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            simpleFunctionTxt+" A simple report is returned.",
            new SequenceType[]{
            new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE)
        },
            new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)
            ),
        
        new FunctionSignature(
            new QName("validate-report", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            extendedFunctionTxt+" A simple report is returned.",
            new SequenceType[]{
            new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
            new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE)
        },
            new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)
            ),
            
        new FunctionSignature(
            new QName("jing", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            jingFunctionTxt,
            new SequenceType[]{
            new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
            new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE)
        },
            new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)
            ),
            
        new FunctionSignature(
            new QName("jing-report", ValidationModule.NAMESPACE_URI,
            ValidationModule.PREFIX),
            jingFunctionTxt+" A simple report is returned.",
            new SequenceType[]{
            new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
            new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE)
        },
            new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)
            )
            
    };
    
    
    public Validation(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
        brokerPool = context.getBroker().getBrokerPool();
        validator = new Validator(brokerPool);
    }

    /**
     * @see BasicFunction#eval(Sequence[], Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {

        // Check input parameters
        if (args.length != 1 && args.length != 2) {
            return Sequence.EMPTY_SEQUENCE;
        }

        InputStream is = null;
        ValidationReport vr = null;

        try {
            // Get inputstream of XML instance document
            if (args[0].getItemType() == Type.ANY_URI || args[0].getItemType() == Type.STRING) {
                // anyURI provided
                String url = args[0].getStringValue();

                // Fix URL
                if (url.startsWith("/")) {
                    url = "xmldb:exist://" + url;
                }

                is = new URL(url).openStream();

            } else if (args[0].getItemType() == Type.ELEMENT || args[0].getItemType() == Type.DOCUMENT) {
                // Node provided
                is = new NodeInputStream(context, args[0].iterate()); // new NodeInputStream()

            } else {
                LOG.error("Wrong item type " + Type.getTypeName(args[0].getItemType()));
                throw new XPathException(getASTNode(), "wrong item type " + Type.getTypeName(args[0].getItemType()));
            }

           
            if(isCalledAs("validate") || isCalledAs("validate-report")){
                 // Perform validation using xerces SAX parsing
                if(args.length == 1){
                    // Validate using system catalog
                    vr = validator.validateParse(is);

                } else {
                    // Validate using resource speciefied in second parameter
                    String url = args[1].getStringValue();

                    if(url.startsWith("/")){
                        url = "xmldb:exist://" + url;
                    }

                    vr = validator.validateParse(is, url);
                }
                
            } else if(isCalledAs("jing") || isCalledAs("jing-report")){
                // Perform validation using JING
                String url = args[1].getStringValue();

                if(url.startsWith("/")){
                    url = "xmldb:exist://" + url;
                }
                vr = validator.validateJing(is, url);
                
            } else {
                // This should not happen
                throw new XPathException("Validaton function not found");
            }

        } catch (MalformedURLException ex) {
            LOG.error(ex);
            throw new XPathException(getASTNode(), "Invalid resource URI", ex);

        } catch (ExistIOException ex) {
            LOG.error(ex.getCause());
            throw new XPathException(getASTNode(), "eXistIOexception", ex.getCause());

        } catch (Exception ex) {
            LOG.error(ex);
            throw new XPathException(getASTNode(), "exception", ex);

        } finally {
            // Force release stream
            try {
                is.close();
            } catch (IOException ex) {
                LOG.debug("Attemted to close stream. ignore.", ex);
            }
        }

        // Create response
        if (isCalledAs("validate") || isCalledAs("jing")) {
            Sequence result = new ValueSequence();
            result.add(new BooleanValue(vr.isValid()));
            return result;

        } else if (isCalledAs("validate-report") || isCalledAs("jing-report")) {
            MemTreeBuilder builder = context.getDocumentBuilder();
            NodeImpl result = writeReport(vr, builder);
            return result;

        }
        

        // Oops
        LOG.error("invoked with wrong function name");
        throw new XPathException("unknown function");
    }

    private NodeImpl writeReport(ValidationReport report, MemTreeBuilder builder) {

        // start root element
        int nodeNr = builder.startElement("", "report", "report", null);

        // validation status: valid or invalid
        builder.startElement("", "status", "status", null);
        if (report.isValid()) {
            builder.characters("valid");
        } else {
            builder.characters("invalid");
        }
        builder.endElement();

        // namespace when available
        if (report.getNamespaceUri() != null) {
            builder.startElement("", "namespace", "namespace", null);
            builder.characters(report.getNamespaceUri());
            builder.endElement();
        }

        // validation duration
        builder.startElement("", "time", "time", null);
        builder.characters("" + report.getValidationDuration());
        builder.endElement();

        // print exceptions if any
        if (report.getThrowable() != null) {
            builder.startElement("", "exception", "exception", null);
            builder.characters("" + report.getThrowable().getMessage());
            builder.endElement();
        }

        // reusable attributes
        AttributesImpl attribs = new AttributesImpl();

        // iterate validation report items, write message
        List cr = report.getValidationReportItemList();
        for (Iterator iter = cr.iterator(); iter.hasNext();) {
            ValidationReportItem vri = (ValidationReportItem) iter.next();

            // construct attributes
            attribs.addAttribute("", "level", "level", "CDATA", vri.getTypeText());
            attribs.addAttribute("", "line", "line", "CDATA", Integer.toString(vri.getLineNumber()));
            attribs.addAttribute("", "column", "column", "CDATA", Integer.toString(vri.getColumnNumber()));

            if (vri.getRepeat() > 1) {
                attribs.addAttribute("", "repeat", "repeat", "CDATA", Integer.toString(vri.getRepeat()));
            }

            // write message
            builder.startElement("", "message", "message", attribs);
            builder.characters(vri.getMessage());
            builder.endElement();

            // Reuse attributes
            attribs.clear();
        }

        // finish root element
        builder.endElement();

        // return result
        return ((DocumentImpl) builder.getDocument()).getNode(nodeNr);

    }
}
