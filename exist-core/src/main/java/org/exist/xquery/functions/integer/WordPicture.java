package org.exist.xquery.functions.integer;

import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.RuleBasedNumberFormat;
import org.apache.commons.lang3.StringUtils;
import org.exist.xquery.XPathException;

import java.math.BigInteger;
import java.util.*;

public class WordPicture extends IntegerPicture {

    final static String DEFAULT_SPELLOUT_CARDINAL = "%spellout-cardinal";
    final static String DEFAULT_SPELLOUT_ORDINAL = "%spellout-ordinal";
    final static List<String> SPELLOUT_EXTENSIONS = Arrays.asList("-feminine", "-masculine", "-neuter", "-native", "-common");

    static Set<String> GetSpelloutRules(Locale locale, final String defaultSpelloutRule) {
        RuleBasedNumberFormat ruleBasedNumberFormat = new RuleBasedNumberFormat(locale, RuleBasedNumberFormat.SPELLOUT);

        Set<String> spelloutRuleSet = new HashSet<>();
        for (String ruleSetName : ruleBasedNumberFormat.getRuleSetNames()) {
            if (ruleSetName.startsWith(defaultSpelloutRule)) {
                spelloutRuleSet.add(ruleSetName);
            }
        }

        return spelloutRuleSet;
    }

    /**
     * Pick the best match spellout for a language
     *
     * @param locale       to pick a spellout for
     * @param formatModifier ordinal or cardinal ? Any hints at the spellout required ?
     * @return our best guess at an appropriate spellout
     */
    static String GetSpellout(Locale locale, FormatModifier formatModifier) {

        String defaultSpelloutRule = null;
        if (formatModifier.numbering == FormatModifier.Numbering.Cardinal)
            defaultSpelloutRule = DEFAULT_SPELLOUT_CARDINAL;
        if (formatModifier.numbering == FormatModifier.Numbering.Ordinal)
            defaultSpelloutRule = DEFAULT_SPELLOUT_ORDINAL;

        String spellout = GetSpellout(locale, formatModifier, defaultSpelloutRule);
        if (spellout == null && formatModifier.numbering == FormatModifier.Numbering.Ordinal) {
            // Back off to cardinal if we can't get an ordinal spellout
            spellout = GetSpellout(locale, formatModifier, DEFAULT_SPELLOUT_CARDINAL);
        }
        return spellout;
    }

    /**
     * Pick the best match spellout for a language
     *
     * @param locale       to pick a spellout for
     * @param formatModifier ordinal or cardinal ? Any hints at the spellout required ?
     * @return our best guess at an appropriate spellout
     */
    static String GetSpellout(Locale locale, FormatModifier formatModifier, final String defaultSpelloutRule) {

        Set<String> spelloutRuleSet = GetSpelloutRules(locale, defaultSpelloutRule);

        if (formatModifier.variation != null) {
            String variantSpelloutRule = defaultSpelloutRule + "-" + formatModifier.variation;
            if (spelloutRuleSet.contains(variantSpelloutRule)) {
                return variantSpelloutRule;
            } else if (spelloutRuleSet.contains(formatModifier.variation)) {
                return formatModifier.variation;
            }
        }

        if (spelloutRuleSet.contains(defaultSpelloutRule)) {
            return defaultSpelloutRule;
        }
        for (String extension : SPELLOUT_EXTENSIONS) {
            if (spelloutRuleSet.contains(defaultSpelloutRule + extension)) {
                return defaultSpelloutRule + extension;
            }
        }
        return null;
    }

    enum CaseAndCaps {
        Upper,
        Lower,
        Capitalized;

        String formatAndConvert(int fromValue, String language, FormatModifier formatModifier) {

            Locale locale = (new Locale.Builder()).setLanguage(language).build();
            String spelloutRule = GetSpellout(locale, formatModifier);

            MessageFormat ruleBasedMessageFormatFormat
                    = new MessageFormat("{0,spellout," + spelloutRule + "}"
                    , locale);
            String formatted = ruleBasedMessageFormatFormat.format(new Object[]{fromValue});

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
    FormatModifier formatModifier;

    WordPicture(CaseAndCaps capitalization, FormatModifier formatModifier) {
        this.capitalization = capitalization;
        this.formatModifier = formatModifier;
    }

    @Override
    public String formatInteger(BigInteger bigInteger, String language) throws XPathException {
        //spec says out of range should be formatted by "1"
        if (bigInteger.compareTo(BigInteger.ZERO) <= 0 || bigInteger.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            return DEFAULT.formatInteger(bigInteger, language);
        }

        return capitalization.formatAndConvert(bigInteger.intValue(), language, formatModifier);
    }
}
