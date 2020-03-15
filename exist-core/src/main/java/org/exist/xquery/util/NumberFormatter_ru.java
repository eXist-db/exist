/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
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