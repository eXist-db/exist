declare namespace atom="http://www.w3.org/2005/Atom";
declare variable $id external;
collection(string-join((substring-before(base-uri(/atom:feed),'/.feed.atom'),".feed.entry"),"/"))/atom:entry[atom:id=$id]