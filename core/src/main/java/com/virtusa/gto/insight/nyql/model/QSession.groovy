package com.virtusa.gto.insight.nyql.model

import com.virtusa.gto.insight.nyql.DSL
import com.virtusa.gto.insight.nyql.DSLContext
import com.virtusa.gto.insight.nyql.utils.Constants

import java.util.concurrent.ConcurrentHashMap

/**
 * @author IWEERARATHNA
 */
class QSession {

    String scriptId
    Map<String, Object> sessionVariables = [:] as ConcurrentHashMap

    QRepository scriptRepo

    QExecutorFactory executorFactory
    QExecutor executor

    DSLContext dslContext

    private QSession() {}

    static QSession create(String theScriptId) {
        QSession qSession = createSession(DSLContext.getActiveDSLContext(),
                QRepositoryRegistry.instance.defaultRepository(),
                null,
                QExecutorRegistry.instance.defaultExecutorFactory())
        qSession.scriptId = theScriptId
        return qSession
    }

    private static QSession createSession(DSLContext context, QRepository repository, QExecutor executor, QExecutorFactory executorFactory) {
        QSession session = new QSession()

        session.dslContext = context
        session.scriptRepo = repository
        session.executor = executor
        session.executorFactory = executorFactory
        session.sessionVariables[Constants.DSL_ENTRY_WORD] = new DSL(session)
        session.sessionVariables[Constants.DSL_SESSION_WORD] = session.sessionVariables
        return session
    }

    QExecutor beingScript() {
        executor = executorFactory.createReusable();
        return executor
    }

    void closeScript() {
        if (executor != null) {
            executor.close()
        }
    }

    def execute(QScript script) {
        if (executor != null) {
            return executor.execute(script)
        } else {
            return executorFactory.create().execute(script)
        }
    }

    def execute(QScriptList scriptList) {
        if (executor != null) {
            return executor.execute(scriptList)
        } else {
            return executorFactory.create().execute(scriptList)
        }
    }
}
