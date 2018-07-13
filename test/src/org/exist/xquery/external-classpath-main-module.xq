xquery version "1.0";

declare variable $s external;

document { <echo>{$s}</echo> }
