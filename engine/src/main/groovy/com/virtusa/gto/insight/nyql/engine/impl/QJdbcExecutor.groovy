package com.virtusa.gto.insight.nyql.engine.impl

import com.virtusa.gto.insight.nyql.model.blocks.AParam
import com.virtusa.gto.insight.nyql.StoredFunction
import com.virtusa.gto.insight.nyql.engine.exceptions.NyScriptExecutionException
import com.virtusa.gto.insight.nyql.engine.impl.pool.QJdbcPoolFetcher
import com.virtusa.gto.insight.nyql.exceptions.NyException
import com.virtusa.gto.insight.nyql.model.QScriptResult
import com.virtusa.gto.insight.nyql.model.blocks.NamedParam
import com.virtusa.gto.insight.nyql.model.blocks.ParamList
import com.virtusa.gto.insight.nyql.utils.QReturnType
import com.virtusa.gto.insight.nyql.utils.QUtils
import com.virtusa.gto.insight.nyql.utils.QueryType
import com.virtusa.gto.insight.nyql.model.QExecutor
import com.virtusa.gto.insight.nyql.model.QScript
import com.virtusa.gto.insight.nyql.engine.transform.JdbcCallResultTransformer
import com.virtusa.gto.insight.nyql.engine.transform.JdbcCallTransformInput
import com.virtusa.gto.insight.nyql.engine.transform.JdbcResultTransformer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.CallableStatement
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Savepoint
import java.sql.Statement
import java.util.stream.Collectors

/**
 * @author IWEERARATHNA
 */
class QJdbcExecutor implements QExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(QJdbcExecutor.class)

    private static final JdbcResultTransformer transformer = new JdbcResultTransformer()
    private static final JdbcCallResultTransformer callResultTransformer = new JdbcCallResultTransformer()

    private QJdbcPoolFetcher poolFetcher
    private Connection connection
    private boolean returnRaw = false
    private boolean reusable = false

    /**
     * Creates an executor with custom connection.
     * In here we won't close the connection at the end of execution.
     *
     * @param yourConnection sql connection
     */
    QJdbcExecutor(Connection yourConnection) {
        connection = yourConnection
        reusable = true
    }

    QJdbcExecutor(QJdbcPoolFetcher jdbcPoolFetcher) {
        this(jdbcPoolFetcher, false)
    }

    QJdbcExecutor(QJdbcPoolFetcher jdbcPoolFetcher, boolean canReusable) {
        poolFetcher = jdbcPoolFetcher
        reusable = canReusable
    }

    private Connection getConnection() {
        if (connection == null) {
            connection = poolFetcher.getConnection()
        }
        return connection
    }

    @Override
    def execute(QScript script) throws Exception {
        if (script instanceof QScriptResult) {
            return script.scriptResult
        }

        if (script.proxy != null && script.proxy.queryType == QueryType.DB_FUNCTION) {
            return executeCall(script)
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Query: -----------------------------------------------------\n" + script.proxy.query.trim())
            LOGGER.trace("------------------------------------------------------------")
        }

        if (script.proxy.queryType == QueryType.BULK_INSERT) {
            LOGGER.debug("Executing as batch...")
            return batchExecute(script);
        }

        PreparedStatement statement = null
        try {
            Map<String, Object> data = script.qSession.sessionVariables
            List<AParam> parameters = script.proxy.orderedParameters
            statement = prepareStatement(script, parameters, data)

            if (script.proxy.queryType == QueryType.SELECT) {
                if (returnRaw) {
                    LOGGER.info("Returning raw result")
                    return statement.executeQuery()
                } else {
                    LOGGER.trace("Transforming result set using {}", transformer.class.name)
                    return transformer.apply(statement.executeQuery())
                }
            } else {
                int count = statement.executeUpdate()
                List keys = [] as LinkedList
                if (count > 0 && isReturnKeys(script)) {
                    ResultSet genKeys
                    try {
                        genKeys = statement.getGeneratedKeys()
                        while (genKeys.next()) {
                            Object val = genKeys.getObject(1)
                            keys.add(val)
                        }
                    } finally {
                        if (genKeys != null) {
                            genKeys.close()
                        }
                    }
                }
                return toMap(count, keys)

            }

        } finally {
            if (statement != null) {
                statement.close()
            }
            closeConnection()
        }
    }

    private def batchExecute(QScript script) throws Exception {
        PreparedStatement statement = null
        try {
            statement = getConnection().prepareStatement(script.proxy.query)
            connection.setAutoCommit(false);

            List<AParam> parameters = script.proxy.orderedParameters
            Object batchData = script.qSession.sessionVariables["batch"];
            if (batchData == null) {
                throw new NyScriptExecutionException("No batch data has been specified through session variables 'batch'!");
            } else if (!(batchData instanceof List)) {
                throw new NyScriptExecutionException("Batch data expected to be a list of hashmaps!");
            }

            List<Map> records = batchData as List<Map>
            for (Map record : records) {
                assignParameters(statement, parameters, record)
                statement.addBatch()
            }

            int[] counts = statement.executeBatch()
            connection.commit()
            return counts;

        } finally {
            if (statement != null) {
                statement.close()
            }
            closeConnection()
        }
    }

    private def executeCall(QScript script) throws Exception {
        CallableStatement statement = null
        try {
            StoredFunction sp = script.proxy.rawObject
            LOGGER.info("Executing stored function '{}'", sp.name)
            statement = getConnection().prepareCall(script.proxy.query)
            Map<String, Object> data = script.qSession.sessionVariables

            List<AParam> parameters = script.proxy.orderedParameters

            // register out parameters
            for (int i = 0; i < parameters.size(); i++) {
                AParam param = parameters[i]
                if (!(param instanceof NamedParam)) {
                    throw new NyScriptExecutionException("Stored functions required to have named parameters!")
                }
                NamedParam namedParam = param as NamedParam
                if (namedParam.scope == null || namedParam.scope == AParam.ParamScope.IN) {
                    continue
                }

                LOGGER.trace("  <- Registering output: {}", namedParam.__mappingParamName)
                statement.registerOutParameter(namedParam.__mappingParamName, param.type)
            }

            // set parameter values
            for (int i = 0; i < parameters.size(); i++) {
                AParam param = parameters[i] as NamedParam
                Object itemValue = data.get(param.__name)
                if (itemValue == null) {
                    throw new NyException("Data for parameter '$param.__name' cannot be found!")
                }
                if (param.__mappingParamName == null) {
                    throw new NyException("Mapping parameter name has not been defined for SP input parameter '$param.__name'!")
                }

                LOGGER.trace(" Parameter #{} : {}", param.__mappingParamName, itemValue)
                statement.setObject(param.__mappingParamName, itemValue)
            }

            boolean hasResults = statement.execute()
            if (hasResults) {
                if (returnRaw) {
                    return statement.getResultSet()
                } else {
                    JdbcCallTransformInput input = new JdbcCallTransformInput(statement: statement, script: script)
                    return callResultTransformer.apply(input)
                }
            }

        } finally {
            if (statement != null) {
                statement.close()
            }
            closeConnection()
        }
    }

    private void closeConnection() {
        if (connection == null || reusable) {
            return
        }
        connection.close()
    }

    private static void assignParameters(PreparedStatement statement, List<AParam> parameters, Map data) {
        int cp = 1
        for (int i = 0; i < parameters.size(); i++) {
            AParam param = parameters[i]
            Object itemValue = deriveValue(data, param.__name)
            if (itemValue == null) {
                throw new NyException("Data for parameter '$param.__name' cannot be found!")
            }


            cp = invokeCorrectInput(statement, param, itemValue, cp)
        }
    }

    private PreparedStatement prepareStatement(QScript script, List<AParam> paramList, Map data) {
        List orderedParams = [] as LinkedList
        String query = script.proxy.query
        int cp = 1

        for (AParam param : paramList) {
            Object itemValue = deriveValue(data, param.__name)
            if (itemValue == null) {
                throw new NyScriptExecutionException("Data for parameter '$param.__name' cannot be found!")
            }

            LOGGER.trace(" Parameter #{} : {} [{}]", (cp), itemValue, itemValue.class.simpleName)
            if (param instanceof ParamList) {
                if (itemValue instanceof List) {
                    List itemList = itemValue
                    itemList.each { orderedParams.add(it) }
                    String pStr = itemList.stream().map({ return "?" }).collect(Collectors.joining(", "))
                    query = query.replaceAll("::" + param.__name + "::", pStr)
                    println query
                    cp += itemList.size()

                } else {
                    throw new NyScriptExecutionException("Parameter value of '$param.__name' expected to be a list but given " + itemValue.class.simpleName + "!");
                }
            } else {
                orderedParams.add(itemValue)
                cp++
            }
        }

        PreparedStatement statement;
        if (isReturnKeys(script)) {
            statement = getConnection().prepareStatement(query, Statement.RETURN_GENERATED_KEYS)
        } else {
            statement = getConnection().prepareStatement(query)
        }
        cp = 1
        for (Object pValue : orderedParams) {
            statement.setObject(cp++, pValue)
        }
        return statement
    }

    private static boolean isReturnKeys(QScript script) {
        script.proxy != null && script.proxy.qObject != null && script.proxy.qObject.returnType == QReturnType.KEYS
    }

    private static Object deriveValue(Map dataMap, String name) {
        if (name.indexOf('.') > 0) {
            String[] parts = name.split("[.]");
            Object res = dataMap;
            for (String p : parts) {
                res = res."$p"
            }
            if (res == dataMap) {
                return null
            }
            return res

        } else {
            return dataMap[name];
        }
    }

    @Override
    void startTransaction() throws NyException {
        getConnection().setAutoCommit(false)
        LOGGER.info("Starting new transaction.")
    }

    @Override
    void commit() throws NyException {
        connection.commit()
    }

    @Override
    def checkPoint() throws NyException {
        return connection.setSavepoint()
    }

    @Override
    void rollback(def checkpoint) throws NyException {
        if (checkpoint != null && checkpoint instanceof Savepoint) {
            connection.rollback(checkpoint)
        } else {
            connection.rollback()
        }
    }

    @Override
    void done() throws NyException {
        connection.setAutoCommit(true)
        LOGGER.info("Transaction completed.")
    }

    private static int invokeCorrectInput(PreparedStatement statement, AParam param, Object data, int index) {
        if (param.length > 0) {
            if (data instanceof List) {
                for (int i = 0; i < param.length; i++) {
                    statement.setObject(index + i, data.get(i))
                }
                return index + param.length
            }
            throw new NyException("Expected a List for the parameter '${param.__name}' having length greater than zero!")
        } else {
            statement.setObject(index, data)
            return index + 1
        }
    }

    private static List<Map> toMap(int count, List keys = null) {
        List<Map> res = []
        res.add([count: count])
        if (QUtils.notNullNorEmpty(keys)) {
            res.add([keys: keys])
        }
        return res
    }

    @Override
    void close() throws IOException {
        if (connection != null) {
            connection.close()
        }
    }
}