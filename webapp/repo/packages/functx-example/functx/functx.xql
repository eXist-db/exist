
(:~

 : --------------------------------
 : The FunctX XQuery Function Library
 : --------------------------------

 : Copyright (C) 2007 Datypic

 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License.

 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.

 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

 : For more information on the FunctX XQuery library, contact contrib@functx.com.

 : @version 1.0
 : @see     http://www.xqueryfunctions.com
 :) 
module namespace  functx = "http://www.functx.com" ;
 
(:~
 : Adds attributes to XML elements 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_add-attributes.html 
 : @param   $elements the element(s) to which you wish to add the attribute 
 : @param   $attrNames the name(s) of the attribute(s) to add 
 : @param   $attrValues the value(s) of the attribute(s) to add 
 :) 
declare function functx:add-attributes 
  ( $elements as element()* ,
    $attrNames as xs:QName* ,
    $attrValues as xs:anyAtomicType* )  as element()? {
       
   for $element in $elements
   return element { node-name($element)}
                  { for $attrName at $seq in $attrNames
                    return if ($element/@*[node-name(.) = $attrName])
                           then ()
                           else attribute {$attrName}
                                          {$attrValues[$seq]},
                    $element/@*,
                    $element/node() }
 } ;

(:~
 : Adds months to a date 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_add-months.html 
 : @param   $date the date 
 : @param   $months the number of months to add 
 :) 
declare function functx:add-months 
  ( $date as xs:anyAtomicType? ,
    $months as xs:integer )  as xs:date? {
       
   xs:date($date) + functx:yearMonthDuration(0,$months)
 } ;

(:~
 : Adds attributes to XML elements 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_add-or-update-attributes.html 
 : @param   $elements the element(s) to which you wish to add the attribute 
 : @param   $attrNames the name(s) of the attribute(s) to add 
 : @param   $attrValues the value(s) of the attribute(s) to add 
 :) 
declare function functx:add-or-update-attributes 
  ( $elements as element()* ,
    $attrNames as xs:QName* ,
    $attrValues as xs:anyAtomicType* )  as element()? {
       
   for $element in $elements
   return element { node-name($element)}
                  { for $attrName at $seq in $attrNames
                    return attribute {$attrName}
                                     {$attrValues[$seq]},
                    $element/@*[not(node-name(.) = $attrNames)],
                    $element/node() }
 } ;

(:~
 : Whether a value is all whitespace or a zero-length string 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_all-whitespace.html 
 : @param   $arg the string (or node) to test 
 :) 
declare function functx:all-whitespace 
  ( $arg as xs:string? )  as xs:boolean {
       
   normalize-space($arg) = ''
 } ;

(:~
 : Whether all the values in a sequence are distinct 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_are-distinct-values.html 
 : @param   $seq the sequence of values 
 :) 
declare function functx:are-distinct-values 
  ( $seq as xs:anyAtomicType* )  as xs:boolean {
       
   count(distinct-values($seq)) = count($seq)
 } ;

(:~
 : The built-in type of an atomic value 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_atomic-type.html 
 : @param   $values the value(s) whose type you want to determine 
 :) 
declare function functx:atomic-type 
  ( $values as xs:anyAtomicType* )  as xs:string* {
       
 for $val in $values
 return
 (if ($val instance of xs:untypedAtomic) then 'xs:untypedAtomic'
 else if ($val instance of xs:anyURI) then 'xs:anyURI'
 else if ($val instance of xs:ENTITY) then 'xs:ENTITY'
 else if ($val instance of xs:ID) then 'xs:ID'
 else if ($val instance of xs:NMTOKEN) then 'xs:NMTOKEN'
 else if ($val instance of xs:language) then 'xs:language'
 else if ($val instance of xs:NCName) then 'xs:NCName'
 else if ($val instance of xs:Name) then 'xs:Name'
 else if ($val instance of xs:token) then 'xs:token'
 else if ($val instance of xs:normalizedString)
         then 'xs:normalizedString'
 else if ($val instance of xs:string) then 'xs:string'
 else if ($val instance of xs:QName) then 'xs:QName'
 else if ($val instance of xs:boolean) then 'xs:boolean'
 else if ($val instance of xs:base64Binary) then 'xs:base64Binary'
 else if ($val instance of xs:hexBinary) then 'xs:hexBinary'
 else if ($val instance of xs:byte) then 'xs:byte'
 else if ($val instance of xs:short) then 'xs:short'
 else if ($val instance of xs:int) then 'xs:int'
 else if ($val instance of xs:long) then 'xs:long'
 else if ($val instance of xs:unsignedByte) then 'xs:unsignedByte'
 else if ($val instance of xs:unsignedShort) then 'xs:unsignedShort'
 else if ($val instance of xs:unsignedInt) then 'xs:unsignedInt'
 else if ($val instance of xs:unsignedLong) then 'xs:unsignedLong'
 else if ($val instance of xs:positiveInteger)
         then 'xs:positiveInteger'
 else if ($val instance of xs:nonNegativeInteger)
         then 'xs:nonNegativeInteger'
 else if ($val instance of xs:negativeInteger)
         then 'xs:negativeInteger'
 else if ($val instance of xs:nonPositiveInteger)
         then 'xs:nonPositiveInteger'
 else if ($val instance of xs:integer) then 'xs:integer'
 else if ($val instance of xs:decimal) then 'xs:decimal'
 else if ($val instance of xs:float) then 'xs:float'
 else if ($val instance of xs:double) then 'xs:double'
 else if ($val instance of xs:date) then 'xs:date'
 else if ($val instance of xs:time) then 'xs:time'
 else if ($val instance of xs:dateTime) then 'xs:dateTime'
 else if ($val instance of xs:dayTimeDuration)
         then 'xs:dayTimeDuration'
 else if ($val instance of xs:yearMonthDuration)
         then 'xs:yearMonthDuration'
 else if ($val instance of xs:duration) then 'xs:duration'
 else if ($val instance of xs:gMonth) then 'xs:gMonth'
 else if ($val instance of xs:gYear) then 'xs:gYear'
 else if ($val instance of xs:gYearMonth) then 'xs:gYearMonth'
 else if ($val instance of xs:gDay) then 'xs:gDay'
 else if ($val instance of xs:gMonthDay) then 'xs:gMonthDay'
 else 'unknown')
 } ;

(:~
 : The average, counting "empty" values as zero 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_avg-empty-is-zero.html 
 : @param   $values the values to be averaged 
 : @param   $allNodes the sequence of all nodes to find the average over 
 :) 
declare function functx:avg-empty-is-zero 
  ( $values as xs:anyAtomicType* ,
    $allNodes as node()* )  as xs:double {
       
   if (empty($allNodes))
   then 0
   else sum($values[string(.) != '']) div count($allNodes)
 } ;

(:~
 : Whether a value is between two provided values 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_between-exclusive.html 
 : @param   $value the value to be tested 
 : @param   $minValue the minimum value 
 : @param   $maxValue the maximum value 
 :) 
declare function functx:between-exclusive 
  ( $value as xs:anyAtomicType? ,
    $minValue as xs:anyAtomicType ,
    $maxValue as xs:anyAtomicType )  as xs:boolean {
       
   $value > $minValue and $value < $maxValue
 } ;

(:~
 : Whether a value is between two provided values, or equal to one of them 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_between-inclusive.html 
 : @param   $value the value to be tested 
 : @param   $minValue the minimum value 
 : @param   $maxValue the maximum value 
 :) 
declare function functx:between-inclusive 
  ( $value as xs:anyAtomicType? ,
    $minValue as xs:anyAtomicType ,
    $maxValue as xs:anyAtomicType )  as xs:boolean {
       
   $value >= $minValue and $value <= $maxValue
 } ;

(:~
 : Turns a camelCase string into space-separated words 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_camel-case-to-words.html 
 : @param   $arg the string to modify 
 : @param   $delim the delimiter for the words (e.g. a space) 
 :) 
declare function functx:camel-case-to-words 
  ( $arg as xs:string? ,
    $delim as xs:string )  as xs:string {
       
   concat(substring($arg,1,1),
             replace(substring($arg,2),'(\p{Lu})',
                        concat($delim, '$1')))
 } ;

(:~
 : Capitalizes the first character of a string 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_capitalize-first.html 
 : @param   $arg the word or phrase to capitalize 
 :) 
declare function functx:capitalize-first 
  ( $arg as xs:string? )  as xs:string? {
       
   concat(upper-case(substring($arg,1,1)),
             substring($arg,2))
 } ;

(:~
 : Changes the names of elements in an XML fragment 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_change-element-names-deep.html 
 : @param   $nodes the element(s) to change 
 : @param   $oldNames the sequence of names to change from 
 : @param   $newNames the sequence of names to change to 
 :) 
declare function functx:change-element-names-deep 
  ( $nodes as node()* ,
    $oldNames as xs:QName* ,
    $newNames as xs:QName* )  as node()* {
       
  if (count($oldNames) != count($newNames))
  then error(xs:QName('functx:Different_number_of_names'))
  else
   for $node in $nodes
   return if ($node instance of element())
          then element
                 {functx:if-empty
                    ($newNames[index-of($oldNames,
                                           node-name($node))],
                     node-name($node)) }
                 {$node/@*,
                  functx:change-element-names-deep($node/node(),
                                           $oldNames, $newNames)}
          else if ($node instance of document-node())
          then functx:change-element-names-deep($node/node(),
                                           $oldNames, $newNames)
          else $node
 } ;

(:~
 : Changes the namespace of XML elements and its descendants 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_change-element-ns-deep.html 
 : @param   $nodes the nodes to change 
 : @param   $newns the new namespace 
 : @param   $prefix the prefix to use for the new namespace 
 :) 
declare function functx:change-element-ns-deep 
  ( $nodes as node()* ,
    $newns as xs:string ,
    $prefix as xs:string )  as node()* {
       
  for $node in $nodes
  return if ($node instance of element())
         then (element
               {QName ($newns,
                          concat($prefix,
                                    if ($prefix = '')
                                    then ''
                                    else ':',
                                    local-name($node)))}
               {$node/@*,
                functx:change-element-ns-deep($node/node(),
                                           $newns, $prefix)})
         else if ($node instance of document-node())
         then functx:change-element-ns-deep($node/node(),
                                           $newns, $prefix)
         else $node
 } ;

(:~
 : Changes the namespace of XML elements 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_change-element-ns.html 
 : @param   $elements the elements to change 
 : @param   $newns the new namespace 
 : @param   $prefix the prefix to use for the new namespace 
 :) 
declare function functx:change-element-ns 
  ( $elements as element()* ,
    $newns as xs:string ,
    $prefix as xs:string )  as element()? {
       
   for $element in $elements
   return
   element {QName ($newns,
                      concat($prefix,
                                if ($prefix = '')
                                then ''
                                else ':',
                                local-name($element)))}
           {$element/@*, $element/node()}
 } ;

(:~
 : Converts a string to a sequence of characters 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_chars.html 
 : @param   $arg the string to split 
 :) 
declare function functx:chars 
  ( $arg as xs:string? )  as xs:string* {
       
   for $ch in string-to-codepoints($arg)
   return codepoints-to-string($ch)
 } ;

(:~
 : Whether a string contains any of a sequence of strings 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_contains-any-of.html 
 : @param   $arg the string to test 
 : @param   $searchStrings the strings to look for 
 :) 
declare function functx:contains-any-of 
  ( $arg as xs:string? ,
    $searchStrings as xs:string* )  as xs:boolean {
       
   some $searchString in $searchStrings
   satisfies contains($arg,$searchString)
 } ;

(:~
 : Whether one string contains another, without regard to case 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_contains-case-insensitive.html 
 : @param   $arg the string to search 
 : @param   $substring the substring to find 
 :) 
declare function functx:contains-case-insensitive 
  ( $arg as xs:string? ,
    $substring as xs:string )  as xs:boolean? {
       
   contains(upper-case($arg), upper-case($substring))
 } ;

(:~
 : Whether one string contains another, as a separate word 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_contains-word.html 
 : @param   $arg the string to search 
 : @param   $word the word to find 
 :) 
declare function functx:contains-word 
  ( $arg as xs:string? ,
    $word as xs:string )  as xs:boolean {
       
   matches(upper-case($arg),
           concat('^(.*\W)?',
                     upper-case(functx:escape-for-regex($word)),
                     '(\W.*)?$'))
 } ;

(:~
 : Copies attributes from one element to another 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_copy-attributes.html 
 : @param   $copyTo the element to copy attributes to 
 : @param   $copyFrom the element to copy attributes from 
 :) 
declare function functx:copy-attributes 
  ( $copyTo as element() ,
    $copyFrom as element() )  as element() {
       
   element { node-name($copyTo)}
           { $copyTo/@*[not(node-name(.) = $copyFrom/@*/node-name(.))],
             $copyFrom/@*,
             $copyTo/node() }

 } ;

(:~
 : Construct a date from a year, month and day 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_date.html 
 : @param   $year the year 
 : @param   $month the month 
 : @param   $day the day 
 :) 
declare function functx:date 
  ( $year as xs:anyAtomicType ,
    $month as xs:anyAtomicType ,
    $day as xs:anyAtomicType )  as xs:date {
       
   xs:date(
     concat(
       functx:pad-integer-to-length(xs:integer($year),4),'-',
       functx:pad-integer-to-length(xs:integer($month),2),'-',
       functx:pad-integer-to-length(xs:integer($day),2)))
 } ;

(:~
 : Construct a date/time from individual components 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_datetime.html 
 : @param   $year the year 
 : @param   $month the month 
 : @param   $day the day 
 : @param   $hour the hour 
 : @param   $minute the minute 
 : @param   $second the second 
 :) 
declare function functx:dateTime 
  ( $year as xs:anyAtomicType ,
    $month as xs:anyAtomicType ,
    $day as xs:anyAtomicType ,
    $hour as xs:anyAtomicType ,
    $minute as xs:anyAtomicType ,
    $second as xs:anyAtomicType )  as xs:dateTime {
       
   xs:dateTime(
     concat(functx:date($year,$month,$day),'T',
             functx:time($hour,$minute,$second)))
 } ;

(:~
 : The day of the year (a number between 1 and 366) 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_day-in-year.html 
 : @param   $date the date 
 :) 
declare function functx:day-in-year 
  ( $date as xs:anyAtomicType? )  as xs:integer? {
       
  days-from-duration(
      xs:date($date) - functx:first-day-of-year($date)) + 1
 } ;

(:~
 : The abbreviated day of the week, from a date, in English 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_day-of-week-abbrev-en.html 
 : @param   $date the date 
 :) 
declare function functx:day-of-week-abbrev-en 
  ( $date as xs:anyAtomicType? )  as xs:string? {
       
   ('Sun', 'Mon', 'Tues', 'Wed', 'Thurs', 'Fri', 'Sat')
   [functx:day-of-week($date) + 1]
 } ;

(:~
 : The name of the day of the week, from a date, in English 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_day-of-week-name-en.html 
 : @param   $date the date 
 :) 
declare function functx:day-of-week-name-en 
  ( $date as xs:anyAtomicType? )  as xs:string? {
       
   ('Sunday', 'Monday', 'Tuesday', 'Wednesday',
    'Thursday', 'Friday', 'Saturday')
      [functx:day-of-week($date) + 1]
 } ;

(:~
 : The day of the week, from a date 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_day-of-week.html 
 : @param   $date the date 
 :) 
declare function functx:day-of-week 
  ( $date as xs:anyAtomicType? )  as xs:integer? {
       
  if (empty($date))
  then ()
  else xs:integer((xs:date($date) - xs:date('1901-01-06'))
          div xs:dayTimeDuration('P1D')) mod 7
 } ;

(:~
 : Construct a dayTimeDuration from a number of days, hours, etc. 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_daytimeduration.html 
 : @param   $days the number of days 
 : @param   $hours the number of hours 
 : @param   $minutes the number of minutes 
 : @param   $seconds the number of seconds 
 :) 
declare function functx:dayTimeDuration 
  ( $days as xs:decimal? ,
    $hours as xs:decimal? ,
    $minutes as xs:decimal? ,
    $seconds as xs:decimal? )  as xs:dayTimeDuration {
       
    (xs:dayTimeDuration('P1D') * functx:if-empty($days,0)) +
    (xs:dayTimeDuration('PT1H') * functx:if-empty($hours,0)) +
    (xs:dayTimeDuration('PT1M') * functx:if-empty($minutes,0)) +
    (xs:dayTimeDuration('PT1S') * functx:if-empty($seconds,0))
 } ;

(:~
 : Number of days in the month 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_days-in-month.html 
 : @param   $date the date 
 :) 
declare function functx:days-in-month 
  ( $date as xs:anyAtomicType? )  as xs:integer? {
       
   if (month-from-date(xs:date($date)) = 2 and
       functx:is-leap-year($date))
   then 29
   else
   (31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    [month-from-date(xs:date($date))]
 } ;

(:~
 : The depth (level) of a node in an XML tree 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_depth-of-node.html 
 : @param   $node the node to check 
 :) 
declare function functx:depth-of-node 
  ( $node as node()? )  as xs:integer {
       
   count($node/ancestor-or-self::node())
 } ;

(:~
 : The distinct names of all attributes in an XML fragment 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_distinct-attribute-names.html 
 : @param   $nodes the root to start from 
 :) 
declare function functx:distinct-attribute-names 
  ( $nodes as node()* )  as xs:string* {
       
   distinct-values($nodes//@*/name(.))
 } ;

(:~
 : The XML nodes with distinct values, taking into account attributes and descendants 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_distinct-deep.html 
 : @param   $nodes the sequence of nodes to test 
 :) 
declare function functx:distinct-deep 
  ( $nodes as node()* )  as node()* {
       
    for $seq in (1 to count($nodes))
    return $nodes[$seq][not(functx:is-node-in-sequence-deep-equal(
                          .,$nodes[position() < $seq]))]
 } ;

(:~
 : The distinct names of all elements in an XML fragment 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_distinct-element-names.html 
 : @param   $nodes the root(s) to start from 
 :) 
declare function functx:distinct-element-names 
  ( $nodes as node()* )  as xs:string* {
       
   distinct-values($nodes/descendant-or-self::*/name(.))
 } ;

(:~
 : The distinct paths of all descendant elements in an XML fragment 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_distinct-element-paths.html 
 : @param   $nodes the root(s) to start from 
 :) 
declare function functx:distinct-element-paths 
  ( $nodes as node()* )  as xs:string* {
       
   distinct-values(functx:path-to-node($nodes/descendant-or-self::*))
 } ;

(:~
 : The distinct XML nodes in a sequence (by node identity) 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_distinct-nodes.html 
 : @param   $nodes the node sequence 
 :) 
declare function functx:distinct-nodes 
  ( $nodes as node()* )  as node()* {
       
    for $seq in (1 to count($nodes))
    return $nodes[$seq][not(functx:is-node-in-sequence(
                                .,$nodes[position() < $seq]))]
 } ;

(:~
 : Converts a timezone like "-05:00" or "Z" into xs:dayTimeDuration 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_duration-from-timezone.html 
 : @param   $timezone the time zone, in (+|-)HH:MM format 
 :) 
declare function functx:duration-from-timezone 
  ( $timezone as xs:string )  as xs:dayTimeDuration {
       
   xs:dayTimeDuration(
     if (not(matches($timezone,'Z|[\+\-]\d{2}:\d{2}')))
     then error(xs:QName('functx:Invalid_Timezone_Value'))
     else if ($timezone = 'Z')
     then 'PT0S'
     else replace($timezone,'\+?(\d{2}):\d{2}','PT$1H')
        )
 } ;

(:~
 : Dynamically evaluates a simple XPath path 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_dynamic-path.html 
 : @param   $parent the root to start from 
 : @param   $path the path expression 
 :) 
declare function functx:dynamic-path 
  ( $parent as node() ,
    $path as xs:string )  as item()* {
       
  let $nextStep := functx:substring-before-if-contains($path,'/')
  let $restOfSteps := substring-after($path,'/')
  for $child in
    ($parent/*[functx:name-test(name(),$nextStep)],
     $parent/@*[functx:name-test(name(),
                              substring-after($nextStep,'@'))])
  return if ($restOfSteps)
         then functx:dynamic-path($child, $restOfSteps)
         else $child
 } ;

(:~
 : Escapes regex special characters 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_escape-for-regex.html 
 : @param   $arg the string to escape 
 :) 
declare function functx:escape-for-regex 
  ( $arg as xs:string? )  as xs:string {
       
   replace($arg,
           '(\.|\[|\]|\\|\||\-|\^|\$|\?|\*|\+|\{|\}|\(|\))','\\$1')
 } ;

(:~
 : Whether one (and only one) of two boolean values is true 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_exclusive-or.html 
 : @param   $arg1 the first boolean value 
 : @param   $arg2 the second boolean value 
 :) 
declare function functx:exclusive-or 
  ( $arg1 as xs:boolean? ,
    $arg2 as xs:boolean? )  as xs:boolean? {
       
   $arg1 != $arg2
 } ;

(:~
 : The first day of the month of a date 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_first-day-of-month.html 
 : @param   $date the date 
 :) 
declare function functx:first-day-of-month 
  ( $date as xs:anyAtomicType? )  as xs:date? {
       
   functx:date(year-from-date(xs:date($date)),
            month-from-date(xs:date($date)),
            1)
 } ;

(:~
 : The first day of the year of a date 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_first-day-of-year.html 
 : @param   $date the date 
 :) 
declare function functx:first-day-of-year 
  ( $date as xs:anyAtomicType? )  as xs:date? {
       
   functx:date(year-from-date(xs:date($date)), 1, 1)
 } ;

(:~
 : The XML node in a sequence that appears first in document order 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_first-node.html 
 : @param   $nodes the sequence of nodes 
 :) 
declare function functx:first-node 
  ( $nodes as node()* )  as node()? {
       
   ($nodes/.)[1]
 } ;

(:~
 : Whether an XML node follows another without being its descendant 
 :
 : @author  W3C XML Query Working Group 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_follows-not-descendant.html 
 : @param   $a the first node 
 : @param   $b the second node 
 :) 
declare function functx:follows-not-descendant 
  ( $a as node()? ,
    $b as node()? )  as xs:boolean {
       
   $a >> $b and empty($b intersect $a/ancestor::node())
 } ;

(:~
 : Moves title words like "the" and "a" to the end of strings 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_format-as-title-en.html 
 : @param   $titles the titles to format 
 :) 
declare function functx:format-as-title-en 
  ( $titles as xs:string* )  as xs:string* {
       
   let $wordsToMoveToEnd := ('A', 'An', 'The')
   for $title in $titles
   let $firstWord := functx:substring-before-match($title,'\W')
   return if ($firstWord = $wordsToMoveToEnd)
          then replace($title,'(.*?)\W(.*)', '$2, $1')
          else $title
 } ;

(:~
 : Returns the fragment from a URI 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_fragment-from-uri.html 
 : @param   $uri the URI 
 :) 
declare function functx:fragment-from-uri 
  ( $uri as xs:string? )  as xs:string? {
       
   substring-after($uri,'#')
 } ;

(:~
 : Splits a string into matching and non-matching regions 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_get-matches-and-non-matches.html 
 : @param   $string the string to split 
 : @param   $regex the pattern 
 :) 
declare function functx:get-matches-and-non-matches 
  ( $string as xs:string? ,
    $regex as xs:string )  as element()* {
       
   let $iomf := functx:index-of-match-first($string, $regex)
   return
   if (empty($iomf))
   then <non-match>{$string}</non-match>
   else
   if ($iomf > 1)
   then (<non-match>{substring($string,1,$iomf - 1)}</non-match>,
         functx:get-matches-and-non-matches(
            substring($string,$iomf),$regex))
   else
   let $length :=
      string-length($string) -
      string-length(functx:replace-first($string, $regex,''))
   return (<match>{substring($string,1,$length)}</match>,
           if (string-length($string) > $length)
           then functx:get-matches-and-non-matches(
              substring($string,$length + 1),$regex)
           else ())
 } ;

(:~
 : Return the matching regions of a string 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_get-matches.html 
 : @param   $string the string to split 
 : @param   $regex the pattern 
 :) 
declare function functx:get-matches 
  ( $string as xs:string? ,
    $regex as xs:string )  as xs:string* {
       
   functx:get-matches-and-non-matches($string,$regex)/
     string(self::match)
 } ;

(:~
 : Whether an element has element-only content 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_has-element-only-content.html 
 : @param   $element the XML element to test 
 :) 
declare function functx:has-element-only-content 
  ( $element as element() )  as xs:boolean {
       
   not($element/text()[normalize-space(.) != '']) and $element/*
 } ;

(:~
 : Whether an element has empty content 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_has-empty-content.html 
 : @param   $element the XML element to test 
 :) 
declare function functx:has-empty-content 
  ( $element as element() )  as xs:boolean {
       
   not($element/node())
 } ;

(:~
 : Whether an element has mixed content 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_has-mixed-content.html 
 : @param   $element the XML element to test 
 :) 
declare function functx:has-mixed-content 
  ( $element as element() )  as xs:boolean {
       
   $element/text()[normalize-space(.) != ''] and $element/*
 } ;

(:~
 : Whether an element has simple content 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_has-simple-content.html 
 : @param   $element the XML element to test 
 :) 
declare function functx:has-simple-content 
  ( $element as element() )  as xs:boolean {
       
   $element/text() and not($element/*)
 } ;

(:~
 : Gets the ID of an XML element 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_id-from-element.html 
 : @param   $element the element 
 :) 
declare function functx:id-from-element 
  ( $element as element()? )  as xs:string? {
       
  data(($element/@*[id(.) is ..])[1])
 } ;

(:~
 : Gets XML element(s) that have an attribute with a particular value 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_id-untyped.html 
 : @param   $node the root node(s) to start from 
 : @param   $id the "id" to find 
 :) 
declare function functx:id-untyped 
  ( $node as node()* ,
    $id as xs:anyAtomicType )  as element()* {
       
  $node//*[@* = $id]
 } ;

(:~
 : The first argument if it is not empty, otherwise the second argument 
 :
 : @author  W3C XML Query WG 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_if-absent.html 
 : @param   $arg the item(s) that may be absent 
 : @param   $value the item(s) to use if the item is absent 
 :) 
declare function functx:if-absent 
  ( $arg as item()* ,
    $value as item()* )  as item()* {
       
    if (exists($arg))
    then $arg
    else $value
 } ;

(:~
 : The first argument if it is not blank, otherwise the second argument 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_if-empty.html 
 : @param   $arg the node that may be empty 
 : @param   $value the item(s) to use if the node is empty 
 :) 
declare function functx:if-empty 
  ( $arg as item()? ,
    $value as item()* )  as item()* {
       
  if (string($arg) != '')
  then data($arg)
  else $value
 } ;

(:~
 : The position of a node in a sequence, based on contents and attributes 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_index-of-deep-equal-node.html 
 : @param   $nodes the node sequence 
 : @param   $nodeToFind the node to find in the sequence 
 :) 
declare function functx:index-of-deep-equal-node 
  ( $nodes as node()* ,
    $nodeToFind as node() )  as xs:integer* {
       
  for $seq in (1 to count($nodes))
  return $seq[deep-equal($nodes[$seq],$nodeToFind)]
 } ;

(:~
 : The first position of a matching substring 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_index-of-match-first.html 
 : @param   $arg the string 
 : @param   $pattern the pattern to match 
 :) 
declare function functx:index-of-match-first 
  ( $arg as xs:string? ,
    $pattern as xs:string )  as xs:integer? {
       
  if (matches($arg,$pattern))
  then string-length(tokenize($arg, $pattern)[1]) + 1
  else ()
 } ;

(:~
 : The position of a node in a sequence, based on node identity 
 :
 : @author  W3C XML Query Working Group 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_index-of-node.html 
 : @param   $nodes the node sequence 
 : @param   $nodeToFind the node to find in the sequence 
 :) 
declare function functx:index-of-node 
  ( $nodes as node()* ,
    $nodeToFind as node() )  as xs:integer* {
       
  for $seq in (1 to count($nodes))
  return $seq[$nodes[$seq] is $nodeToFind]
 } ;

(:~
 : The first position of a substring 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_index-of-string-first.html 
 : @param   $arg the string 
 : @param   $substring the substring to find 
 :) 
declare function functx:index-of-string-first 
  ( $arg as xs:string? ,
    $substring as xs:string )  as xs:integer? {
       
  if (contains($arg, $substring))
  then string-length(substring-before($arg, $substring))+1
  else ()
 } ;

(:~
 : The last position of a substring 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_index-of-string-last.html 
 : @param   $arg the string 
 : @param   $substring the substring to find 
 :) 
declare function functx:index-of-string-last 
  ( $arg as xs:string? ,
    $substring as xs:string )  as xs:integer? {
       
  functx:index-of-string($arg, $substring)[last()]
 } ;

(:~
 : The position(s) of a substring 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_index-of-string.html 
 : @param   $arg the string 
 : @param   $substring the substring to find 
 :) 
declare function functx:index-of-string 
  ( $arg as xs:string? ,
    $substring as xs:string )  as xs:integer* {
       
  if (contains($arg, $substring))
  then (string-length(substring-before($arg, $substring))+1,
        for $other in
           functx:index-of-string(substring-after($arg, $substring),
                               $substring)
        return
          $other +
          string-length(substring-before($arg, $substring)) +
          string-length($substring))
  else ()
 } ;

(:~
 : Inserts a string at a specified position 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_insert-string.html 
 : @param   $originalString the original string to insert into 
 : @param   $stringToInsert the string to insert 
 : @param   $pos the position 
 :) 
declare function functx:insert-string 
  ( $originalString as xs:string? ,
    $stringToInsert as xs:string? ,
    $pos as xs:integer )  as xs:string {
       
   concat(substring($originalString,1,$pos - 1),
             $stringToInsert,
             substring($originalString,$pos))
 } ;

(:~
 : Whether a value is numeric 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_is-a-number.html 
 : @param   $value the value to test 
 :) 
declare function functx:is-a-number 
  ( $value as xs:anyAtomicType? )  as xs:boolean {
       
   string(number($value)) != 'NaN'
 } ;

(:~
 : Whether a URI is absolute 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_is-absolute-uri.html 
 : @param   $uri the URI to test 
 :) 
declare function functx:is-absolute-uri 
  ( $uri as xs:string? )  as xs:boolean {
       
   matches($uri,'^[a-z]+:')
 } ;

(:~
 : Whether an XML node is an ancestor of another node 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_is-ancestor.html 
 : @param   $node1 the first node 
 : @param   $node2 the second node 
 :) 
declare function functx:is-ancestor 
  ( $node1 as node() ,
    $node2 as node() )  as xs:boolean {
       
   exists($node1 intersect $node2/ancestor::node())
 } ;

(:~
 : Whether an XML node is a descendant of another node 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_is-descendant.html 
 : @param   $node1 the first node 
 : @param   $node2 the second node 
 :) 
declare function functx:is-descendant 
  ( $node1 as node() ,
    $node2 as node() )  as xs:boolean {
       
   boolean($node2 intersect $node1/ancestor::node())
 } ;

(:~
 : Whether a date falls in a leap year 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_is-leap-year.html 
 : @param   $date the date or year 
 :) 
declare function functx:is-leap-year 
  ( $date as xs:anyAtomicType? )  as xs:boolean {
       
    for $year in xs:integer(substring(string($date),1,4))
    return ($year mod 4 = 0 and
            $year mod 100 != 0) or
            $year mod 400 = 0
 } ;

(:~
 : Whether an XML node is among the descendants of a sequence, based on contents and attributes 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_is-node-among-descendants-deep-equal.html 
 : @param   $node the node to test 
 : @param   $seq the sequence of nodes to search 
 :) 
declare function functx:is-node-among-descendants-deep-equal 
  ( $node as node()? ,
    $seq as node()* )  as xs:boolean {
       
   some $nodeInSeq in $seq/descendant-or-self::*/(.|@*)
   satisfies deep-equal($nodeInSeq,$node)
 } ;

(:~
 : Whether an XML node is among the descendants of a sequence, based on node identity 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_is-node-among-descendants.html 
 : @param   $node the node to test 
 : @param   $seq the sequence of nodes to search 
 :) 
declare function functx:is-node-among-descendants 
  ( $node as node()? ,
    $seq as node()* )  as xs:boolean {
       
   some $nodeInSeq in $seq/descendant-or-self::*/(.|@*)
   satisfies $nodeInSeq is $node
 } ;

(:~
 : Whether an XML node is in a sequence, based on contents and attributes 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_is-node-in-sequence-deep-equal.html 
 : @param   $node the node to test 
 : @param   $seq the sequence of nodes to search 
 :) 
declare function functx:is-node-in-sequence-deep-equal 
  ( $node as node()? ,
    $seq as node()* )  as xs:boolean {
       
   some $nodeInSeq in $seq satisfies deep-equal($nodeInSeq,$node)
 } ;

(:~
 : Whether an XML node is in a sequence, based on node identity 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_is-node-in-sequence.html 
 : @param   $node the node to test 
 : @param   $seq the sequence of nodes to search 
 :) 
declare function functx:is-node-in-sequence 
  ( $node as node()? ,
    $seq as node()* )  as xs:boolean {
       
   some $nodeInSeq in $seq satisfies $nodeInSeq is $node
 } ;

(:~
 : Whether an atomic value appears in a sequence  
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_is-value-in-sequence.html 
 : @param   $value the atomic value to test 
 : @param   $seq the sequence of values to search 
 :) 
declare function functx:is-value-in-sequence 
  ( $value as xs:anyAtomicType? ,
    $seq as xs:anyAtomicType* )  as xs:boolean {
       
   $value = $seq
 } ;

(:~
 : The last day of the month of a date 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_last-day-of-month.html 
 : @param   $date the date 
 :) 
declare function functx:last-day-of-month 
  ( $date as xs:anyAtomicType? )  as xs:date? {
       
   functx:date(year-from-date(xs:date($date)),
            month-from-date(xs:date($date)),
            functx:days-in-month($date))
 } ;

(:~
 : The last day of the month of a date 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_last-day-of-year.html 
 : @param   $date the date 
 :) 
declare function functx:last-day-of-year 
  ( $date as xs:anyAtomicType? )  as xs:date? {
       
   functx:date(year-from-date(xs:date($date)), 12, 31)
 } ;

(:~
 : The XML node in a sequence that is last in document order 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_last-node.html 
 : @param   $nodes the sequence of nodes 
 :) 
declare function functx:last-node 
  ( $nodes as node()* )  as node()? {
       
   ($nodes/.)[last()]
 } ;

(:~
 : All XML elements that don't have any child elements 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_leaf-elements.html 
 : @param   $root the root 
 :) 
declare function functx:leaf-elements 
  ( $root as node()? )  as element()* {
       
   $root/descendant-or-self::*[not(*)]
 } ;

(:~
 : Trims leading whitespace 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_left-trim.html 
 : @param   $arg the string to trim 
 :) 
declare function functx:left-trim 
  ( $arg as xs:string? )  as xs:string {
       
   replace($arg,'^\s+','')
 } ;

(:~
 : The number of lines 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_line-count.html 
 : @param   $arg the string to test 
 :) 
declare function functx:line-count 
  ( $arg as xs:string? )  as xs:integer {
       
   count(functx:lines($arg))
 } ;

(:~
 : Split a string into separate lines 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_lines.html 
 : @param   $arg the string to split 
 :) 
declare function functx:lines 
  ( $arg as xs:string? )  as xs:string* {
       
   tokenize($arg, '(\r\n?|\n\r?)')
 } ;

(:~
 : The maximum depth of elements in an XML tree 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_max-depth.html 
 : @param   $root the root to start from 
 :) 
declare function functx:max-depth 
  ( $root as node()? )  as xs:integer? {
       
   if ($root/*)
   then max($root/*/functx:max-depth(.)) + 1
   else 1
 } ;

(:~
 : The maximum value in a sequence, figuring out its type (numeric or string) 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_max-determine-type.html 
 : @param   $seq the sequence of values to test 
 :) 
declare function functx:max-determine-type 
  ( $seq as xs:anyAtomicType* )  as xs:anyAtomicType? {
       
   if (every $value in $seq satisfies ($value castable as xs:double))
   then max(for $value in $seq return xs:double($value))
   else max(for $value in $seq return xs:string($value))
 } ;

(:~
 : The maximum line length 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_max-line-length.html 
 : @param   $arg the string to test 
 :) 
declare function functx:max-line-length 
  ( $arg as xs:string? )  as xs:integer {
       
   max(
     for $line in functx:lines($arg)
     return string-length($line))
 } ;

(:~
 : The XML node whose typed value is the maximum 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_max-node.html 
 : @param   $nodes the sequence of nodes to test 
 :) 
declare function functx:max-node 
  ( $nodes as node()* )  as node()* {
       
   $nodes[. = max($nodes)]
 } ;

(:~
 : The maximum of a sequence of values, treating them like strings 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_max-string.html 
 : @param   $strings the sequence of values 
 :) 
declare function functx:max-string 
  ( $strings as xs:anyAtomicType* )  as xs:string? {
       
   max(for $string in $strings return string($string))
 } ;

(:~
 : The minimum value in a sequence, figuring out its type (numeric or string) 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_min-determine-type.html 
 : @param   $seq the sequence of values to test 
 :) 
declare function functx:min-determine-type 
  ( $seq as xs:anyAtomicType* )  as xs:anyAtomicType? {
       
   if (every $value in $seq satisfies ($value castable as xs:double))
   then min(for $value in $seq return xs:double($value))
   else min(for $value in $seq return xs:string($value))
 } ;

(:~
 : The XML node whose typed value is the minimum 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_min-node.html 
 : @param   $nodes the sequence of nodes to test 
 :) 
declare function functx:min-node 
  ( $nodes as node()* )  as node()* {
       
   $nodes[. = min($nodes)]
 } ;

(:~
 : The minimum of a sequence of strings, ignoring "empty" values 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_min-non-empty-string.html 
 : @param   $strings the sequence of strings to search 
 :) 
declare function functx:min-non-empty-string 
  ( $strings as xs:string* )  as xs:string? {
       
   min($strings[. != ''])
 } ;

(:~
 : The minimum of a sequence of values, treating them like strings 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_min-string.html 
 : @param   $strings the sequence of strings 
 :) 
declare function functx:min-string 
  ( $strings as xs:anyAtomicType* )  as xs:string? {
       
   min(for $string in $strings return string($string))
 } ;

(:~
 : Converts a string with format MMDDYYYY (with any delimiters) to a date 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_mmddyyyy-to-date.html 
 : @param   $dateString the MMDDYYYY string 
 :) 
declare function functx:mmddyyyy-to-date 
  ( $dateString as xs:string? )  as xs:date? {
       
   if (empty($dateString))
   then ()
   else if (not(matches($dateString,
                        '^\D*(\d{2})\D*(\d{2})\D*(\d{4})\D*$')))
   then error(xs:QName('functx:Invalid_Date_Format'))
   else xs:date(replace($dateString,
                        '^\D*(\d{2})\D*(\d{2})\D*(\d{4})\D*$',
                        '$3-$1-$2'))
 } ;

(:~
 : The month of a date as an abbreviated word (Jan, Feb, etc.) 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_month-abbrev-en.html 
 : @param   $date the date 
 :) 
declare function functx:month-abbrev-en 
  ( $date as xs:anyAtomicType? )  as xs:string? {
       
   ('Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec')
    [month-from-date(xs:date($date))]
 } ;

(:~
 : The month of a date as a word (January, February, etc.) 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_month-name-en.html 
 : @param   $date the date 
 :) 
declare function functx:month-name-en 
  ( $date as xs:anyAtomicType? )  as xs:string? {
       
   ('January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December')
   [month-from-date(xs:date($date))]
 } ;

(:~
 : Whether a name matches a list of names or name wildcards 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_name-test.html 
 : @param   $testname the name to test 
 : @param   $names the list of names or name wildcards 
 :) 
declare function functx:name-test 
  ( $testname as xs:string? ,
    $names as xs:string* )  as xs:boolean {
       
$testname = $names
or
$names = '*'
or
functx:substring-after-if-contains($testname,':') =
   (for $name in $names
   return substring-after($name,'*:'))
or
substring-before($testname,':') =
   (for $name in $names[contains(.,':*')]
   return substring-before($name,':*'))
 } ;

(:~
 : A list of namespaces used in element/attribute names in an XML fragment 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_namespaces-in-use.html 
 : @param   $root the root node to start from 
 :) 
declare function functx:namespaces-in-use 
  ( $root as node()? )  as xs:anyURI* {
       
   distinct-values(
      $root/descendant-or-self::*/(.|@*)/namespace-uri(.))
 } ;

(:~
 : The next day 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_next-day.html 
 : @param   $date the date 
 :) 
declare function functx:next-day 
  ( $date as xs:anyAtomicType? )  as xs:date? {
       
   xs:date($date) + xs:dayTimeDuration('P1D')
 } ;

(:~
 : The XML node kind (element, attribute, text, etc.) 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_node-kind.html 
 : @param   $nodes the node(s) whose kind you want to determine 
 :) 
declare function functx:node-kind 
  ( $nodes as node()* )  as xs:string* {
       
 for $node in $nodes
 return
 if ($node instance of element()) then 'element'
 else if ($node instance of attribute()) then 'attribute'
 else if ($node instance of text()) then 'text'
 else if ($node instance of document-node()) then 'document-node'
 else if ($node instance of comment()) then 'comment'
 else if ($node instance of processing-instruction())
         then 'processing-instruction'
 else 'unknown'
 } ;

(:~
 : Returns any values that appear more than once in a sequence 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_non-distinct-values.html 
 : @param   $seq the sequence of values 
 :) 
declare function functx:non-distinct-values 
  ( $seq as xs:anyAtomicType* )  as xs:anyAtomicType* {
       
   for $val in distinct-values($seq)
   return $val[count($seq[. = $val]) > 1]
 } ;

(:~
 : The number of regions that match a pattern 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_number-of-matches.html 
 : @param   $arg the string to test 
 : @param   $pattern the regular expression 
 :) 
declare function functx:number-of-matches 
  ( $arg as xs:string? ,
    $pattern as xs:string )  as xs:integer {
       
   count(tokenize($arg,$pattern)) - 1
 } ;

(:~
 : Resolves a relative URI and references it, returning an XML document 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_open-ref-document.html 
 : @param   $refNode a node whose value is a relative URI reference 
 :) 
declare function functx:open-ref-document 
  ( $refNode as node() )  as document-node() {
       
   if (base-uri($refNode))
   then doc(resolve-uri($refNode, base-uri($refNode)))
   else doc(resolve-uri($refNode))
 } ;

(:~
 : Reformats a number as an ordinal number, e.g. 1st, 2nd, 3rd. 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_ordinal-number-en.html 
 : @param   $num the number 
 :) 
declare function functx:ordinal-number-en 
  ( $num as xs:integer? )  as xs:string {
       
   concat(xs:string($num),
         if (matches(xs:string($num),'[04-9]$|1[1-3]$')) then 'th'
         else if (ends-with(xs:string($num),'1')) then 'st'
         else if (ends-with(xs:string($num),'2')) then 'nd'
         else if (ends-with(xs:string($num),'3')) then 'rd'
         else '')
 } ;

(:~
 : Pads an integer to a desired length by adding leading zeros 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_pad-integer-to-length.html 
 : @param   $integerToPad the integer to pad 
 : @param   $length the desired length 
 :) 
declare function functx:pad-integer-to-length 
  ( $integerToPad as xs:anyAtomicType? ,
    $length as xs:integer )  as xs:string {
       
   if ($length < string-length(string($integerToPad)))
   then error(xs:QName('functx:Integer_Longer_Than_Length'))
   else concat
         (functx:repeat-string(
            '0',$length - string-length(string($integerToPad))),
          string($integerToPad))
 } ;

(:~
 : Pads a string to a desired length 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_pad-string-to-length.html 
 : @param   $stringToPad the string to pad 
 : @param   $padChar the character(s) to use as padding 
 : @param   $length the desired length 
 :) 
declare function functx:pad-string-to-length 
  ( $stringToPad as xs:string? ,
    $padChar as xs:string ,
    $length as xs:integer )  as xs:string {
       
   substring(
     string-join (
       ($stringToPad, for $i in (1 to $length) return $padChar)
       ,'')
    ,1,$length)
 } ;

(:~
 : A unique path to an XML node (or sequence of nodes) 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_path-to-node-with-pos.html 
 : @param   $node the node sequence 
 :) 
declare function functx:path-to-node-with-pos 
  ( $node as node()? )  as xs:string {
       
string-join(
  for $ancestor in $node/ancestor-or-self::*
  let $sibsOfSameName := $ancestor/../*[name() = name($ancestor)]
  return concat(name($ancestor),
   if (count($sibsOfSameName) <= 1)
   then ''
   else concat(
      '[',functx:index-of-node($sibsOfSameName,$ancestor),']'))
 , '/')
 } ;

(:~
 : A path to an XML node (or sequence of nodes) 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_path-to-node.html 
 : @param   $nodes the node sequence 
 :) 
declare function functx:path-to-node 
  ( $nodes as node()* )  as xs:string* {
       
$nodes/string-join(ancestor-or-self::*/name(.), '/')
 } ;

(:~
 : Whether an XML node precedes another without being its ancestor 
 :
 : @author  W3C XML Query Working Group 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_precedes-not-ancestor.html 
 : @param   $a the first node 
 : @param   $b the second node 
 :) 
declare function functx:precedes-not-ancestor 
  ( $a as node()? ,
    $b as node()? )  as xs:boolean {
       
   $a << $b and empty($a intersect $b/ancestor::node())
 } ;

(:~
 : The previous day 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_previous-day.html 
 : @param   $date the date 
 :) 
declare function functx:previous-day 
  ( $date as xs:anyAtomicType? )  as xs:date? {
       
   xs:date($date) - xs:dayTimeDuration('P1D')
 } ;

(:~
 : Removes attributes from an XML fragment, based on name 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_remove-attributes-deep.html 
 : @param   $nodes the root(s) to start from 
 : @param   $names the names of the attributes to remove, or * for all attributes 
 :) 
declare function functx:remove-attributes-deep 
  ( $nodes as node()* ,
    $names as xs:string* )  as node()* {
       
   for $node in $nodes
   return if ($node instance of element())
          then  element { node-name($node)}
                { $node/@*[not(functx:name-test(name(),$names))],
                  functx:remove-attributes-deep($node/node(), $names)}
          else if ($node instance of document-node())
          then functx:remove-attributes-deep($node/node(), $names)
          else $node
 } ;

(:~
 : Removes attributes from an XML element, based on name 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_remove-attributes.html 
 : @param   $elements the element(s) from which to remove the attributes 
 : @param   $names the names of the attributes to remove, or * for all attributes 
 :) 
declare function functx:remove-attributes 
  ( $elements as element()* ,
    $names as xs:string* )  as element() {
       
   for $element in $elements
   return element
     {node-name($element)}
     {$element/@*[not(functx:name-test(name(),$names))],
      $element/node() }
 } ;

(:~
 : Removes descendant elements from an XML node, based on name 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_remove-elements-deep.html 
 : @param   $nodes root(s) to start from 
 : @param   $names the names of the elements to remove 
 :) 
declare function functx:remove-elements-deep 
  ( $nodes as node()* ,
    $names as xs:string* )  as node()* {
       
   for $node in $nodes
   return
     if ($node instance of element())
     then if (functx:name-test(name($node),$names))
          then ()
          else element { node-name($node)}
                { $node/@*,
                  functx:remove-elements-deep($node/node(), $names)}
     else if ($node instance of document-node())
     then functx:remove-elements-deep($node/node(), $names)
     else $node
 } ;

(:~
 : Removes descendant XML elements but keeps their content 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_remove-elements-not-contents.html 
 : @param   $nodes the root(s) to start from 
 : @param   $names the names of the elements to remove 
 :) 
declare function functx:remove-elements-not-contents 
  ( $nodes as node()* ,
    $names as xs:string* )  as node()* {
       
   for $node in $nodes
   return
    if ($node instance of element())
    then if (functx:name-test(name($node),$names))
         then functx:remove-elements-not-contents($node/node(), $names)
         else element {node-name($node)}
              {$node/@*,
              functx:remove-elements-not-contents($node/node(),$names)}
    else if ($node instance of document-node())
    then functx:remove-elements-not-contents($node/node(), $names)
    else $node
 } ;

(:~
 : Removes child elements from an XML node, based on name 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_remove-elements.html 
 : @param   $elements the element(s) from which you wish to remove the children 
 : @param   $names the names of the child elements to remove 
 :) 
declare function functx:remove-elements 
  ( $elements as element()* ,
    $names as xs:string* )  as element()* {
       
   for $element in $elements
   return element
     {node-name($element)}
     {$element/@*,
      $element/node()[not(functx:name-test(name(),$names))] }
 } ;

(:~
 : Repeats a string a given number of times 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_repeat-string.html 
 : @param   $stringToRepeat the string to repeat 
 : @param   $count the desired number of copies 
 :) 
declare function functx:repeat-string 
  ( $stringToRepeat as xs:string? ,
    $count as xs:integer )  as xs:string {
       
   string-join((for $i in 1 to $count return $stringToRepeat),
                        '')
 } ;

(:~
 : Replaces the beginning of a string, up to a matched pattern 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_replace-beginning.html 
 : @param   $arg the entire string to change 
 : @param   $pattern the pattern of characters to replace up to 
 : @param   $replacement the replacement string 
 :) 
declare function functx:replace-beginning 
  ( $arg as xs:string? ,
    $pattern as xs:string ,
    $replacement as xs:string )  as xs:string {
       
   replace($arg, concat('^.*?', $pattern), $replacement)
 } ;

(:~
 : Updates the content of one or more elements 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_replace-element-values.html 
 : @param   $elements the elements whose content you wish to replace 
 : @param   $values the replacement values 
 :) 
declare function functx:replace-element-values 
  ( $elements as element()* ,
    $values as xs:anyAtomicType* )  as element()* {
       
   for $element at $seq in $elements
   return element { node-name($element)}
             { $element/@*,
               $values[$seq] }
 } ;

(:~
 : Replaces the first match of a pattern 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_replace-first.html 
 : @param   $arg the entire string to change 
 : @param   $pattern the pattern of characters to replace 
 : @param   $replacement the replacement string 
 :) 
declare function functx:replace-first 
  ( $arg as xs:string? ,
    $pattern as xs:string ,
    $replacement as xs:string )  as xs:string {
       
   replace($arg, concat('(^.*?)', $pattern),
             concat('$1',$replacement))
 } ;

(:~
 : Performs multiple replacements, using pairs of replace parameters 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_replace-multi.html 
 : @param   $arg the string to manipulate 
 : @param   $changeFrom the sequence of strings or patterns to change from 
 : @param   $changeTo the sequence of strings to change to 
 :) 
declare function functx:replace-multi 
  ( $arg as xs:string? ,
    $changeFrom as xs:string* ,
    $changeTo as xs:string* )  as xs:string? {
       
   if (count($changeFrom) > 0)
   then functx:replace-multi(
          replace($arg, $changeFrom[1],
                     functx:if-absent($changeTo[1],'')),
          $changeFrom[position() > 1],
          $changeTo[position() > 1])
   else $arg
 } ;

(:~
 : Reverses the order of characters 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_reverse-string.html 
 : @param   $arg the string to reverse 
 :) 
declare function functx:reverse-string 
  ( $arg as xs:string? )  as xs:string {
       
   codepoints-to-string(reverse(string-to-codepoints($arg)))
 } ;

(:~
 : Trims trailing whitespace 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_right-trim.html 
 : @param   $arg the string to trim 
 :) 
declare function functx:right-trim 
  ( $arg as xs:string? )  as xs:string {
       
   replace($arg,'\s+$','')
 } ;

(:~
 : Returns the scheme from a URI 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_scheme-from-uri.html 
 : @param   $uri the URI 
 :) 
declare function functx:scheme-from-uri 
  ( $uri as xs:string? )  as xs:string? {
       
   substring-before($uri,':')
 } ;

(:~
 : Whether two sequences have the same XML node content and/or values 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_sequence-deep-equal.html 
 : @param   $seq1 the first sequence 
 : @param   $seq2 the second sequence 
 :) 
declare function functx:sequence-deep-equal 
  ( $seq1 as item()* ,
    $seq2 as item()* )  as xs:boolean {
       
  every $i in 1 to max((count($seq1),count($seq2)))
  satisfies deep-equal($seq1[$i],$seq2[$i])
 } ;

(:~
 : Whether two sequences contain the same XML nodes, regardless of order 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_sequence-node-equal-any-order.html 
 : @param   $seq1 the first sequence of nodes 
 : @param   $seq2 the second sequence of nodes 
 :) 
declare function functx:sequence-node-equal-any-order 
  ( $seq1 as node()* ,
    $seq2 as node()* )  as xs:boolean {
       
  not( ($seq1 except $seq2, $seq2 except $seq1))
 } ;

(:~
 : Whether two sequences contain the same XML nodes, in the same order 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_sequence-node-equal.html 
 : @param   $seq1 the first sequence of nodes 
 : @param   $seq2 the second sequence of nodes 
 :) 
declare function functx:sequence-node-equal 
  ( $seq1 as node()* ,
    $seq2 as node()* )  as xs:boolean {
       
  every $i in 1 to max((count($seq1),count($seq2)))
  satisfies $seq1[$i] is $seq2[$i]
 } ;

(:~
 : The sequence type that represents a sequence of nodes or values 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_sequence-type.html 
 : @param   $items the items whose sequence type you want to determine 
 :) 
declare function functx:sequence-type 
  ( $items as item()* )  as xs:string {
       
concat(
  if (empty($items))
  then 'empty-sequence()'
  else if (every $val in $items
           satisfies $val instance of xs:anyAtomicType)
  then if (count(distinct-values(functx:atomic-type($items)))
           > 1)
  then 'xs:anyAtomicType'
  else functx:atomic-type($items[1])
  else if (some $val in $items
           satisfies $val instance of xs:anyAtomicType)
  then 'item()'
  else if (count(distinct-values(functx:node-kind($items))) > 1)
  then 'node()'
  else concat(functx:node-kind($items[1]),'()')
  ,
  if (count($items) > 1)
  then '+' else '')
   } ;

(:~
 : The siblings of an XML element that have the same name 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_siblings-same-name.html 
 : @param   $element the node 
 :) 
declare function functx:siblings-same-name 
  ( $element as element()? )  as element()* {
       
   $element/../*[node-name(.) = node-name($element)]
   except $element
 } ;

(:~
 : The siblings of an XML node 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_siblings.html 
 : @param   $node the node 
 :) 
declare function functx:siblings 
  ( $node as node()? )  as node()* {
       
   $node/../node() except $node
 } ;

(:~
 : Sorts a sequence of numeric values or nodes 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_sort-as-numeric.html 
 : @param   $seq the sequence to sort 
 :) 
declare function functx:sort-as-numeric 
  ( $seq as item()* )  as item()* {
       
   for $item in $seq
   order by number($item)
   return $item
 } ;

(:~
 : Sorts a sequence of values or nodes regardless of capitalization 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_sort-case-insensitive.html 
 : @param   $seq the sequence to sort 
 :) 
declare function functx:sort-case-insensitive 
  ( $seq as item()* )  as item()* {
       
   for $item in $seq
   order by upper-case(string($item))
   return $item
 } ;

(:~
 : Sorts a sequence of nodes in document order 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_sort-document-order.html 
 : @param   $seq the sequence to sort 
 :) 
declare function functx:sort-document-order 
  ( $seq as node()* )  as node()* {
       
   $seq/.
 } ;

(:~
 : Sorts a sequence of values or nodes 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_sort.html 
 : @param   $seq the sequence to sort 
 :) 
declare function functx:sort 
  ( $seq as item()* )  as item()* {
       
   for $item in $seq
   order by $item
   return $item
 } ;

(:~
 : Performs substring-after, returning the entire string if it does not contain the delimiter 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_substring-after-if-contains.html 
 : @param   $arg the string to substring 
 : @param   $delim the delimiter 
 :) 
declare function functx:substring-after-if-contains 
  ( $arg as xs:string? ,
    $delim as xs:string )  as xs:string? {
       
   if (contains($arg,$delim))
   then substring-after($arg,$delim)
   else $arg
 } ;

(:~
 : The substring after the last text that matches a regex 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_substring-after-last-match.html 
 : @param   $arg the string to substring 
 : @param   $regex the regular expression 
 :) 
declare function functx:substring-after-last-match 
  ( $arg as xs:string? ,
    $regex as xs:string )  as xs:string {
       
   replace($arg,concat('^.*',$regex),'')
 } ;

(:~
 : The substring after the last occurrence of a delimiter 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_substring-after-last.html 
 : @param   $arg the string to substring 
 : @param   $delim the delimiter 
 :) 
declare function functx:substring-after-last 
  ( $arg as xs:string? ,
    $delim as xs:string )  as xs:string {
       
   replace ($arg,concat('^.*',functx:escape-for-regex($delim)),'')
 } ;

(:~
 : The substring after the first text that matches a regex 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_substring-after-match.html 
 : @param   $arg the string to substring 
 : @param   $regex the regular expression 
 :) 
declare function functx:substring-after-match 
  ( $arg as xs:string? ,
    $regex as xs:string )  as xs:string? {
       
   replace($arg,concat('^.*?',$regex),'')
 } ;

(:~
 : Performs substring-before, returning the entire string if it does not contain the delimiter 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_substring-before-if-contains.html 
 : @param   $arg the string to substring 
 : @param   $delim the delimiter 
 :) 
declare function functx:substring-before-if-contains 
  ( $arg as xs:string? ,
    $delim as xs:string )  as xs:string? {
       
   if (contains($arg,$delim))
   then substring-before($arg,$delim)
   else $arg
 } ;

(:~
 : The substring after the first text that matches a regex 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_substring-before-last-match.html 
 : @param   $arg the string to substring 
 : @param   $regex the regular expression 
 :) 
declare function functx:substring-before-last-match 
  ( $arg as xs:string? ,
    $regex as xs:string )  as xs:string? {
       
   replace($arg,concat('^(.*)',$regex,'.*'),'$1')
 } ;

(:~
 : The substring before the last occurrence of a delimiter 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_substring-before-last.html 
 : @param   $arg the string to substring 
 : @param   $delim the delimiter 
 :) 
declare function functx:substring-before-last 
  ( $arg as xs:string? ,
    $delim as xs:string )  as xs:string {
       
   if (matches($arg, functx:escape-for-regex($delim)))
   then replace($arg,
            concat('^(.*)', functx:escape-for-regex($delim),'.*'),
            '$1')
   else ''
 } ;

(:~
 : The substring before the last text that matches a regex 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_substring-before-match.html 
 : @param   $arg the string to substring 
 : @param   $regex the regular expression 
 :) 
declare function functx:substring-before-match 
  ( $arg as xs:string? ,
    $regex as xs:string )  as xs:string {
       
   tokenize($arg,$regex)[1]
 } ;

(:~
 : Construct a time from an hour, minute and second 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_time.html 
 : @param   $hour the hour 
 : @param   $minute the minute 
 : @param   $second the second 
 :) 
declare function functx:time 
  ( $hour as xs:anyAtomicType ,
    $minute as xs:anyAtomicType ,
    $second as xs:anyAtomicType )  as xs:time {
       
   xs:time(
     concat(
       functx:pad-integer-to-length(xs:integer($hour),2),':',
       functx:pad-integer-to-length(xs:integer($minute),2),':',
       functx:pad-integer-to-length(xs:integer($second),2)))
 } ;

(:~
 : Converts an xs:dayTimeDuration into a timezone like "-05:00" or "Z" 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_timezone-from-duration.html 
 : @param   $duration the duration 
 :) 
declare function functx:timezone-from-duration 
  ( $duration as xs:dayTimeDuration )  as xs:string {
       
   if (string($duration) = ('PT0S','-PT0S'))
   then 'Z'
   else if (matches(string($duration),'-PT[1-9]H'))
   then replace(string($duration),'PT([1-9])H','0$1:00')
   else if (matches(string($duration),'PT[1-9]H'))
   then replace(string($duration),'PT([1-9])H','+0$1:00')
   else if (matches(string($duration),'-PT1[0-4]H'))
   then replace(string($duration),'PT(1[0-4])H','$1:00')
   else if (matches(string($duration),'PT1[0-4]H'))
   then replace(string($duration),'PT(1[0-4])H','+$1:00')
   else error(xs:QName('functx:Invalid_Duration_Value'))
 } ;

(:~
 : The total number of days in a dayTimeDuration 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_total-days-from-duration.html 
 : @param   $duration the duration 
 :) 
declare function functx:total-days-from-duration 
  ( $duration as xs:dayTimeDuration? )  as xs:decimal? {
       
   $duration div xs:dayTimeDuration('P1D')
 } ;

(:~
 : The total number of hours in a dayTimeDuration 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_total-hours-from-duration.html 
 : @param   $duration the duration 
 :) 
declare function functx:total-hours-from-duration 
  ( $duration as xs:dayTimeDuration? )  as xs:decimal? {
       
   $duration div xs:dayTimeDuration('PT1H')
 } ;

(:~
 : The total number of minutes in a dayTimeDuration 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_total-minutes-from-duration.html 
 : @param   $duration the duration 
 :) 
declare function functx:total-minutes-from-duration 
  ( $duration as xs:dayTimeDuration? )  as xs:decimal? {
       
   $duration div xs:dayTimeDuration('PT1M')
 } ;

(:~
 : The total number of months in a yearMonthDuration 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_total-months-from-duration.html 
 : @param   $duration the duration 
 :) 
declare function functx:total-months-from-duration 
  ( $duration as xs:yearMonthDuration? )  as xs:decimal? {
       
   $duration div xs:yearMonthDuration('P1M')
 } ;

(:~
 : The total number of seconds in a dayTimeDuration 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_total-seconds-from-duration.html 
 : @param   $duration the duration 
 :) 
declare function functx:total-seconds-from-duration 
  ( $duration as xs:dayTimeDuration? )  as xs:decimal? {
       
   $duration div xs:dayTimeDuration('PT1S')
 } ;

(:~
 : The total number of years in a yearMonthDuration 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_total-years-from-duration.html 
 : @param   $duration the duration 
 :) 
declare function functx:total-years-from-duration 
  ( $duration as xs:yearMonthDuration? )  as xs:decimal? {
       
   $duration div xs:yearMonthDuration('P1Y')
 } ;

(:~
 : Trims leading and trailing whitespace 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_trim.html 
 : @param   $arg the string to trim 
 :) 
declare function functx:trim 
  ( $arg as xs:string? )  as xs:string {
       
   replace(replace($arg,'\s+$',''),'^\s+','')
 } ;

(:~
 : Updates the attribute value of an XML element 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_update-attributes.html 
 : @param   $elements the element(s) for which you wish to update the attribute 
 : @param   $attrNames the name(s) of the attribute(s) to add 
 : @param   $attrValues the value(s) of the attribute(s) to add 
 :) 
declare function functx:update-attributes 
  ( $elements as element()* ,
    $attrNames as xs:QName* ,
    $attrValues as xs:anyAtomicType* )  as element()? {
       
   for $element in $elements
   return element { node-name($element)}
                  { for $attrName at $seq in $attrNames
                    return if ($element/@*[node-name(.) = $attrName])
                           then attribute {$attrName}
                                     {$attrValues[$seq]}
                           else (),
                    $element/@*[not(node-name(.) = $attrNames)],
                    $element/node() }
 } ;

(:~
 : The values in one sequence that aren't in another sequence 
 :
 : @author  W3C XML Query Working Group 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_value-except.html 
 : @param   $arg1 the first sequence 
 : @param   $arg2 the second sequence 
 :) 
declare function functx:value-except 
  ( $arg1 as xs:anyAtomicType* ,
    $arg2 as xs:anyAtomicType* )  as xs:anyAtomicType* {
       
  distinct-values($arg1[not(.=$arg2)])
 } ;

(:~
 : The intersection of two sequences of values 
 :
 : @author  W3C XML Query Working Group 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_value-intersect.html 
 : @param   $arg1 the first sequence 
 : @param   $arg2 the second sequence 
 :) 
declare function functx:value-intersect 
  ( $arg1 as xs:anyAtomicType* ,
    $arg2 as xs:anyAtomicType* )  as xs:anyAtomicType* {
       
  distinct-values($arg1[.=$arg2])
 } ;

(:~
 : The union of two sequences of values 
 :
 : @author  W3C XML Query Working Group 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_value-union.html 
 : @param   $arg1 the first sequence 
 : @param   $arg2 the second sequence 
 :) 
declare function functx:value-union 
  ( $arg1 as xs:anyAtomicType* ,
    $arg2 as xs:anyAtomicType* )  as xs:anyAtomicType* {
       
  distinct-values(($arg1, $arg2))
 } ;

(:~
 : The number of words 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_word-count.html 
 : @param   $arg the string to measure 
 :) 
declare function functx:word-count 
  ( $arg as xs:string? )  as xs:integer {
       
   count(tokenize($arg, '\W+')[. != ''])
 } ;

(:~
 : Turns a string of words into camelCase 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_words-to-camel-case.html 
 : @param   $arg the string to modify 
 :) 
declare function functx:words-to-camel-case 
  ( $arg as xs:string? )  as xs:string {
       
     string-join((tokenize($arg,'\s+')[1],
       for $word in tokenize($arg,'\s+')[position() > 1]
       return functx:capitalize-first($word))
      ,'')
 } ;

(:~
 : Wraps a sequence of atomic values in XML elements 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_wrap-values-in-elements.html 
 : @param   $values the values to wrap in elements 
 : @param   $elementName the name of the elements to construct 
 :) 
declare function functx:wrap-values-in-elements 
  ( $values as xs:anyAtomicType* ,
    $elementName as xs:QName )  as element()* {
       
   for $value in $values
   return element {$elementName} {$value}
 } ;

(:~
 : Construct a yearMonthDuration from a number of years and months 
 :
 : @author  Priscilla Walmsley, Datypic 
 : @version 1.0 
 : @see     http://www.xqueryfunctions.com/xq/functx_yearmonthduration.html 
 : @param   $years the number of years 
 : @param   $months the number of months 
 :) 
declare function functx:yearMonthDuration 
  ( $years as xs:decimal? ,
    $months as xs:integer? )  as xs:yearMonthDuration {
       
    (xs:yearMonthDuration('P1M') * functx:if-empty($months,0)) +
    (xs:yearMonthDuration('P1Y') * functx:if-empty($years,0))
 } ;
