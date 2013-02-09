package org.exist.xquery.util;

/**
 * Russian formatter for numbers and dates.
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author Wolfgang
 */
public class NumberFormatter_ru extends NumberFormatter {

    public static String[] DAYS = { "Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота" };

    public static String[] MONTHS = { "Января", "Февраля", "Марта", "Апреля", "Мая", "Июня", "Июля", "Августа", "Сентября", "Октября", "Ноября", "Декабря" };
    //TODO "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    
    @Override
    public String getMonth(int month) {
        return MONTHS[month - 1];
    }

    @Override
    public String getDay(int day) {
        return DAYS[day - 1];
    }

    @Override
    public String getAmPm(int hour) {
        if (hour > 12)
            {return "pm";}
        else
            {return "am";}
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