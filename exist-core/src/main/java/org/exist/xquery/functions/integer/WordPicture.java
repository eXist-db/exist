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

    static Set<String> GetSpelloutRules(final Locale locale, final String defaultSpelloutRule) {
        final RuleBasedNumberFormat ruleBasedNumberFormat = new RuleBasedNumberFormat(locale, RuleBasedNumberFormat.SPELLOUT);

        final Set<String> spelloutRuleSet = new HashSet<>();
        for (final String ruleSetName : ruleBasedNumberFormat.getRuleSetNames()) {
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
    static String GetSpellout(final Locale locale, final FormatModifier formatModifier) {

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
    static String GetSpellout(final Locale locale, final FormatModifier formatModifier, final String defaultSpelloutRule) {

        final Set<String> spelloutRuleSet = GetSpelloutRules(locale, defaultSpelloutRule);

        if (formatModifier.variation != null) {
            final String variantSpelloutRule;
            if (formatModifier.variation.startsWith("-")) {
                variantSpelloutRule = defaultSpelloutRule + formatModifier.variation;
            } else {
                variantSpelloutRule = defaultSpelloutRule + "-" + formatModifier.variation;
            }
            if (spelloutRuleSet.contains(variantSpelloutRule)) {
                return variantSpelloutRule;
            } else if (spelloutRuleSet.contains(formatModifier.variation)) {
                return formatModifier.variation;
            }
        }

        if (spelloutRuleSet.contains(defaultSpelloutRule)) {
            return defaultSpelloutRule;
        }
        for (final String extension : SPELLOUT_EXTENSIONS) {
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

        String formatAndConvert(final int fromValue, final String language, final FormatModifier formatModifier) {

            final Locale locale = (new Locale.Builder()).setLanguage(language).build();
            final String spelloutRule = GetSpellout(locale, formatModifier);

            final MessageFormat ruleBasedMessageFormatFormat
                    = new MessageFormat("{0,spellout," + spelloutRule + "}"
                    , locale);
            final String formatted = ruleBasedMessageFormatFormat.format(new Object[]{fromValue});

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
    }

    CaseAndCaps capitalization;
    FormatModifier formatModifier;

    WordPicture(final CaseAndCaps capitalization, final FormatModifier formatModifier) {
        this.capitalization = capitalization;
        this.formatModifier = formatModifier;
    }

    @Override
    public String formatInteger(final BigInteger bigInteger, final String language) throws XPathException {
        //spec says out of range should be formatted by "1"
        if (bigInteger.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0 || bigInteger.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            return DEFAULT.formatInteger(bigInteger, language);
        }

        final BigInteger absInteger = bigInteger.abs();
        String prefix = "";
        if (absInteger.compareTo(bigInteger) != 0) {
            prefix = "-";
        }
        return prefix + capitalization.formatAndConvert(absInteger.intValue(), language, formatModifier);
    }
}
