declare namespace my="my.uri";
declare function my:toc($book-or-section as element()) as element()*
{
    for $section in $book-or-section/section
    return
        <section>
                { $section/@* , $section/title , my:toc($section) }
        </section>
};

<toc>
   {
     for $s in doc("book.xml")/book return my:toc($s)
   }
</toc>