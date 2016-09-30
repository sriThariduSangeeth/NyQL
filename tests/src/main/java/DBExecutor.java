import com.virtusa.gto.insight.nyql.engine.NyQL;
import com.virtusa.gto.insight.nyql.model.QScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author IWEERARATHNA
 */
public class DBExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DBExecutor.class);

    public static void main(String[] args) throws Exception {
        try {
            Map<String, Object> data = new HashMap<>();
            List<Integer> teams = asList(1410, 1411);
            List<Integer> modules = asList(97389, 97390, 97391);

            Map<String, Object> inners = new HashMap<>();
            inners.put("abc", "Dsadsads");

            data.put("teamIDs", teams);
            data.put("moduleIDs", modules);
            data.put("filmId", 250);
            data.put("start", 100);
            data.put("end", 200);

            //data.put("hello", inners);

            //QScript result = NyQL.parse("insight/unmapped_users", data);
            Object result = NyQL.execute("trans", data);
            System.out.println(result);

        } finally {
            NyQL.shutdown();
        }
        //NyQL.execute("")
        //Quickly.configOnce();
        //parse();
    }

    private static void parse() throws Exception {
        Map<String, Object> data = new HashMap<>();
        List<Integer> teams = asList(1410, 1411);
        List<Integer> modules = asList(97389, 97390, 97391);

        data.put("teamIDs", teams);
        data.put("moduleIDs", modules);
        data.put("filmId", 250);

        QScript result = NyQL.parse("select", data);
        System.out.println(result);
    }

    private static void execute() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("minRentals", 25);
        data.put("customerId", 2);
        data.put("filmId", 250);

        Object result = NyQL.execute("top_customers", data);
        if (result instanceof List) {
            for (Object row : (List)result) {
                LOGGER.debug(row.toString());
            }
        }
    }

    @SafeVarargs
    private static <T> List<T> asList(T... items) {
        List<T> list = new LinkedList<T>();
        Collections.addAll(list, items);
        return list;
    }

}
