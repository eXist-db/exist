package org.exist.xquery.functions.integer;

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class FormatModifier {

    enum Numbering {Cardinal, Ordinal}

    ;

    enum LetterSequence {Alphabetic, Traditional}

    ;

    final String modifier;
    final boolean isEmpty;
    Numbering numbering = Numbering.Cardinal;
    String variation;
    LetterSequence letterSequence = LetterSequence.Alphabetic;

    final static Pattern modifierPattern = Pattern.compile("^(?:([co])(\\((.+)\\))?)?([at])?$");

    FormatModifier(final String modifier) throws XPathException {
        this.modifier = modifier;
        this.isEmpty = modifier.isEmpty();
        if (!isEmpty) {
            parseModifier();
        }
    }

    private void parseModifier() throws XPathException {
        Matcher m = modifierPattern.matcher(modifier);
        if (!m.matches()) {
            throw new XPathException(ErrorCodes.FODF1310, "Modifier " + modifier + " is not a valid pattern modifier");
        }
        String n = m.group(1);
        if (n != null) {
            if (n.equals("c")) numbering = Numbering.Cardinal;
            if (n.equals("o")) numbering = Numbering.Ordinal;
        }
        String v = m.group(3);
        if (v != null) {
            variation = v;
        }
        String l = m.group(4);
        if (l != null) {
            if (l.equals("a")) letterSequence = LetterSequence.Alphabetic;
            if (l.equals("t")) letterSequence = LetterSequence.Traditional;
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("numbering=").append(numbering).append("::");
        sb.append("variation=").append(variation).append("::");
        sb.append("lettersequence=").append(letterSequence).append("::");
        return sb.substring(0, sb.length() - 2);
    }
}
