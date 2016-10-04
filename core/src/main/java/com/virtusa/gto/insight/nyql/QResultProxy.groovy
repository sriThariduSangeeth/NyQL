package com.virtusa.gto.insight.nyql

import com.virtusa.gto.insight.nyql.model.blocks.AParam
import com.virtusa.gto.insight.nyql.utils.QueryType
import groovy.transform.ToString

/**
 * @author IWEERARATHNA
 */
@ToString(includePackage = false)
class QResultProxy {

    String query

    List<AParam> orderedParameters

    QueryType queryType = QueryType.UNKNOWN

    def rawObject
    def qObject
}
