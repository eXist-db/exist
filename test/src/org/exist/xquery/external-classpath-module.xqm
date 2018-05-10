module namespace ext1 = "http://import-external-classpath-test.com";

declare function ext1:echo($s) as element(echo) {
    <echo>{$s}</echo>
};
