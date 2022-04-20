package org.exist.xquery.functions.integer;

import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.RuleBasedNumberFormat;
import org.apache.commons.lang3.StringUtils;
import org.exist.xquery.XPathException;

import java.math.BigInteger;
import java.util.*;

public class WordPicture extends IntegerPicture {

    enum CaseAndCaps {
        Upper,
        Lower,
        Capitalized;

        String formatAndConvert(int fromValue, String language, FormatModifier formatModifier) {

            Set<String> spelloutRuleSet = new HashSet<>();
            List<String> spelloutRuleList = new ArrayList<>();
            String defaultSpelloutRule = null;
            if (formatModifier.numbering == FormatModifier.Numbering.Cardinal) defaultSpelloutRule = "%spellout-cardinal";
            if (formatModifier.numbering == FormatModifier.Numbering.Ordinal) defaultSpelloutRule = "%spellout-ordinal";

            Locale locale = (new Locale.Builder()).setLanguage(language).build();
            RuleBasedNumberFormat ruleBasedNumberFormat = new RuleBasedNumberFormat( locale, RuleBasedNumberFormat.SPELLOUT );
            for (String ruleSetName : ruleBasedNumberFormat.getRuleSetNames()) {
                if (ruleSetName.startsWith(defaultSpelloutRule)) {
                    spelloutRuleSet.add(ruleSetName);
                    spelloutRuleList.add(ruleSetName);
                }
            }
            String spelloutRule = defaultSpelloutRule;
            if (!spelloutRuleSet.contains(defaultSpelloutRule)) {
                spelloutRule = spelloutRuleList.get(0);
            }

            if (formatModifier.variation != null) {
                String variantSpelloutRule = defaultSpelloutRule + "-" + formatModifier.variation;
                if (spelloutRuleSet.contains(variantSpelloutRule)) {
                    spelloutRule = variantSpelloutRule;
                } else if (spelloutRuleSet.contains(formatModifier.variation)) {
                    spelloutRule = formatModifier.variation;
                }
            }

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
