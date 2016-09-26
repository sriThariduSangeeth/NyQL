import com.virtusa.gto.insight.nyql.utils.QUtils;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.Concat;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;

import java.util.List;

import static com.sun.xml.internal.fastinfoset.alphabet.BuiltInRestrictedAlphabets.table;

/**
 * @author IWEERARATHNA
 */
public class SqlToDSL {

    public static void main(String[] args) throws JSQLParserException {
        Statement stmt = CCJSqlParserUtil.parse("select sum(release_open_tech_s1) as techS1,sum(release_open_tech_s2) as techS2,sum(release_open_tech_s3) as techS3 ,sum(release_open_security_s1) as securityS1, sum(release_open_security_s2) as securityS2, sum(release_open_security_s3) as securityS3 from release_module_summary where org_unit_id=\"123\" and release_id=\"11739\" and team_id in (1410,1411) and module_id in (97389,97390);");
        if (stmt instanceof Select) {
            visit((Select)stmt);
        } else if (stmt instanceof Update) {
            visit((Update)stmt);
        } else if (stmt instanceof Insert) {
            visit((Insert)stmt);
        }
    }

    private static void visit(Insert insertStmt) {
        StringBuilder dsl = new StringBuilder();
        dsl.append("$DSL.insert {\n");

        Table table = insertStmt.getTable();
        handleTarget(table, dsl);

        if (QUtils.notNullNorEmpty(insertStmt.getColumns())) {
            dsl.append("DATA (\n");
            ExpressionList expressionList = (ExpressionList) insertStmt.getItemsList();
            for (int i = 0; i < insertStmt.getColumns().size(); i++) {
                Column column = insertStmt.getColumns().get(i);
                Expression expression = expressionList.getExpressions().get(i);

                dsl.append("\t\"");
                toColumn(column, dsl);
                dsl.append("\": ");
                resolveExpr(expression, dsl);
                dsl.append("\n");
            }
            dsl.append(")\n");
        }

        dsl.append("\n}\n");
        System.out.println(dsl);
    }

    private static void visit(Update updateStmt) {
        StringBuilder dsl = new StringBuilder();
        dsl.append("$DSL.update {\n");

        Table table = updateStmt.getTable();
        handleTarget(table, dsl);
        handleJoin(updateStmt.getJoins(), dsl);

        if (QUtils.notNullNorEmpty(updateStmt.getColumns())) {
            dsl.append("SET {\n");
            for (int i = 0; i < updateStmt.getColumns().size(); i++) {
                Column column = updateStmt.getColumns().get(i);
                Expression expression = updateStmt.getExpressions().get(i);

                dsl.append("\tEQ (");
                toColumn(column, dsl);
                dsl.append(", ");
                resolveExpr(expression, dsl);
                dsl.append(")\n");
            }
            dsl.append("}\n");
        }

        if (updateStmt.getWhere() != null) {
            handleWhere(updateStmt.getWhere(), dsl);
        }

        dsl.append("\n}\n");
        System.out.println(dsl);
    }

    private static void visit(Select selectStmt) {
        StringBuilder dsl = new StringBuilder();
        dsl.append("$DSL.select {\n");
        PlainSelect plainSelect = (PlainSelect) selectStmt.getSelectBody();

        Table table = (Table) plainSelect.getFromItem();
        handleTarget(table, dsl);

        handleJoin(plainSelect.getJoins(), dsl);

        if (QUtils.notNullNorEmpty(plainSelect.getSelectItems())) {
            dsl.append("\nFETCH (");
            int c = 0;
            for (SelectItem selectItem : plainSelect.getSelectItems()) {
                if (c > 0) dsl.append(", ");
                if (selectItem instanceof AllColumns) {
                    continue;
                } else if (selectItem instanceof AllTableColumns) {
                    dsl.append(toTable(((AllTableColumns) selectItem).getTable(), false));
                } else if (selectItem instanceof SelectExpressionItem) {
                    resolveExpr(((SelectExpressionItem) selectItem).getExpression(), dsl);
                    if (((SelectExpressionItem) selectItem).getAlias() != null) {
                        dsl.append(".alias(").append(quote(((SelectExpressionItem) selectItem).getAlias().getName())).append(")");
                    }
                }

                c++;
            }
            dsl.append(")\n");
        }

        if (plainSelect.getInto() != null) {
            dsl.append("\nINTO (").append(toTable(plainSelect.getInto(), true)).append(")\n");
        }

        if (plainSelect.getWhere() != null) {
            handleWhere(plainSelect.getWhere(), dsl);
        }

        if (plainSelect.getGroupByColumnReferences() != null) {
            dsl.append("\nGROUP_BY (");
            int c = 0;
            for (Expression e : plainSelect.getGroupByColumnReferences()) {
                if (c > 0) dsl.append(", ");

                resolveExpr(e, dsl);
                c++;
            }
            dsl.append(")\n");
        }

        if (plainSelect.getHaving() != null) {
            dsl.append("HAVING {\n").append("\t");
            resolveExpr(plainSelect.getHaving(), dsl);
            dsl.append("\n}\n");
        }

        if (QUtils.notNullNorEmpty(plainSelect.getOrderByElements())) {
            dsl.append("ORDER_BY (");
            int c = 0;
            for (OrderByElement e : plainSelect.getOrderByElements()) {
                if (c > 0) dsl.append(", ");

                if (!e.isAsc()) dsl.append("DESC(");
                resolveExpr(e.getExpression(), dsl);
                if (!e.isAsc()) dsl.append(")");
                c++;
            }
            dsl.append(")\n");
        }

        if (plainSelect.getLimit() != null) {
            handleLimit(plainSelect.getLimit(), dsl);
        }

        dsl.append("\n}\n");
        //System.out.println(selectStmt);
        System.out.println(dsl);
    }

    private static void handleTarget(Table table, StringBuilder dsl) {
        dsl.append("\nTARGET (TABLE(").append(quote(table.getName())).append(")");
        if (table.getAlias() != null) {
            dsl.append(".alias(").append(quote(table.getAlias().getName())).append(")\n");
        }
        dsl.append(")\n");
    }

    private static void handleLimit(Limit limit, StringBuilder dsl) {
        if (limit.isRowCountJdbcParameter()) {
            dsl.append("LIMIT ");
            dsl.append("PARAM(").append(quote("rowCount")).append(") ");
        } else {
            if (limit.getRowCount() > 0) {
                dsl.append("LIMIT ");
                dsl.append(limit.getRowCount()).append(" ");
            }
        }

        if (limit.isOffsetJdbcParameter()) {
            dsl.append("OFFSET ");
            dsl.append("PARAM(").append(quote("offsetRecords")).append(") ");
        } else {
            if (limit.getOffset() >= 0) {
                dsl.append("OFFSET ");
                dsl.append(limit.getOffset()).append(" ");
            }
        }
    }

    private static void handleWhere(Expression expression, StringBuilder dsl) {
        dsl.append("\nWHERE {\n");
        dsl.append("\t");
        resolveExpr(expression, dsl);
        dsl.append("\n}\n");
    }

    private static void handleJoin(List<Join> joins, StringBuilder dsl) {
        if (joins == null) {
            return;
        }

        dsl.append("\nJOINING {\n").append(" TARGET() ");
        int count = 0;
        for (Join j : joins) {
            if (count > 0) dsl.append(" ");

            if (j.isLeft()) dsl.append("LEFT_");
            else if (j.isRight()) dsl.append("RIGHT_");
            else if (j.isFull()) dsl.append("FULL_");

            if (j.isOuter()) dsl.append("OUTER_");
            if (j.isInner()) dsl.append("INNER_");
            dsl.append("JOIN ");

            if (j.getRightItem() instanceof Table) {
                dsl.append(toTable((Table)j.getRightItem(), true)).append(" ");
            }

            if (j.getOnExpression() != null) {
                dsl.append("ON ");
                if (j.getOnExpression() instanceof EqualsTo) {
                    resolveExpr(((EqualsTo) j.getOnExpression()).getLeftExpression(), dsl);
                    dsl.append(", ");
                    resolveExpr(((EqualsTo) j.getOnExpression()).getRightExpression(), dsl);
                } else {
                    dsl.append("{");
                    resolveExpr(j.getOnExpression(), dsl);
                    dsl.append("}");
                }

            }
            count++;
        }


        dsl.append("\n}\n");
    }

    private static void resolveExpr(Expression expr, StringBuilder dsl) {
        if (expr instanceof Addition) {
            resolveExpr(((Addition) expr).getLeftExpression(), dsl);
            dsl.append(" + ");
            resolveExpr(((Addition) expr).getRightExpression(), dsl);
        } else if (expr instanceof AndExpression) {
            resolveExpr(((AndExpression) expr).getLeftExpression(), dsl);
            dsl.append("\nAND");
            boolean p = ((AndExpression) expr).getRightExpression() instanceof Parenthesis;
            if (p) dsl.append(" {\n"); else dsl.append("\n");
            resolveExpr(((AndExpression) expr).getRightExpression(), dsl);
            if (p) dsl.append("\n}");
        } else if (expr instanceof Between) {
            resolveExpr(((Between) expr).getLeftExpression(), dsl);
            if (((Between) expr).isNot()) dsl.append("NOT ");
            dsl.append("BETWEEN ");
            resolveExpr(((Between) expr).getBetweenExpressionStart(), dsl);
            dsl.append("AND ");
            resolveExpr(((Between) expr).getBetweenExpressionEnd(), dsl);
        } else if (expr instanceof CaseExpression) {
            dsl.append("CASE {");
            for (Expression e : ((CaseExpression) expr).getWhenClauses()) {
                resolveExpr(e, dsl);
            }
            if (((CaseExpression) expr).getElseExpression() != null) {
                dsl.append("ELSE {");
                resolveExpr(((CaseExpression) expr).getElseExpression(), dsl);
                dsl.append("}");
            }
            dsl.append("} ");
        } else if (expr instanceof CastExpression) {
            dsl.append("CAST(");
            resolveExpr(((CastExpression) expr).getLeftExpression(), dsl);
            dsl.append(", ").append(quote(((CastExpression) expr).getType().getDataType())).append(")");
        } else if (expr instanceof Column) {
            toColumn((Column)expr, dsl);
        } else if (expr instanceof Concat) {
            dsl.append("CONCAT(");
            resolveExpr(((Concat) expr).getLeftExpression(), dsl);
            dsl.append(",");
            resolveExpr(((Concat) expr).getRightExpression(), dsl);
            dsl.append(") ");
        } else if (expr instanceof DateValue) {
            dsl.append(quote(((DateValue) expr).getValue().toString()));
        } else if (expr instanceof DoubleValue) {
            dsl.append(((DoubleValue) expr).getValue());
        } else if (expr instanceof EqualsTo) {
            dsl.append("EQ (");
            resolveExpr(((EqualsTo) expr).getLeftExpression(), dsl);
            dsl.append(", ");
            resolveExpr(((EqualsTo) expr).getRightExpression(), dsl);
            dsl.append(") ");
        } else if (expr instanceof Function) {
            dsl.append(((Function) expr).getName().toUpperCase()).append("(");
            if (((Function) expr).getParameters() != null && ((Function) expr).getParameters().getExpressions() != null) {
                for (Expression e : ((Function) expr).getParameters().getExpressions()) {
                    resolveExpr(e, dsl);
                }
            }
            dsl.append(")");
        } else if (expr instanceof GreaterThan) {
            dsl.append("GT (");
            resolveExpr(((GreaterThan) expr).getLeftExpression(), dsl);
            dsl.append(", ");
            resolveExpr(((GreaterThan) expr).getRightExpression(), dsl);
            dsl.append(") ");
        } else if (expr instanceof GreaterThanEquals) {
            dsl.append("GTE (");
            resolveExpr(((GreaterThanEquals) expr).getLeftExpression(), dsl);
            dsl.append(", ");
            resolveExpr(((GreaterThanEquals) expr).getRightExpression(), dsl);
            dsl.append(") ");
        } else if (expr instanceof InExpression) {
            dsl.append("IN (");
            resolveExpr(((InExpression) expr).getLeftExpression(), dsl);
            dsl.append(", ");
            ExpressionList leftItemsList = (ExpressionList) ((InExpression) expr).getRightItemsList();
            if (leftItemsList != null) {
                int c = 0;
                for (Expression e : leftItemsList.getExpressions()) {
                    if (c > 0) dsl.append(", ");

                    resolveExpr(e, dsl);
                    c++;
                }
            }
            dsl.append(") ");
        } else if (expr instanceof IsNullExpression) {
            if (((IsNullExpression) expr).isNot()) dsl.append("NOTNULL (");
            else dsl.append("ISNULL (");
            resolveExpr(((IsNullExpression) expr).getLeftExpression(), dsl);
            dsl.append(") ");
        } else if (expr instanceof JdbcNamedParameter) {
            dsl.append("PARAM(").append(quote(((JdbcNamedParameter) expr).getName())).append(") ");
        } else if (expr instanceof JdbcParameter) {
            dsl.append("PARAM(").append(quote("<your-param-name-here>")).append(") ");
        } else if (expr instanceof LikeExpression) {
            if (((LikeExpression) expr).isNot()) dsl.append("NOTLIKE (");
            else dsl.append("LIKE (");
            resolveExpr(((LikeExpression) expr).getLeftExpression(), dsl);
            dsl.append(", ");
            resolveExpr(((LikeExpression) expr).getRightExpression(), dsl);
            dsl.append(") ");
        } else if (expr instanceof LongValue) {
            dsl.append(((LongValue) expr).getValue());
        } else if (expr instanceof MinorThan) {
            dsl.append("LT (");
            resolveExpr(((MinorThan) expr).getLeftExpression(), dsl);
            dsl.append(", ");
            resolveExpr(((MinorThan) expr).getRightExpression(), dsl);
            dsl.append(") ");
        } else if (expr instanceof MinorThanEquals) {
            dsl.append("LTE (");
            resolveExpr(((MinorThanEquals) expr).getLeftExpression(), dsl);
            dsl.append(", ");
            resolveExpr(((MinorThanEquals) expr).getRightExpression(), dsl);
            dsl.append(") ");
        } else if (expr instanceof NotEqualsTo) {
            dsl.append("NEQ (");
            resolveExpr(((NotEqualsTo) expr).getLeftExpression(), dsl);
            dsl.append(", ");
            resolveExpr(((NotEqualsTo) expr).getRightExpression(), dsl);
            dsl.append(") ");
        } else if (expr instanceof NullValue) {
            dsl.append("null");
        } else if (expr instanceof OrExpression) {
            resolveExpr(((OrExpression) expr).getLeftExpression(), dsl);
            dsl.append("\nOR");
            boolean p = ((OrExpression) expr).getRightExpression() instanceof Parenthesis;
            if (p) dsl.append(" {\n"); else dsl.append("\n");
            resolveExpr(((OrExpression) expr).getRightExpression(), dsl);
            if (p) dsl.append("\n}");
        } else if (expr instanceof Parenthesis) {
            resolveExpr(((Parenthesis) expr).getExpression(), dsl);
        } else if (expr instanceof StringValue) {
            dsl.append("STR(").append(quote(((StringValue) expr).getValue())).append(")");
        } else if (expr instanceof TimeValue) {
            dsl.append(quote(((TimeValue) expr).getValue().toString()));
        } else if (expr instanceof TimestampValue) {
            dsl.append(quote(((TimestampValue) expr).getValue().toString()));
        } else if (expr instanceof WhenClause) {
            dsl.append("WHEN {");
            resolveExpr(((WhenClause) expr).getWhenExpression(), dsl);
            dsl.append("} THEN {");
            resolveExpr(((WhenClause) expr).getThenExpression(), dsl);
            dsl.append("} ");
        }
    }

    private static void toColumn(Column column, StringBuilder dsl) {
        if (column.getTable() != null && column.getTable().getName() != null) {
            dsl.append(column.getTable().getName()).append(".").append(column.getColumnName());
        } else {
            dsl.append(column.getColumnName());
        }
    }

    private static String toTable(Table table, boolean full) {
        if (full) {
            String tbName = "TABLE(" + quote(table.getName(), '"') + ")";
            if (table.getAlias() != null) {
                tbName = tbName + ".alias(" + quote(table.getAlias().getName(), '"') + ")";
            }
            return tbName;
        } else {
            if (table.getAlias() != null) {
                return table.getAlias().getName();
            } else {
                return "TABLE(" + quote(table.getName(), '"') + ")";
            }
        }
    }

    static String quote(String name) {
        return quote(name, '"');
    }

    static String quote(String name, char q) {
        return String.valueOf(q) + name + String.valueOf(q);
    }

}