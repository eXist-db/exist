package org.exist.xquery.modules.cssparser;

import com.steadystate.css.parser.CSSOMParser;
import com.steadystate.css.parser.SACParserCSS21;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
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
import org.w3c.css.sac.InputSource;
import org.w3c.css.sac.Parser;
import org.w3c.dom.css.CSSStyleSheet;
import org.xml.sax.helpers.ParserFactory;

/**
 * @author Adam Retter <adam@exist-db.org>
 */
public class CSSParserFunctions extends BasicFunction {

    private static final Logger logger = Logger.getLogger(CSSParserFunctions.class);
    
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("parse-to-xml", CSSParserModule.NAMESPACE_URI, CSSParserModule.PREFIX),
            "Parse a CSS into an XML representation.",
            new SequenceType[]{
                new FunctionParameterSequenceType("css", Type.STRING, Cardinality.EXACTLY_ONE, "The CSS.")
            },
            new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.EXACTLY_ONE, "The XML representation of the CSS file")
        )
    };

    public CSSParserFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        
        Reader reader = new StringReader(args[0].getStringValue());
        InputSource source = new InputSource(reader);
        
        MemTreeBuilder builder = new MemTreeBuilder(context);
        
        Parser parser = new SACParserCSS21();
        parser.setErrorHandler(new XMLCSSErrorHandler());
        parser.setDocumentHandler(new XMLCSSDocumentHandler(builder));
        
        try {
            parser.parseStyleSheet(source);
        } catch(IOException ioe) {
            throw new XPathException(this, ioe.getMessage(), ioe);
        }
        
        return builder.getDocument();
    }
    
}
