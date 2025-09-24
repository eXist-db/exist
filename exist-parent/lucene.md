## v5

- `IndexFormatTooOldException`
- [blog post](https://blog.mikemccandless.com/2014/11/apache-lucene-500-is-coming.html)

## v6
- 'NumericField' deprecation

## v7
- `IndexWriter` better concurrency
- Classic `QueryParser` no longer splits on whitespace by default. Use `setSplitOnWhitespace(true)` to get the old behavior.

## v8
- `StandardFilter` and `StandardFilterFactory` have been removed
- [migrate.md](https://github.com/apache/lucene/blob/history/branches/lucene-solr/origin/branch_8_x/lucene/MIGRATE.md)

## v9
- Lucene 9 no longer has split packages. This required renaming some packages outside of the lucene-core JAR, so you will need to adjust some imports accordingly.

|         Old Artifact Coordinates            |        New Artifact Coordinates            |
|---------------------------------------------|--------------------------------------------|
|org.apache.lucene:lucene-analyzers-common    |org.apache.lucene:lucene-analysis-common    |
|org.apache.lucene:lucene-analyzers-icu       |org.apache.lucene:lucene-analysis-icu       |
|org.apache.lucene:lucene-analyzers-kuromoji  |org.apache.lucene:lucene-analysis-kuromoji  |
|org.apache.lucene:lucene-analyzers-morfologik|org.apache.lucene:lucene-analysis-morfologik|
|org.apache.lucene:lucene-analyzers-nori      |org.apache.lucene:lucene-analysis-nori      |
|org.apache.lucene:lucene-analyzers-opennlp   |org.apache.lucene:lucene-analysis-opennlp   |
|org.apache.lucene:lucene-analyzers-phonetic  |org.apache.lucene:lucene-analysis-phonetic  |
|org.apache.lucene:lucene-analyzers-smartcn   |org.apache.lucene:lucene-analysis-smartcn   |
|org.apache.lucene:lucene-analyzers-stempel   |org.apache.lucene:lucene-analysis-stempel   |

- All binary analysis packages (and corresponding Maven artifacts) with names containing '-analyzers-' have been renamed to '-analysis-
- Change "name" argument in ICU factories to "form". Here, "form" is named after "Unicode Normalization Form".
- Use `java.util.ServiceLoader` to load codec components and analysis factories to be compatible with Java Module System. This allows to load factories without META-INF/service from a Java module exposing the factory in the module descriptor. This breaks backwards compatibility as custom analysis factories must now also implement the default constructor (see MIGRATE.md).
(Uwe Schindler, Dawid Weiss)
- `TokenStreamComponents` is now final
Instead of overriding TokenStreamComponents#setReader() to customise analyzer
initialisation, you should now pass a Consumer&lt;Reader> instance to the
TokenStreamComponents constructor.
- `maxClausesCount` moved from BooleanQuery To IndexSearcher (LUCENE-8811)
IndexSearcher now performs max clause count checks on all types of queries (including BooleanQueries).
This led to a logical move of the clauses count from BooleanQuery to IndexSearcher.

## v10
- [migrate.md](https://github.com/apache/lucene/blob/branch_10_0/lucene/MIGRATE.md)
- there's no "German2" anymore. For Lucene APIs (TokenFilter, TokenFilterFactory) that accept String, "German2" will be mapped to "German" to avoid breaking users.
- [`IndexSearch#search(Query, Collector)` being deprecated in favor of `IndexSearcher#search(Query, CollectorManager)` ](https://github.com/apache/lucene/blob/branch_10_0/lucene/MIGRATE.md#indexsearchsearchquery-collector-being-deprecated-in-favor-of-indexsearchersearchquery-collectormanager-lucene-10002)
- IntField(String name, int value). Use IntField(String, int, Field.Store) with Field.Store#NO instead.
- DoubleField(String name, double value). Use DoubleField(String, double, Field.Store) with Field.Store#NO instead.
- FloatField(String name, float value). Use FloatField(String, float, Field.Store) with Field.Store#NO instead.
- LongField(String name, long value). Use LongField(String, long, Field.Store) with Field.Store#NO instead.
- BooleanQuery#TooManyClauses, BooleanQuery#getMaxClauseCount(), BooleanQuery#setMaxClauseCount(). Use IndexSearcher#TooManyClauses, IndexSearcher#getMaxClauseCount(), IndexSearcher#setMaxClauseCount() instead
