# Sql Usage

## Physical tables and columns each SQL statement touches, and who issues it

Every SQL statement found in code and resources, with the physical tables and columns it touches and the class and method that issues it. One row per statement per table, so filtering on a table name answers which code reads or writes it, and filtering on a class or method answers what data that code reaches. Covers SQL in string literals, `.sql` files, MyBatis mapper XML, YAML and JSON resources, and statements assembled by concatenation or string interpolation, the last of which is reported as dynamic because only its static part is known. Source path and line number join `sql-anti-patterns.csv`, and class name and method signature join `method-quality-metrics.csv` and `test-gaps.csv`. Names are the ones the statement itself uses, so a table written as both `orders` and `Orders` appears twice, and JPQL that parses as SQL contributes entity names rather than physical tables.

## Data Tables

### SQL usage

**File:** [`sql-usage.csv`](sql-usage.csv)

Physical tables and columns each SQL statement touches, attributed to the class and method that issues it.

| Column | Description |
|--------|-------------|
| Source path | The path to the source file containing the SQL. |
| Line number | The line the SQL statement begins on. Together with the source path this joins a row in `sql-anti-patterns.csv` to the method that issues the query. |
| Class name | The fully qualified name of the class issuing the statement, or of the MyBatis mapper interface a mapper XML declares as its `namespace`. Empty for a `.sql` file or any other resource that names no class. |
| Method name | The simple name of the method issuing the statement, or the `id` of the MyBatis statement, which is the mapper interface method it implements. Empty for SQL outside any method. |
| Method signature | The full method signature including parameter types, joining `method-quality-metrics.csv` and `test-gaps.csv`. Empty where the SQL is not inside a method, including in mapper XML, where the method is named but its parameter types are not known. |
| Language | The language of the file the statement is written in, e.g. `java`, `kotlin`, `csharp`, `python`, `xml`, or `sql` for a plain `.sql` file. |
| Embedded in | How the statement is written: `literal` for a single string literal, `concatenation` for one assembled with `+` or `StringBuilder.append`, `interpolation` for an interpolated or template string, or `file`, `xml`, `yaml` or `json` for a statement carried by a resource. |
| Table | The physical table this row reports on. A statement touching several tables contributes one row per table. |
| Operations | The operations the statement performs on this table, comma-separated in `SELECT,UPDATE,INSERT,DELETE,CREATE,ALTER` order. A write that also reads the table, as an `UPDATE ... WHERE` does, reports both. |
| Columns | The columns of this table the statement names, comma-separated, or `*` where it selects them all. Empty where no column can be attributed to the table with certainty, which a `DELETE` or an ambiguous join produces. |
| Dynamic | Whether the statement is assembled at runtime, from an interpolated string, from a concatenation with a non-constant operand, or from a mapper XML statement with conditional elements nested in it, in which case the query below is only the static part of what actually runs. |
| Query | The SQL statement, truncated to 200 characters. Read the source at the path and line above for the whole of a longer statement. |

