declare variable $words {
	("wußte", "wüßte", "Apfel", "Äpfel", "Buch", "Bücher", "Jagen", "Jäger", "Bauer", "Bäuerin", "cóte", "côte")
};

<words>
{
	for $w in $words
	order by $w collation "codepoint"
	return
		<word>{$w}</word>
}
</words>
