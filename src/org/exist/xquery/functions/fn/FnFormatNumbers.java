/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.lang.String;
import java.math.BigDecimal;

/**
 * fn:format-number($value as numeric?, $picture as xs:string) as xs:string 
 * fn:format-number($value as numeric?, $picture as xs:string, $decimal-format-name as xs:string) as xs:string 
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class FnFormatNumbers extends BasicFunction {

	private static final SequenceType NUMBER_PARAMETER = new FunctionParameterSequenceType("value", Type.NUMBER, Cardinality.ZERO_OR_ONE, "The number to format");

	private static final SequenceType PICTURE = new FunctionParameterSequenceType("picture", Type.STRING, Cardinality.EXACTLY_ONE, "The format pattern string.  Please see the JavaDoc for java.text.DecimalFormat to get the specifics of this format string.");
	private static final String PICTURE_DESCRIPTION = "The formatting of a number is controlled by a picture string. The picture string is a sequence of ·characters·, in which the characters assigned to the variables decimal-separator-sign, grouping-sign, decimal-digit-family, optional-digit-sign and pattern-separator-sign are classified as active characters, and all other characters (including the percent-sign and per-mille-sign) are classified as passive characters.";

	private static final SequenceType DECIMAL_FORMAT = new FunctionParameterSequenceType("decimal-format-name", Type.STRING, Cardinality.EXACTLY_ONE, "The decimal-format name must be a QName, which is expanded as described in [2.4 Qualified Names]. It is an error if the stylesheet does not contain a declaration of the decimal-format with the specified expanded-name.");
	private static final String DECIMAL_FORMAT_DESCRIPTION = "";

	private static final FunctionReturnSequenceType FUNCTION_RETURN_TYPE = new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE, "the formatted string");

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
				new QName("format-number", Function.BUILTIN_FUNCTION_NS, FnModule.PREFIX),
				PICTURE_DESCRIPTION,
				new SequenceType[] {NUMBER_PARAMETER, PICTURE},
				FUNCTION_RETURN_TYPE
		),
		new FunctionSignature(
				new QName("format-number", Function.BUILTIN_FUNCTION_NS, FnModule.PREFIX),
				DECIMAL_FORMAT_DESCRIPTION,
				new SequenceType[] {NUMBER_PARAMETER, PICTURE, DECIMAL_FORMAT},
				FUNCTION_RETURN_TYPE
		)
	};
	
	/**
	 * @param context
	 */
	public FnFormatNumbers(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {

		if (args[0].isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}
		
		final NumericValue numericValue = (NumericValue)args[0].itemAt(0);
		
		try {
			final Formatter[] formatters = prepare(args[1].getStringValue());
			final String value = format(formatters[0], numericValue);
			return new StringValue(value);
		} catch (final java.lang.IllegalArgumentException e) {
			e.printStackTrace();
			throw new XPathException(e.getMessage());
		}
	}
	
	private String format(Formatter f, NumericValue numericValue) throws XPathException {
		if (numericValue.isNaN()) {
			return NaN;
		}

		final String minuSign = numericValue.isNegative()? String.valueOf(MINUS_SIGN) : "";
		if (numericValue.isInfinite()) {
			return minuSign + f.prefix + INFINITY + f.suffix;
		}
		
		NumericValue factor = null;
		if (f.isPercent) {
			factor = new IntegerValue(100);
		} else if (f.isPerMille) {
			factor = new IntegerValue(1000);
		}
		
		if (factor != null) {
			try {
				numericValue = (NumericValue) numericValue.mult(factor);
			} catch (final XPathException e) {
				e.printStackTrace();
				throw e;
			}
		}
		
		int pl = 0;
		
		final StringBuilder sb = new StringBuilder();
		
		if (numericValue.hasFractionalPart()) {
			
			BigDecimal val = ((DecimalValue)numericValue.convertTo(Type.DECIMAL)).getValue();

			val = val.setScale(f.flMAX, BigDecimal.ROUND_HALF_EVEN);
			
			final String number = val.toPlainString();
				
			sb.append(number);
			
			pl = number.indexOf('.');
			if (pl < 0) {
				sb.append('.');
				for (int i = 0; i < f.flMIN; i++) {
					sb.append('0');
				}
			} else {
				
			}

		} else {
			final String str = numericValue.getStringValue();
			pl = str.length();
			formatInt(str, sb, f);
		}
		
		if (f.mg != 0) {
			int pos = pl - f.mg;
			while (pos > 0) {
				sb.insert(pos, ',');
				pos -= f.mg;
			}
		}

		return sb.toString();
	}
	
	private void formatInt(String number, StringBuilder sb, Formatter f) {
		final int leadingZ = f.mlMIN - number.length();
		for (int i = 0; i < leadingZ; i++) {
			sb.append('0');
		}
		sb.append(number);
		if (f.flMIN > 0) {
			sb.append(".");
			for (int i = 0; i < f.mlMIN; i++) {
				sb.append('0');
			}
		}
	}

	private final char DECIMAL_SEPARATOR_SIGN = '.';
	private final char GROUPING_SEPARATOR_SIGN = ',';
	private final String INFINITY = "Infinity";
	private final char MINUS_SIGN = '-';
	private final String NaN = "NaN";
	private final char PERCENT_SIGN = '%';
	private final char PER_MILLE_SIGN = '\u2030';
	private final char MANDATORY_DIGIT_SIGN = '0';
	private final char OPTIONAL_DIGIT_SIGN = '#';
	private final char PATTERN_SEPARATOR_SIGN = ';';
	
	private Formatter[] prepare(String picture) throws XPathException {
		if (picture.length() == 0)
			{throw new XPathException(this, ErrorCodes.XTDE1310, "format-number() picture is zero-length");}
		
		final String[] pics = picture.split(String.valueOf(PATTERN_SEPARATOR_SIGN));
		
		final Formatter[] formatters = new Formatter[2];
		for (int i = 0; i < pics.length; i++) {
			formatters[i] = new Formatter(pics[i]);
		}
		
		return formatters;
	}
	
	class Formatter {

		String prefix = "", suffix = "";

		boolean ds = false, isPercent = false, isPerMille = false;
		int mlMAX = 0, flMAX = 0;
		int mlMIN = 0, flMIN = 0;
		
		int mg = 0, fg = 0;

		public Formatter(String picture) throws XPathException {
			if ( ! ( 
					picture.contains(String.valueOf(OPTIONAL_DIGIT_SIGN)) 
					|| picture.contains(String.valueOf(MANDATORY_DIGIT_SIGN))
					)
				) {
				
				throw new XPathException(FnFormatNumbers.this, ErrorCodes.XTDE1310, 
					"A sub-picture must contain at least one character that is an optional-digit-sign or a member of the decimal-digit-family.");
			}
			
			int bmg = -1, bfg = -1;

			// 0 - beginning passive-chars
			// 1 - digit signs
			// 2 - zero signs
			// 3 - fractional zero signs
			// 4 - fractional digit signs
			// 5 - ending passive-chars
			short phase = 0;
			
			for (int i = 0; i < picture.length(); i++) {
				char ch = picture.charAt(i);
				
				switch (ch) {
				case OPTIONAL_DIGIT_SIGN:
					
					switch (phase) {
					case 0:
					case 1:
						mlMAX++;
						phase = 1;
						break;

					case 2:
						throw new XPathException(FnFormatNumbers.this, ErrorCodes.XTDE1310,
							"");

					case 3:
					case 4:
						flMAX++;
						phase = 4;
						break;

					case 5:
						throw new XPathException(FnFormatNumbers.this, ErrorCodes.XTDE1310, 
							"A sub-picture must not contain a passive character that is preceded by an active character and that is followed by another active character. " +
							"Found at optional-digit-sign.");
					}
					
					break;

				case MANDATORY_DIGIT_SIGN:
					
					switch (phase) {
					case 0:
					case 1:
					case 2:
						mlMIN++;
						mlMAX++;
						phase = 2;
						break;

					case 3:
						flMIN++;
						flMAX++;
						break;

					case 4:
						throw new XPathException(FnFormatNumbers.this, ErrorCodes.XTDE1310,
							"");

					case 5:
						throw new XPathException(FnFormatNumbers.this, ErrorCodes.XTDE1310, 
							"A sub-picture must not contain a passive character that is preceded by an active character and that is followed by another active character. " +
							"Found at mandatory-digit-sign.");
					}

					break;

				case GROUPING_SEPARATOR_SIGN:
					
					switch (phase) {
					case 0:
					case 1:
					case 2:
						if (bmg == -1) {
							bmg = i;
						} else {
							mg = i - bmg;
							bmg = -1;
						}
						break;

					case 3:
					case 4:
						if (bfg == -1) {
							bfg = i;
						} else {
							fg = i - bfg;
							bfg = -1;
						}
						break;
					case 5:
						throw new XPathException(FnFormatNumbers.this, ErrorCodes.XTDE1310, 
							"A sub-picture must not contain a passive character that is preceded by an active character and that is followed by another active character. " +
							"Found at grouping-separator-sign.");
					}

					break;

				case DECIMAL_SEPARATOR_SIGN:
					
					switch (phase) {
					case 0:
					case 1:
					case 2:
						if (bmg != -1) {
							mg = i - bmg - 1;
							bmg = -1;
						}
						ds = true;
						phase = 3;
						break;

					case 3:
					case 4:
					case 5:
						if (ds)
							{throw new XPathException(FnFormatNumbers.this, ErrorCodes.XTDE1310, 
								"A sub-picture must not contain more than one decimal-separator-sign.");}

						throw new XPathException(FnFormatNumbers.this, ErrorCodes.XTDE1310, 
							"A sub-picture must not contain a passive character that is preceded by an active character and that is followed by another active character. " +
							"Found at decimal-separator-sign.");
					}

					break;

				case PERCENT_SIGN:
				case PER_MILLE_SIGN:
					if (isPercent || isPerMille)
						{throw new XPathException(FnFormatNumbers.this, ErrorCodes.XTDE1310, 
							"A sub-picture must not contain more than one percent-sign or per-mille-sign, and it must not contain one of each.");}
					
					isPercent = ch == PERCENT_SIGN;
					isPerMille = ch == PER_MILLE_SIGN;
					
					switch (phase) {
					case 0:
						prefix += ch;
						break;
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						phase = 5;
						suffix += ch;
						break;
					}

					break;

				default:
					//passive chars
					switch (phase) {
					case 0:
						prefix += ch;
						break;
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						if (bmg != -1) {
							mg = i - bmg - 1;
							bmg = -1;
						}
						suffix += ch;
						phase = 5;
						break;
					}
					break;
				}
			}
			
			if (mlMIN == 0 && !ds) {mlMIN = 1;}
			
//			System.out.println("prefix = "+prefix);
//			System.out.println("suffix = "+suffix);
//			System.out.println("ds = "+ds);
//			System.out.println("isPercent = "+isPercent);
//			System.out.println("isPerMille = "+isPerMille);
//			System.out.println("ml = "+mlMAX);
//			System.out.println("fl = "+flMAX);
//			System.out.println("mg = "+mg);
//			System.out.println("fg = "+fg);
		}
	}
}
