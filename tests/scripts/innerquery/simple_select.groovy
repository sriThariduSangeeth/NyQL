/**
 * @author IWEERARATHNA
 */
$DSL.select {
    TARGET (Film.alias("f"))
    FETCH (f.id, f.year)
}