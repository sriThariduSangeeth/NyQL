package nyql.parsing;

import com.virtusa.gto.nyql.exceptions.NyException;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author IWEERARATHNA
 */
@Test(groups = {"parsing"})
public class WhereTest extends AbstractTest {

    public void testBasic() throws NyException {
        Map<String, Object> data = new HashMap<>();
        data.put("emptyList", new ArrayList<>());
        data.put("singleList", Collections.singletonList(1));
        data.put("doubleList", Arrays.asList(1, 2));

        assertQueries(nyql().parse("where/basic_where", data));
    }

    public void testImport() throws NyException {
//        Map<String, Object> data = new HashMap<>();
//        data.put("emptyList", new ArrayList<>());
//        data.put("singleList", Arrays.asList(1));
//        data.put("doubleList", Arrays.asList(1, 2));

        assertQueries(nyql().parse("where/where_import"));
    }
}
