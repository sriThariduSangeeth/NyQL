package com.virtusa.gto.insight.nyql

import com.virtusa.gto.insight.nyql.ddl.DDL
import com.virtusa.gto.insight.nyql.exceptions.NyException
import com.virtusa.gto.insight.nyql.model.QScript
import com.virtusa.gto.insight.nyql.model.QScriptList
import com.virtusa.gto.insight.nyql.model.QSession
import com.virtusa.gto.insight.nyql.utils.QUtils
import com.virtusa.gto.insight.nyql.utils.QueryType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentHashMap

/**
 * @author Isuru Weerarathna
 */
class DSL {

    private static final Logger LOGGER = LoggerFactory.getLogger(DSL.class)

    final QSession session
    final DSLContext dslContext

    private boolean currentTransactionAutoCommit = false

    public DSL(QSession theSession) {
        session = theSession
        dslContext = session.dslContext
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ////
    ////   Script Execution Related Commands
    ////
    ///////////////////////////////////////////////////////////////////////////////////

    DSL script(@DelegatesTo(DSL) closure) {
        def code = closure.rehydrate(this, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        code()

        return this
    }

    QScript $IMPORT(String scriptName) {
        return session.scriptRepo.parse(scriptName, session)
    }

    def RUN(QScriptList scriptList) {
        return session.executor.execute(scriptList)
    }

    def RUN(QScript script) {
        return session.executor.execute(script)
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

    QResultProxy dbFunction(String name, List<AParam> paramList) {
        StoredFunction sp = new StoredFunction(name: name, paramList: new ArrayList<AParam>(paramList))
        return session.dslContext.qTranslator.___storedFunction(sp)
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    QResultProxy nativeQuery(QueryType queryType, List<AParam> orderedParams = [], String query) {
        return new QResultProxy(query: String.valueOf(query).trim(),
                queryType: queryType,
                orderedParameters: orderedParams)
    }

    QResultProxy nativeQuery(QueryType queryType, List<AParam> orderedParams = [], Map queries) {
        def query = queries[dslContext.activeDb]
        if (query == null) {
            throw new NyException("No query is defined for the database '${dslContext.activeDb}'!")
        }
        return new QResultProxy(query: String.valueOf(query).trim(),
                queryType: queryType,
                orderedParameters: orderedParams)
    }

    QResultProxy insert(closure) {
        QueryInsert queryInsert = new QueryInsert(createContext())
        Object qs = assignTraits(queryInsert)

        def code = closure.rehydrate(qs, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        QueryInsert q = code()

        return q._ctx.translator.___insertQuery(q)
    }

    QResultProxy select(closure) {
        QuerySelect querySelect = new QuerySelect(createContext())
        Object qs = assignTraits(querySelect)

        def code = closure.rehydrate(qs, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        QuerySelect q = code()

        return q._ctx.translator.___selectQuery(q)
    }

    QResultProxy update(closure) {
        QueryUpdate queryUpdate = new QueryUpdate(createContext())
        Object qs = assignTraits(queryUpdate)

        def code = closure.rehydrate(qs, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        QueryUpdate q = code()

        return q._ctx.translator.___updateQuery(q)
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ////
    ////   Query Part Reuse Related Commands
    ////
    ///////////////////////////////////////////////////////////////////////////////////

    QResultProxy $q(closure) {
        QueryPart queryPart = new QueryPart(createContext())
        Object qp = assignTraits(queryPart)

        def code = closure.rehydrate(qp, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        QueryPart q = code()

        return q._ctx.translator.___partQuery(q)
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ////
    ////   DDL Related Commands
    ////
    ///////////////////////////////////////////////////////////////////////////////////

    QScriptList ddl(closure) {
        DDL activeDDL = new DDL(session)

        def code = closure.rehydrate(activeDDL, this, this)
        code.resolveStrategy = Closure.DELEGATE_ONLY
        activeDDL = code()

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
    DSL TRANSACTION(closure) {
        try {
            session.executor.startTransaction()

            def code = closure.rehydrate(this, this, this)
            code.resolveStrategy = Closure.DELEGATE_ONLY
            code()

            if (currentTransactionAutoCommit) {
                COMMIT()
            }

        } catch (Throwable ex) {
            LOGGER.error("Error occurred while in transaction!", ex)
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
        LOGGER.debug(String.valueOf(msg))
        return this
    }

    DSL $LOG(String message) {
        LOGGER.debug(message)
        return this
    }

    private def createContext(String db=null) {
        String dbName = db ?: dslContext.activeDb
        return new QContext(translator: dslContext.qTranslator, translatorName: dbName, ownerSession: session)
    }

    private Object assignTraits(Query query) {
        //String dbName = query._ctx.translatorName
        List<Class<?>> classes = dslContext.dbTraits
        if (QUtils.notNullNorEmpty(classes)) {
            Class<?>[] clz = new Class<?>[classes.size()]
            clz = classes.toArray(clz)
            return query.withTraits(clz)
        }
        return query
    }

}