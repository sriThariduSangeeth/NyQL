package com.virtusa.gto.nyql.model

import com.virtusa.gto.nyql.configs.Configurations
import com.virtusa.gto.nyql.db.QDbFactory
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
/**
 * A context associated with a given script parsing and execution.
 *
 * @author IWEERARATHNA
 */
@CompileStatic
class QSession implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(QSession)

    /**
     * root script id which is the first (root) script which user has commanded to run.
     */
    String rootScriptId

    /**
     * Stack of scripts which are running.
     */
    private Stack<String> scriptStack = [] as Stack
    private final Object stackLock = new Object()

    /**
     * session variable set given by user.
     */
    Map<String, Object> sessionVariables = Collections.synchronizedMap(new HashMap<String, Object>())

    /**
     * active script repository.
     */
    QRepository scriptRepo

    /**
     * executor factory to execute scripts.
     */
    QExecutorFactory executorFactory

    /**
     * Database factory.
     */
    QDbFactory dbFactory

    /**
     * active executor for this session.
     */
    QExecutor executor

    /**
     * configuration instance associated with this instance.
     */
    Configurations configurations

    /**
     * Execution listener which allows external users to listen for execution events.
     * Can be used to cancel execution.
     */
    QExecutionListener executionListener

    /**
     * current execution depth.
     */
    private int execDepth = 0
    private final Object depthLock = new Object()

    private QSession() {}

    final void free() {
        synchronized (depthLock) {
            if (execDepth > 1) {
                LOGGER.warn('Cannot free session instance at this moment! Stack: ' + execDepth)
                return
            }
        }
        sessionVariables.clear()
        scriptStack.clear()
        scriptRepo = null
        executorFactory = null
        dbFactory = null
        executor = null
        configurations = null
    }

    static QSession create(Configurations configurations, String theScriptId) {
        QSession qSession = createSession(configurations.activeDbFactory,
                configurations.repositoryRegistry.defaultRepository(),
                null,
                configurations.executorRegistry.defaultExecutorFactory())
        qSession.rootScriptId = theScriptId
        qSession.scriptStack.push(theScriptId)
        qSession.configurations = configurations
        qSession
    }

    private static QSession createSession(QDbFactory dbFactory, QRepository repository,
                                  QExecutor executor, QExecutorFactory executorFactory) {
        QSession session = new QSession()

        session.dbFactory = dbFactory
        session.scriptRepo = repository
        session.executor = executor
        session.executorFactory = executorFactory
        //session.sessionVariables[Constants.DSL_ENTRY_WORD] = new DSL(session)
        //session.sessionVariables[Constants.DSL_SESSION_WORD] = session.sessionVariables
        session
    }

    void intoScript(String scriptId) {
        synchronized (stackLock) {
            scriptStack.push(scriptId)
        }
    }

    void outFromScript(String scriptId) {
        synchronized (stackLock) {
            scriptStack.pop()
        }
    }

    @CompileStatic
    String currentCallingFromScript() {
        synchronized (stackLock) {
            int loc = scriptStack.size() - 2;
            if (loc >= 0) {
                scriptStack.get(loc)
            } else {
                rootScriptId
            }
        }
    }

    String currentActiveScript() {
        synchronized (stackLock) {
            scriptStack.peek()
        }
    }

    QExecutor beingScript() {
        if (executor == null) {
            executor = executorFactory.createReusable()
        }
        def stack = incrStack()
        LOGGER.trace('Session {} starting script at execution depth {}', this, stack)
        executor
    }

    void closeScript() {
        def stack = decrStack()
        if (executor != null && stack <= 0) {
            LOGGER.trace('Closing executor since script has completed running.')
            executor.close()
            executor = null
        } else if (stack > 0) {
            LOGGER.trace('Session {} ended script at execution depth {}', this, stack)
        }
    }

    def execute(QScript script) {
        if (executor != null) {
            executor.execute(script)
        } else {
            executorFactory.create().execute(script)
        }
    }

    def execute(QScriptList scriptList) {
        if (executor != null) {
            executor.execute(scriptList)
        } else {
            executorFactory.create().execute(scriptList)
        }
    }

    private int incrStack() {
        synchronized (depthLock) {
            ++execDepth
        }
    }

    private int decrStack() {
        synchronized (depthLock) {
            --execDepth
        }
    }

    @Override
    String toString() {
        'QSession@' + Integer.toHexString(hashCode())
    }

    @Override
    void close() throws Exception {
        scriptStack.clear()
        sessionVariables.clear()

        scriptStack = null
        sessionVariables = null
    }
}
