xquery version "1.0";

module namespace test = "http://xproc.net/test";

declare namespace saxon="http://saxon.sf.net/";


(: ---------------------------------------------------------------------------- :)
                                                                    (: XML Tests:)
(: ---------------------------------------------------------------------------- :)

declare function test:assertXMLEqual($a as item()*, $b as item()*) as xs:boolean {
    deep-equal($a,$b)
};

declare function test:assertXMLNotEqual($a as item()*, $b as item()*) as xs:boolean {
    not(deep-equal($a,$b))
};

(: ---------------------------------------------------------------------------- :)
                                                                 (: String Tests:)
(: ---------------------------------------------------------------------------- :)

declare function test:assertStringEqual($a as xs:string, $b as xs:string) as xs:boolean {  
 not(boolean(compare($a, $b)))
};

declare function test:assertStringNotEqual($a as xs:string, $b as xs:string) as xs:boolean {  
 boolean(compare($a, $b))
};

declare function test:assertStringContain($a as xs:string, $b as xs:string) as xs:boolean {
    contains($a, $b)
};

declare function test:assertStringNotContain($a as xs:string, $b as xs:string) as xs:boolean {
    not(contains($a, $b))
};

(: ---------------------------------------------------------------------------- :)
                                                                (: Integer Tests:)
(: ---------------------------------------------------------------------------- :)

declare function test:assertIntegerEqual($a as xs:integer, $b as xs:integer) as xs:boolean {  
  boolean($a=$b) 
};

declare function test:assertIsInteger($a as xs:integer) as xs:boolean {  
 true() (: NOTE THIS IS A PROBLEM:)
};


(: ---------------------------------------------------------------------------- :)
                                                                  (: XPATH Tests:)
(: ---------------------------------------------------------------------------- :)

declare function test:evalXPATH($xpathstring, $xml){
    $xml/saxon:evaluate($xpathstring)
};

declare function test:assertXpathExist($xml as item(), $xpathstring as xs:string) as xs:boolean {
    exists(test:evalXPATH($xpathstring,$xml))
};

declare function test:assertXpathNotExist($xml as item(), $xpathstring as xs:string) as xs:boolean {
    not(exists(test:evalXPATH($xpathstring,$xml)))
};

declare function test:assertXpathEqual($a as element(), $b as xs:string, $c as xs:string ) as xs:boolean {
true()
};

declare function test:assertXpathNotEqual($a as element(), $b as xs:string, $c as xs:string ) as xs:boolean {
true()
};

(: ---------------------------------------------------------------------------- :)

