/**
 * @author IWEERARATHNA
 */
[
        $DSL.delete {
            TARGET (Film.alias("f"))
            WHERE {
                EQ (f.film_id, 1234)
            }
        },
        "DELETE FROM `Film` WHERE `Film`.film_id = 1234"

]