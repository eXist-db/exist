package org.exist.xquery.functions.integer;

import com.ibm.icu.text.RuleBasedNumberFormat;
import org.apache.commons.lang3.StringUtils;
import org.exist.xquery.XPathException;

import java.math.BigInteger;
import java.util.Locale;

public class WordPicture extends IntegerPicture {

    enum CaseAndCaps {
        Upper,
        Lower,
        Capitalized;

        String convert(int fromValue, String language) {

            Locale locale = (new Locale.Builder()).setLanguage(language).build();
            RuleBasedNumberFormat ruleBasedNumberFormat = new RuleBasedNumberFormat( locale, RuleBasedNumberFormat.SPELLOUT );
            String formatted = ruleBasedNumberFormat.format(fromValue);

            String result = null;
            switch (this) {
                case Lower:
                    result = formatted;
                    break;
                case Upper:
                    result = formatted.toUpperCase(locale);
                    break;
                 case Capitalized:
                    result = StringUtils.capitalize(formatted);
                    break;
            }
            return result;
        }
    };

    CaseAndCaps capitalization;

    WordPicture(CaseAndCaps capitalization) {
        this.capitalization = capitalization;
    }

    @Override
    public String formatInteger(BigInteger bigInteger, String language) throws XPathException {
        //spec says out of range should be formatted by "1"
        if (bigInteger.compareTo(BigInteger.ZERO) <= 0 || bigInteger.compareTo(BigInteger.valueOf(4999L)) > 0) {
            return DEFAULT.formatInteger(bigInteger, language);
        }

        return capitalization.convert(bigInteger.intValue(), language);
    }
}
