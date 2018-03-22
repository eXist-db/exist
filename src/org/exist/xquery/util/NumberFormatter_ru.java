package org.exist.xquery.util;

import java.util.Locale;

/**
 * Russian formatter for numbers and dates.
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author Wolfgang
 */
public class NumberFormatter_ru extends NumberFormatter {

    public NumberFormatter_ru(Locale locale) {
        super(locale);
    }

    /*
         * TODO
         * "Один","Два","Три","Четыре","Пять","Шесть","Семь","Восемь","Девять","Одна","Две",
         * "Десять","Одиннадцать","Двенадцать","Тринадцать","Четырнадцать","Пятнадцать","Шестнадцать","Семнадцать","Восемнадцать","Девятнадцать",
         * "Двадцать","Тридцать","Сорок","Пятьдесят","Шестьдесят","Семьдесят","Восемьдесят","Девяносто",
         * "Сто","Двести","Триста","Четыреста","Пятьсот","Шестьсот","Семьсот","Восемьсот","Девятьсот",
         * "Тысяча","Тысячи","Тысяч",
         * "Миллион","Миллиона","Миллионов",
         * "Миллиард","Миллиарда","Миллиардов",
         * "Триллион","Триллиона","Триллионов",
         * "Ноль"
         */
    public String getOrdinalSuffix(long number) {
        return "";
    }
}