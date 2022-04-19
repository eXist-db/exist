package org.exist.xquery.functions.integer;

import org.apache.commons.lang3.StringUtils;
import org.exist.xquery.XPathException;

import pl.allegro.finance.tradukisto.ValueConverters;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Optional;

public class WordPicture extends IntegerPicture {

    enum CaseAndCaps {
        Upper,
        Lower,
        Capitalized;

        String convert(int fromValue, String language) {

            ValueConverters converter = ValueConverters.getByLanguageCodeOrDefault(language, ValueConverters.ENGLISH_INTEGER);
            String formatted = converter.asWords(fromValue);

            String result = null;
            switch (this) {
                case Lower:
                    result = formatted;
                    break;
                case Upper:
                    Locale locale = (new Locale.Builder()).setLanguage(language).build();
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
