declare namespace my="my.uri";
declare function my:section-summary($book-or-section as element())
  as element()*
{
  for $section in $book-or-section/section
  return
    <section>
       { $section/@* }
       { $section/title }   
       <figcount>     
          { count($section/figure) }
       </figcount>        
       { my:section-summary($section) }            
    </section>
};

<toc>
  {
    for $s in doc("book.xml")/book
    return my:section-summary($s)
  }
</toc>