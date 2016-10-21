package com.virtusa.gto.insight.nyql

import com.virtusa.gto.insight.nyql.ddl.DDL
import com.virtusa.gto.insight.nyql.exceptions.NyException
import com.virtusa.gto.insight.nyql.model.QScript
import com.virtusa.gto.insight.nyql.model.QScriptList
import com.virtusa.gto.insight.nyql.model.QSession
import com.virtusa.gto.insight.nyql.model.units.AParam
import com.virtusa.gto.insight.nyql.utils.QUtils
import com.virtusa.gto.insight.nyql.utils.QueryCombineType
import com.virtusa.gto.insight.nyql.utils.QueryType
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Isuru Weerarathna
 */
@CompileStatic
class DSL {

    private static final Logger LOGGER = LoggerFactory.getLogger(DSL.class)

    final QSession session
    //final DSLContext dslContext

    private boolean currentTransactionAutoCommit = false

    public DSL(QSession theSession) {
        session = theSession
        set$SESSION(session.sessionVariables)
        //dslContext = session.dslContext
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ////
    ////   Script Execution Related Commands
    ////
    ///////////////////////////////////////////////////////////////////////////////////

    def script(@DelegatesTo(DSL) Closure closure) {
        try {
            session.beingScript()

            def code = closure.rehydrate(this, this, this)
            code.resolveStrategy = Closure.DELEGATE_ONLY
            return code()

        } finally {
            session.closeScript()
        }
    }

    QScript $IMPORT(String scriptName) {
        session.intoScript(scriptName)
        QScript res = session.scriptRepo.parse(scriptName, session)
        session.outFromScript(scriptName)
        return res
    }

    def RUN(QScriptList scriptList) {
        return session.execute(scriptList)
    }

    def RUN(QScript script) {
        return session.execute(script)
    }

    def RUN(QResultProxy proxy) {
        return RUN(session.scriptRepo.parse(proxy, session))
    }

    def RUN(String scriptName) {
        return RUN($IMPORT(scriptName))
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ////
    ////   Query Related Commands
    ////
    ///////////////////////////////////////////////////////////////////////////////////

    QResultProxy union(Object... qResultProxies) {
        List list = []
        for (def q : qResultProxies) {
            if (q instanceof QResultProxy) {
                list.add((QResultProxy) q)
            } else if (q instanceof QScript) {
                list.add(((QScript)q).proxy)
            }
        }
        return session.dbFactory.createTranslator().___combinationQuery(QueryCombineType.UNION, list);
    }

    QResultProxy unionDistinct(Object... qResultProxies) {
        List list = []
        for (def q : qResultProxies) {
            if (q instanceof QResultProxy) {
                list.add((QResultProxy) q)
            } else if (q instanceof QScript) {
                list.add(((QScript)q).proxy)
            }
        }
        return session.dbFactory.createTranslator().___combinationQuery(QueryCombineType.UNION_DISTINCT, list);
    }

    QResultProxy dbFunction(String name, List<AParam> paramList) {
        StoredFunction sp = new StoredFunction(name: name, paramList: new ArrayList<AParam>(paramList))
        return session.dbFactory.createTranslator().___storedFunction(sp)
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    QResultProxy nativeQuery(QueryType queryType, List<AParam> orderedParams = [], String query) {
        return new QResultProxy(query: String.valueOf(query).trim(),
                queryType: queryType,
                orderedParameters: orderedParams)
    }

    QResultProxy nativeQuery(QueryType queryType, List<AParam> orderedParams = [], Map queries) {
        String activeDb = session.dbFactory.dbName()
        def query = queries[activeDb]
        if (query == null) {
            throw new NyException("No query is defined for the database '${activeDb}'!")
        }
        return new QResultProxy(query: String.valueOf(query).trim(),
                queryType: queryType,
                orderedParameters: orderedParams)
    }

    QResultProxy bulkInsert(@DelegatesTo(value = QueryInsert, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        QueryInsert qs = new QueryInsert(createContext())
        //Object qs = assignTraits(queryInsert)

        def code = closure.rehydrate(qs, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        code()

        QResultProxy proxy = qs._ctx.translator.___insertQuery(qs)
        proxy.setQueryType(QueryType.BULK_INSERT)
        return proxy
    }

    QResultProxy delete(@DelegatesTo(value = QueryDelete, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        QueryDelete qs = new QueryDelete(createContext())
        //Object qs = assignTraits(queryDelete)

        def code = closure.rehydrate(qs, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        code()

        return qs._ctx.translator.___deleteQuery(qs)
    }

    QResultProxy insert(@DelegatesTo(value = QuerySelect, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        QueryInsert qs = new QueryInsert(createContext())
        //Object qs = assignTraits(queryInsert)

        def code = closure.rehydrate(qs, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        code()

        return qs._ctx.translator.___insertQuery(qs)
    }

    QResultProxy select(@DelegatesTo(value = QuerySelect, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        QuerySelect qs = new QuerySelect(createContext())
        //Object qs = assignTraits(querySelect)

        def code = closure.rehydrate(qs, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        code()

        return qs._ctx.translator.___selectQuery(qs)
    }

    QResultProxy update(@DelegatesTo(value = QueryUpdate, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        QueryUpdate qs = new QueryUpdate(createContext())
        //Object qs = assignTraits(queryUpdate)

        def code = closure.rehydrate(qs, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        code()

        return qs._ctx.translator.___updateQuery(qs)
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ////
    ////   Query Part Reuse Related Commands
    ////
    ///////////////////////////////////////////////////////////////////////////////////

    QResultProxy $q(@DelegatesTo(value = QueryPart, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        QueryPart qp = new QueryPart(createContext())
        //Object qp = assignTraits(queryPart)

        def code = closure.rehydrate(qp, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        code()

        return qp._ctx.translator.___partQuery(qp)
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ////
    ////   DDL Related Commands
    ////
    ///////////////////////////////////////////////////////////////////////////////////

    QScriptList ddl(@DelegatesTo(value = DDL, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        DDL activeDDL = new DDL(session)

        def code = closure.rehydrate(activeDDL, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        code()

        return activeDDL.createScripts()
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ////
    ////   Transaction Related Commands
    ////
    ///////////////////////////////////////////////////////////////////////////////////

    /**
     * Enables auto commit at the end of transaction.
     * @param autoCommit status of auto committing.
     * @return this DSL instance.
     */
    DSL AUTO_COMMIT(boolean autoCommit=true) {
        currentTransactionAutoCommit = autoCommit
        return this
    }

    /**
     * Starts a new transaction.
     *
     * Note: No sub-transactions are allowed within a transaction
     *
     * @param autoCommit auto commit on successful transaction.
     * @param closure transaction content.
     * @return this same DSL instance
     */
    DSL TRANSACTION(@DelegatesTo(value = DSL, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        try {
            session.executor.startTransaction()

            def code = closure.rehydrate(this, this, this)
            code.resolveStrategy = Closure.DELEGATE_ONLY
            code()

            if (currentTransactionAutoCommit) {
                COMMIT()
            }

        } catch (Throwable ex) {
            LOGGER.error('Error occurred while in transaction!', ex)
            ROLLBACK()

        } finally {
            session.executor.done()
            currentTransactionAutoCommit = false
        }
        return this
    }

    /**
     * Commit the changes done within this active transaction.
     *
     * @return this same DSL instance
     */
    DSL COMMIT() {
        session.executor.commit()
        return this
    }

    /**
     * Creates a new checkpoint at this point. So, you will probably can rollback
     * when an error occurred.
     *
     * @return a reference to the newly created checkpoint at this stage.
     */
    def CHECKPOINT() {
        return session.executor.checkPoint()
    }

    /**
     * Rollback the transaction to the given checkpoint, or to the beginning if no checkpoint
     * has been given.
     *
     * @param checkPoint checkpoint reference to rollback
     * @return this same DSL instance
     */
    DSL ROLLBACK(Object checkPoint=null) {
        session.executor.rollback(checkPoint)
        return this
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ////
    ////   Log Related Commands
    ////
    ///////////////////////////////////////////////////////////////////////////////////

    DSL $LOG(Object msg) {
        return $LOG(String.valueOf(msg))
    }

    DSL $LOG(String message) {
        LOGGER.debug('[@ ' + session.currentActiveScript() + ' @]' + message)
        return this
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ////
    ////   Common Commands
    ////
    ///////////////////////////////////////////////////////////////////////////////////

    AParam PARAM(String name, AParam.ParamScope scope=null, String mappingName=null) {
        return QUtils.createParam(name, scope, mappingName)
    }

    private QContext createContext(String db=null) {
        String dbName = db ?: session.dbFactory.dbName()
        return new QContext(translator: session.dbFactory.createTranslator(),
                translatorName: dbName,
                ownerSession: session)
    }

    DSL $DSL = this

    Map $SESSION

}
