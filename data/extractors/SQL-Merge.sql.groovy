/*
 * Available context bindings:
 *   COLUMNS     List<DataColumn>
 *   ROWS        Iterable<DataRow>
 *   OUT         { append() }
 *   FORMATTER   { format(row, col); formatValue(Object, col); getTypeName(Object, col); isStringLiteral(Object, col); }
 *   TRANSPOSED  Boolean
 * plus ALL_COLUMNS, TABLE, DIALECT
 *
 * where:
 *   DataRow     { rowNumber(); first(); last(); data(): List<Object>; value(column): Object }
 *   DataColumn  { columnNumber(), name() }
 */

import com.intellij.database.model.ObjectKind

SEP = ", "
QUOTE     = "\'"
STRING_PREFIX = DIALECT.getDbms().isMicrosoft() ? "N" : ""
NEWLINE   = System.getProperty("line.separator")
TABS = "    "

KEYWORDS_LOWERCASE = com.intellij.database.util.DbSqlUtil.areKeywordsLowerCase(PROJECT)

KW_AND = KEYWORDS_LOWERCASE ? "and " : "AND "
KW_AS = KEYWORDS_LOWERCASE ? "as " : "AS "

KW_BY = KEYWORDS_LOWERCASE ? "by " : "BY "

KW_DELETE = KEYWORDS_LOWERCASE ? "delete " : "DELETE "
KW_DROP = KEYWORDS_LOWERCASE ? "drop " : "DROP "

KW_FROM = KEYWORDS_LOWERCASE ? "from " : "FROM "

KW_IFOBJ = KEYWORDS_LOWERCASE ? "if object_id('" : "IF OBJECT_ID('"
KW_INSERT = KEYWORDS_LOWERCASE ? "insert " : "INSERT "
KW_INTO = KEYWORDS_LOWERCASE ? "into " : "INTO "
KW_ISNNLL = KEYWORDS_LOWERCASE ? "') is not null drop table " : "') IS NOT NULL DROP TABLE "

KW_MATCHED = KEYWORDS_LOWERCASE ? "matched " : "MATCHED "
KW_MERGE = KEYWORDS_LOWERCASE ? "merge " : "MERGE "

KW_NOT = KEYWORDS_LOWERCASE ? "not " : "NOT "
KW_NULL = KEYWORDS_LOWERCASE ? "null " : "NULL "

KW_ON = KEYWORDS_LOWERCASE ? "on " : "ON "

KW_PRINT = KEYWORDS_LOWERCASE ? "print " : "PRINT "

KW_SELECT = KEYWORDS_LOWERCASE ? "select " : "SELECT "
KW_SET = KEYWORDS_LOWERCASE ? "set " : "SET "
KW_SOURCE = KEYWORDS_LOWERCASE ? "source " : "SOURCE "

KW_TABLE = KEYWORDS_LOWERCASE ? "table " : "TABLE "
KW_TARGET = KEYWORDS_LOWERCASE ? "target " : "TARGET "
KW_THEN = KEYWORDS_LOWERCASE ? "then " : "THEN "

KW_UPDATE = KEYWORDS_LOWERCASE ? "update" : "UPDATE "
KW_USING = KEYWORDS_LOWERCASE ? "using " : "USING "

KW_VALUES = KEYWORDS_LOWERCASE ? "values " : "VALUES "

KW_WITH = KEYWORDS_LOWERCASE ? "with " : "WITH "
KW_WHEN = KEYWORDS_LOWERCASE ? "when " : "WHEN "
KW_WHERE = KEYWORDS_LOWERCASE ? "where " : "WHERE "


temp = tempTable(TABLE)
source = sourceTable(TABLE)
cteName = "cte_data "
tempdb = "tempdb.dbo."

begin = true
create_temp = true

def tempTable(TABLE) {
    def tempTable = "#"
    if (TABLE == null) tempTable = tempTable + ("MY_TABLE")
    else tempTable = tempTable + (TABLE.getParent().getName()) + ("_") + (TABLE.getName())
}

def sourceTable(TABLE){
    if (TABLE == null)
        sourceTable = "MY_TABLE"
    else
        sourceTable = TABLE.getParent().getName() + "." + TABLE.getName()

    sourceTable += " "
}

def primaryKeys(TABLE){

    def keys = KW_ON

    if(TABLE == null)
        keys += "t.table_primaryKey = s.table_primaryKey"
    else{
        def key = TABLE.getDasChildren(ObjectKind.KEY).find { it.isPrimary() }

        if (key != null) {
            def primaryKeys = []
            key.getColumnsRef().names().each { colName ->
                primaryKeys.add("t." + colName + " = s." + colName)
          }
          keys += primaryKeys.join(NEWLINE + TABS + KW_AND)
        }
    }

    primaryKeys = keys
}


def record(columns, dataRow) {

    def temp = tempTable(TABLE)

    if(create_temp){
        OUT.append(KW_SELECT)

        columns.eachWithIndex { column, idx ->
                OUT.append(column.name()).append(idx != columns.size() - 1 ? SEP : "")
            }

        OUT.append(NEWLINE)
        OUT.append(KW_INTO).append(temp).append(" ").append(KW_FROM)
        if (TABLE == null) OUT.append("MY_TABLE")
        else OUT.append(TABLE.getParent().getName()).append(".").append(TABLE.getName())
        OUT.append(" ")
        OUT.append(KW_WHERE).append("0=1;")
        OUT.append(NEWLINE).append(NEWLINE)

        create_temp = false
    }

    if (begin) {
        OUT.append(KW_INSERT).append(KW_INTO).append(temp)
        OUT.append(" (")

        columns.eachWithIndex { column, idx ->
            OUT.append(column.name()).append(idx != columns.size() - 1 ? SEP : "")
        }

        OUT.append(")").append(NEWLINE)
        OUT.append(KW_VALUES).append("  (")
        begin = false
    }
    else {
        OUT.append(",").append(NEWLINE)
        OUT.append("        (")
    }

    columns.eachWithIndex { column, idx ->
        def value = dataRow.value(column)
        def stringValue = value == null ? KW_NULL : FORMATTER.formatValue(value, column)
        def isStringLiteral = value != null && FORMATTER.isStringLiteral(value, column)
        if (isStringLiteral && DIALECT.getDbms().isMysql()) stringValue = stringValue.replace("\\", "\\\\")
        OUT.append(isStringLiteral ? (STRING_PREFIX + QUOTE) : "")
          .append(stringValue ? stringValue.replace(QUOTE, QUOTE + QUOTE) : stringValue)
          .append(isStringLiteral ? QUOTE : "")
          .append(idx != columns.size() - 1 ? SEP : "")

    }
    OUT.append(")")
}

def columnList(columns, parentAlias){
    def cList = ''
    columns.eachWithIndex { column, idx ->
        def cName = parentAlias + column.name()
        def cSep = idx != columns.size() - 1 ? SEP : ""

        cList = cList + cName + cSep
    }
    columnList = cList
}

def columnsUpdatePairs(columns, parentAlias){
    def cList = ''
    columns.eachWithIndex { column, idx ->
        def cName = column.name() + " = " + parentAlias + column.name()
        def cSep = idx != columns.size() - 1 ? SEP + NEWLINE + TABS + TABS : ""

        cList += cName + cSep
    }
    columnList = cList
}



OUT.append(KW_PRINT).append("'Populating data into ").append(source)
OUT.append("'").append(NEWLINE).append(NEWLINE)

OUT.append(KW_IFOBJ).append(tempdb).append(temp).append(KW_ISNNLL).append(temp).append(";")
OUT.append(NEWLINE)

ROWS.each { row -> record(COLUMNS, row) }
OUT.append(";")
OUT.append(NEWLINE)

OUT.append(NEWLINE).append(NEWLINE)
OUT.append(KW_WITH).append(cteName).append(KW_AS).append("(").append(NEWLINE)
OUT.append(TABS).append(KW_SELECT).append(columnList(ALL_COLUMNS,'')).append(NEWLINE)
OUT.append(TABS).append(KW_FROM).append(temp).append(")").append(NEWLINE)
OUT.append(KW_MERGE).append(source).append(KW_AS).append("t").append(NEWLINE)
OUT.append(KW_USING).append(cteName).append(KW_AS).append("s").append(NEWLINE)
OUT.append(TABS).append(primaryKeys(TABLE)).append(NEWLINE)
OUT.append(KW_WHEN).append(KW_NOT).append(KW_MATCHED).append(KW_BY).append(KW_TARGET).append(KW_THEN).append(NEWLINE)
OUT.append(TABS).append(KW_INSERT).append("(").append(columnList(ALL_COLUMNS,'')).append(")").append(NEWLINE)
OUT.append(TABS).append(KW_VALUES).append("(").append(columnList(ALL_COLUMNS,'s.')).append(")").append(NEWLINE)
OUT.append(KW_WHEN).append(KW_MATCHED).append(KW_THEN).append(NEWLINE)
OUT.append(TABS).append(KW_UPDATE).append(KW_SET).append(NEWLINE)
OUT.append(TABS).append(TABS).append(columnsUpdatePairs(ALL_COLUMNS,'s.')).append(NEWLINE)
OUT.append(KW_WHEN).append(KW_NOT).append(KW_MATCHED).append(KW_BY).append(KW_SOURCE).append(KW_THEN).append(NEWLINE)
OUT.append(TABS).append(KW_DELETE).append(NEWLINE)
OUT.append(";").append(NEWLINE)
OUT.append(NEWLINE)
OUT.append(KW_IFOBJ).append(tempdb).append(temp).append(KW_ISNNLL).append(temp).append(";")
