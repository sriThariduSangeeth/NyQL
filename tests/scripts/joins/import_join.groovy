/**
 * @author IWEERARATHNA
 */
$DSL.$q {

    EXPECT (Film.alias("f"))

    JOIN (f) {
        INNER_JOIN (Actor.alias("ac")) ON f.actor_id, ac.actor_id
    }

}
