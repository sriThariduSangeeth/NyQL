package com.virtusa.gto.nyql.engine.exceptions

import com.virtusa.gto.nyql.exceptions.NyException
import groovy.transform.CompileStatic

/**
 * @author IWEERARATHNA
 */
@CompileStatic
class NyScriptParseException extends NyException {

    NyScriptParseException(String scriptId, File file, Throwable inner) {
        super("Query script cannot be parsed due to syntax errors! ['$scriptId', ${file.absolutePath}]", inner)
    }

}
