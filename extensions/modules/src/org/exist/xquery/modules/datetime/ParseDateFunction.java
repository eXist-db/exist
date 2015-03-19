package org.exist.xquery.modules.datetime;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.TimeUtils;
import org.exist.xquery.value.Type;

/**
 * @author Adam Retter <adam@exist-db.org>
 */
public class ParseDateFunction extends BasicFunction
{
    protected static final Logger logger = LogManager.getLogger(FormatDateFunction.class);

    public final static FunctionSignature signature =
        new FunctionSignature(
                new QName("parse-date", DateTimeModule.NAMESPACE_URI, DateTimeModule.PREFIX),
                "Returns an xs:date of the xs:string parsed according to the SimpleDateFormat format.",
                new SequenceType[] {
                        new FunctionParameterSequenceType("date-string", Type.STRING, Cardinality.EXACTLY_ONE, "The date to to be parsed."),
                        new FunctionParameterSequenceType("simple-date-format", Type.STRING, Cardinality.EXACTLY_ONE, "The format string according to the Java java.text.SimpleDateFormat class")
                },
                new FunctionReturnSequenceType(Type.DATE, Cardinality.EXACTLY_ONE, "the parsed xs:date"));

    public ParseDateFunction(XQueryContext context)
    {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
    {
        String strDate = args[0].itemAt(0).toString();
        String dateFormat = args[1].itemAt(0).toString();

        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        //ParsePosition pp = new ParsePosition(0);

        try
        {
            Date date = sdf.parse(strDate);

            GregorianCalendar cal = new GregorianCalendar();
            cal.setTime(date);

            return new DateValue(TimeUtils.getInstance().newXMLGregorianCalendar(cal));
        }
        catch(ParseException pe)
        {
            throw new XPathException(this, "Could not parse date string '" + strDate + "' for format '" + dateFormat + "': " + pe.getMessage(), pe);
        }
    }
}