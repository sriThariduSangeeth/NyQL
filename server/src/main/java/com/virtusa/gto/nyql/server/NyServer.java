package com.virtusa.gto.nyql.server;


import com.google.gson.Gson;
import com.virtusa.gto.nyql.engine.NyQL;
import com.virtusa.gto.nyql.exceptions.NyException;
import com.virtusa.gto.nyql.model.QScript;
import com.virtusa.gto.nyql.model.QScriptResult;
import com.virtusa.gto.nyql.model.units.AParam;
import groovy.json.JsonSlurper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author IWEERARATHNA
 */
public class NyServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NyServer.class);

    private static final String DEF_SERVER_JSON = "./config/server.json";
    private static final String DEF_NYQL_JSON = "./config/nyql.json";

    private static final Gson GSON = new Gson();

    private String basePath;

    private NyServer() {
    }

    @SuppressWarnings("unchecked")
    private void init() {
        String serverJson = System.getProperty("NYSERVER_CONFIG_PATH", DEF_SERVER_JSON);
        String nyqlJson = System.getProperty("NYSERVER_NYJSON_PATH", DEF_NYQL_JSON);

        // read server config file
        File confFile = new File(serverJson);
        JsonSlurper jsonSlurper = new JsonSlurper();
        Map<String, Object> confObject = (Map<String, Object>) jsonSlurper.parse(confFile, StandardCharsets.UTF_8.name());

        basePath = confObject.get("basePath") != null ? String.valueOf(confObject.get("basePath")) : "";
        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        int port = (int) confObject.get("port");

        File nyConfigFile = new File(nyqlJson);
        NyQL.configure(nyConfigFile);

        // register nyql shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(NyQL::shutdown));

        Spark.port(port);

        // register spark server stop hook
        Runtime.getRuntime().addShutdownHook(new Thread(Spark::stop));

        // register all routes...
        registerRoutes();

        LOGGER.debug("Server is running at " + port + "...");
    }

    private void registerRoutes() {
        Spark.webSocket(basePath + "/profile", NyProfileSocket.class);

        Spark.post(basePath + "/parse", this::epParse, GSON::toJson);
        Spark.post(basePath + "/execute", this::epExecute, GSON::toJson);

        // handle exceptions
        Spark.exception(NyException.class, this::anyError);
    }

    @SuppressWarnings("unchecked")
    private Object epParse(Request req, Response res) throws Exception {
        res.type("application/json");

        Map<String, Object> bodyData = (Map<String, Object>) new JsonSlurper().parseText(req.body());
        String scriptId = String.valueOf(bodyData.get("scriptId"));
        Map<String, Object> data = new HashMap<>();
        if (bodyData.containsKey("data")) {
            data = (Map<String, Object>) bodyData.get("data");
        }
        QScript result = NyQL.parse(scriptId, data);

        Map<String, Object> r = new HashMap<>();
        if (result instanceof QScriptResult) {
            r.put("result", ((QScriptResult) result).getScriptResult());
            r.put("query", null);
            r.put("params", null);
        } else {
            r.put("result", null);
            r.put("query", result.getProxy().getQuery());
            r.put("params", result.getProxy().getOrderedParameters().stream()
                    .map(AParam::get__name).collect(Collectors.toList()));
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private Object epExecute(Request req, Response res) throws Exception {
        res.type("application/json");

        Map<String, Object> bodyData = (Map<String, Object>) new JsonSlurper().parseText(req.body());
        String scriptId = String.valueOf(bodyData.get("scriptId"));
        Map<String, Object> data = new HashMap<>();
        if (bodyData.containsKey("data")) {
            data = (Map<String, Object>) bodyData.get("data");
        }

        return NyQL.execute(scriptId, data);
    }

    private void anyError(Exception ex, Request req, Response res) {
        res.status(500);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        // turn off jetty logging
        org.eclipse.jetty.util.log.Log.setLog(null);

        NyServer server = new NyServer();
        server.init();
    }

}
