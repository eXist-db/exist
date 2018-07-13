module namespace ext1 = "http://import-external-classpath-library-module-test.com";

declare function ext1:echo($s) as element(echo) {
    <echo>{$s}</echo>
};
