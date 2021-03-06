package com.virtusa.gto.nyql.configs

import com.virtusa.gto.nyql.db.QDbFactory
import com.virtusa.gto.nyql.exceptions.NyConfigurationException
import com.virtusa.gto.nyql.exceptions.NyException
import com.virtusa.gto.nyql.model.*
import com.virtusa.gto.nyql.model.impl.QProfRepository
import com.virtusa.gto.nyql.utils.Constants
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
/**
 * @author iweerarathna
 */
class ConfigurationsV2 extends Configurations {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationsV2)

    ConfigurationsV2() {
        super()
    }

    @CompileStatic
    @Override
    protected void doConfig() throws NyException {
        validateProperties()

        ClassLoader classLoader = super.classLoader
        setName()

        databaseRegistry = QDatabaseRegistry.newInstance().discover(classLoader)
        executorRegistry = QExecutorRegistry.newInstance().discover(classLoader)
        repositoryRegistry = QRepositoryRegistry.newInstance().discover(classLoader)
        mapperRegistry = QMapperRegistry.newInstance().discover(classLoader)

        // validations before executions
        validateTranslators()
        validateExecutors()
        validateRepositories()
        validateMappers()

        // load query related configurations
        loadQueryInfo(getQueryConfigs())

        // mark active database
        String activeDb = getActivatedDb()
        LOGGER.info("Activated: ${activeDb}")

        // load executors
        DbInfo dbInfo = loadExecutors(activeDb, false)

        // load repositories
        loadRepos(false)

        // finally, initialize factory
        def factory = databaseRegistry.getDbFactory(activeDb)
        factory.init(this, dbInfo)

        LOGGER.debug('Default executor factory: ' + executorRegistry.defaultExecutorFactory().name)
    }

    @CompileStatic
    @Override
    protected DbInfo loadExecutors(String activeDb, boolean profEnabled) {
        QDbFactory activeFactory = databaseRegistry.getDbFactory(activeDb)
        Map executor = properties.get(ConfigKeys.EXECUTOR) as Map

        // String execName = executor.get('name')
        String execImpl = executor.get('impl')

        // set default as specified one...
        executorRegistry.makeDefault(execImpl)

        executor.putIfAbsent(ConfigKeys.JDBC_DRIVER_CLASS_KEY, activeFactory.driverClassName())
        executor.putIfAbsent(ConfigKeys.JDBC_DATASOURCE_CLASS_KEY, activeFactory.dataSourceClassName())

        QExecutorFactory executorFactory = executorRegistry.getExecutorFactory(execImpl)

        DbInfo activeDbInfo = executorFactory.init(executor, this)
        activeDbInfo
    }

    @Override
    @CompileStatic
    protected void loadRepos(boolean profEnabled) {
        Map repository
        if (!properties.containsKey(ConfigKeys.REPOSITORY)
            && properties.containsKey(ConfigKeys.REPOSITORIES)) {
            def list = properties.get(ConfigKeys.REPOSITORIES) as List
            repository = list[0] as Map
        } else {
            repository = properties.get(ConfigKeys.REPOSITORY) as Map
        }
        checkRepository(repository)

        String repoName = repository.get('name') ?: Constants.DEFAULT_REPOSITORY_NAME
        String repoImpl = repository.get('impl') ?: Constants.DEFAULT_REPOSITORY_IMPL
        String mapper = repository.get('mapper')

        Map args = (repository.get('mapperArgs') ?: [:]) as Map
        args.put(ConfigKeys.LOCATION_KEY, properties._location)

        // call from mapper factory
        def mapperFactory = mapperRegistry.getMapperFactory(mapper)
        QScriptMapper scriptMapper = mapperFactory.create(mapper, args, this)

        def factory = repositoryRegistry.getRepositoryFactory(repoImpl)
        QRepository qRepository = factory.create(this, scriptMapper)

        repositoryRegistry.register(repoName, qRepository)

        if (properties[ConfigKeys.REPO_MAP]) {
            Map<String, QRepository> repositoryMap = (Map<String, QRepository>) properties[ConfigKeys.REPO_MAP]
            repositoryMap.each {
                QRepository tmpRepo = profEnabled ? new QProfRepository(this, it.value) : it.value
                repositoryRegistry.register(it.key, tmpRepo)
            }
        }
    }

    private static void checkRepository(Map repo) {
        if (!repo.mapper) {
            throw new NyException('Mandatory field "mapper" is not specified!')
        }
    }


    @CompileStatic
    void validateMappers() {
        def allMaps = mapperRegistry.listAll()
        LOGGER.info("Found mapper implementations in classpath: [ ${allMaps.join(', ')} ]")

        if (allMaps.isEmpty()) {
            throw new NyConfigurationException('No mapper implementations has been found in classpath!')
        }
    }

    void validateRepositories() {
        def allRepos = repositoryRegistry.listAll()
        LOGGER.info("Found repository implementations in classpath: [ ${allRepos.join(', ')} ]")

        if (allRepos.isEmpty()) {
            throw new NyConfigurationException('No repository implementations has been found in classpath!')
        }
    }

    void validateTranslators() {
        def allDbs = databaseRegistry.listAll()
        LOGGER.info("Found database implementations in classpath: [ ${allDbs.join(', ')} ]")

        if (allDbs.isEmpty()) {
            throw new NyConfigurationException('No database implementations has been found in classpath!')
        }
    }

    void validateExecutors() {
        def allExecs = executorRegistry.listAll()
        LOGGER.info("Found executor implementations in classpath: [ ${allExecs.join(', ')} ]")

        if (allExecs.isEmpty()) {
            throw new NyConfigurationException('No executor implementations has been found in classpath!')
        }
    }

    void validateProperties() throws NyConfigurationException {
        def properties = super.properties

        assertTruth(properties.version == 2, "Version value must be 2! Received ${properties.version}")
        assertNonNull(properties.activate, "Mandatory parameter 'activate' is missing!")

        assertNonNull(properties.executor, 'Executor has not been specified!')
        assertNonNull(properties.repository, 'Repository has not been specified!')
        assertTruth(properties.executors == null, "Field 'executors' has been deprecated! NyQL v2 can have only one executor!")
    }

    static void assertNonNull(Object value, String msg) throws NyConfigurationException {
        if (value == null) {
            throw new NyConfigurationException(msg)
        }
    }

    static void assertIfNot(Object value, Class<?> type, String msg) throws NyConfigurationException {
        if (!type.isAssignableFrom(value.class)) {
            throw new NyConfigurationException(msg)
        }
    }

    static void assertTruth(boolean truth, String msg) throws NyConfigurationException {
        if (!truth) {
            throw new NyConfigurationException(msg)
        }
    }
}
