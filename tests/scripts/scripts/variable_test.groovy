/**
 * @author IWEERARATHNA
 */

$DSL.script {

    $SESSION.manProperty = 1

    def innQ = $DSL.select {
        TARGET (Film.alias("f"))

        FETCH (f.film_id, $SESSION.manProperty)

    }

    RUN (innQ)

}