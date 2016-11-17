/**
 * @author IWEERARATHNA
 */
def innQ = $DSL.select {
    TARGET (Film.alias("f"))
    WHERE {
        EQ (f.film_id, NUM(1))
    }
}

def innQP = $DSL.select {
    TARGET (Film.alias("f"))
    WHERE {
        EQ (f.film_id, PARAM("id"))
    }
}

[
        $DSL.insert {
            TARGET (Film.alias("f"))
            DATA (
                    "film_id": PARAM("id"),
                    "title": PARAM("title")
            )
        },
        ["INSERT INTO `Film` (`film_id`, `title`) VALUES (?, ?)",
         ["id", "title"]],

        $DSL.insert {
            TARGET (Film.alias("f"))
            DATA (
                    "film_id": PARAM("id"),
                    "title": PARAM("title")
            )
            RETURN_KEYS()
        },
        ["INSERT INTO `Film` (`film_id`, `title`) VALUES (?, ?)", ["id", "title"]],

        $DSL.bulkInsert {
            TARGET (Film.alias("f"))
            DATA (
                    "film_id": PARAM("id"),
                    "title": PARAM("title"),
                    "createdAt": CUREPOCH()
            )
        },
        ["INSERT INTO `Film` (`film_id`, `title`, `createdAt`) VALUES (?, ?, UNIX_TIMESTAMP() * 1000)",
         ["id", "title"]],

        $DSL.insert {
            TARGET (Film.alias("f"))
            DATA (
                    "film_id": TABLE(innQ),
                    "title": PARAM("title")
            )
            RETURN_KEYS()
        },
        ["INSERT INTO `Film` (`film_id`, `title`) VALUES ((SELECT * FROM `Film` f WHERE f.film_id = 1), ?)", ["title"]],

        $DSL.insert {
            TARGET (Film.alias("f"))
            DATA (
                    "film_id": TABLE(innQP),
                    "title": PARAM("title")
            )
        },
        ["INSERT INTO `Film` (`film_id`, `title`) VALUES ((SELECT * FROM `Film` f WHERE f.film_id = ?), ?)", ["id", "title"]],

        $DSL.insert {
            TARGET (Film.alias("f"))
            DATA (
                    "film_id": TABLE(innQP),
                    "title": PARAM("title")
            )
            DATA ($IMPORT("inserts/data_imports"))
        },
        ["INSERT INTO `Film` (`film_id`, `title`, `importedCol1`, `importedCol2`, `importedCol3`) VALUES " +
                 "((SELECT * FROM `Film` f WHERE f.film_id = ?), ?, ?, \"const\", ?)",
         ["id", "title", "iparam1", "iparam2"]],

        $DSL.insert {
            TARGET (Film.alias("f"))
            DATA (["film_id": TABLE(innQP), "title": PARAM("title")], $IMPORT("inserts/data_imports"))
        },
        ["INSERT INTO `Film` (`film_id`, `title`, `importedCol1`, `importedCol2`, `importedCol3`) VALUES " +
                 "((SELECT * FROM `Film` f WHERE f.film_id = ?), ?, ?, \"const\", ?)",
         ["id", "title", "iparam1", "iparam2"]],

        $DSL.insert {
            TARGET (Film.alias("f"))
            DATA (
                    "film_id": PARAM("id"),
                    "releaseDate": PARAM_DATE("relDate")
            )
        },
        ["INSERT INTO `Film` (`film_id`, `releaseDate`) VALUES (?, ?)",
         ["id", "relDate"]],

        $DSL.insert {
            TARGET (Film.alias("f"))
            DATA (
                    "film_id": PARAM("id"),
                    "debutScreenTime": PARAM_TIMESTAMP("debutTime")
            )
        },
        ["INSERT INTO `Film` (`film_id`, `debutScreenTime`) VALUES (?, ?)",
         ["id", "debutTime"]],

        $DSL.insert {
            TARGET (Film.alias("f"))
            DATA (
                    "film_id": PARAM("id"),
                    "debutScreenTime": PARAM_TIMESTAMP("debutTime"),
                    "debutScreenTime2": PARAM_TIMESTAMP("debutTime2", "YYYY-MM-DDThh:mm:ssTZD"),
                    "releaseDate": PARAM_DATE("relDate")
            )
        },
        ["INSERT INTO `Film` (`film_id`, `debutScreenTime`, `debutScreenTime2`, `releaseDate`) VALUES (?, ?, ?, ?)",
         ["id", "debutTime", "debutTime2", "relDate"]],
]