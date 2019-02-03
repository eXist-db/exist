package org.exist.xquery.modules.httpclient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;

import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Set default options for the NekoHtml parser for all subsequent requests in
 * the same XQuery context
 *
 * @see <a href='http://nekohtml.sourceforge.net/settings.html'>NekoHtml Parser Settings</a>
 *
 * @author   O.Pax <o.pax@web.de>
 * @version  2.1
 * @serial   20140526
 */
public class SetOptionsFunction extends BaseHTTPClientFunction {

    protected static final Logger logger       = LogManager.getLogger( SetOptionsFunction.class );

    public final static FunctionSignature[] signatures   = {
            new FunctionSignature(
                    new QName( "set-parser-options", NAMESPACE_URI, PREFIX ),
                    "Sets default options for the HTML parser for all subsequent requests in this session",
                    new SequenceType[] {
                            OPTIONS_PARAM
                    },
                    new SequenceType( Type.ITEM, Cardinality.EMPTY )
            )
    };


    public SetOptionsFunction(XQueryContext context, FunctionSignature signature)
    {
        super( context, signature );
    }


    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
    {
        if( isCalledAs( "set-parser-options" ) ) {

            FeaturesAndProperties featuresAndProperties = null;

            if (args.length > 0 && !args[0].isEmpty()) {
                featuresAndProperties = getParserFeaturesAndProperties(((NodeValue)args[0].itemAt(0)).getNode());
            }

            context.setXQueryContextVar( HTTP_MODULE_PERSISTENT_OPTIONS, featuresAndProperties );
        }

        return( Sequence.EMPTY_SEQUENCE );
    }

}
