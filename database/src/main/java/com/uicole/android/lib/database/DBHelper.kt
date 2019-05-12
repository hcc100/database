package com.uicole.android.lib.database

import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.reflect.KClass


class DBHelper(context: Context, var dbInfo: DBInfo, var tableInfos: List<DBTableInfo>?, var onDBUpdateListener: OnDatabaseUpdateListener?): SQLiteOpenHelper(context, "uicole", null, dbInfo.version, null) {


    override fun onCreate(db: SQLiteDatabase?) {
        try {
            tableInfos?.forEach {
                if (createTable(db, it)) {
                    dbInfo.tables.add(it)
                }
            }
            onDBUpdateListener?.onTableUpdateSusscess()
        } catch (e: Exception) {
            e.printStackTrace()
            onDBUpdateListener?.onDBtabaseInitError(e)
        }
    }


    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        try {
            var oldIterator = dbInfo.tables.iterator()
            while (oldIterator.hasNext()) {
                var oldTable = oldIterator.next()
                var isExist = false
                tableInfos?.forEach{ newTable ->
                    if (oldTable.tableName == newTable.tableName) {
                        isExist = true
                        return@forEach
                    }
                }
                if (!isExist && dropTable(db, oldTable)) {// if old table is not exist in the new tables, remove table from database
                    oldIterator.remove()
                    onDBUpdateListener?.onTableUpdateSusscess()
                }
            }

            tableInfos?.forEach goNewTable@ { newTableInfo ->
                dbInfo.tables.forEach { oldTableInfo ->
                    if (oldTableInfo.tableName == newTableInfo.tableName) {
                        mergeTableInfo(db, oldTableInfo, newTableInfo)
                        return@goNewTable
                    }
                }
                createTable(db, newTableInfo) //if new table is not exist in the old tables, create new table
                dbInfo.tables.add(newTableInfo)
                onDBUpdateListener?.onTableUpdateSusscess()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onDBUpdateListener?.onDBtabaseInitError(e)
        }
    }

    private fun mergeTableInfo(db: SQLiteDatabase?, oldTableInfo: DBTableInfo, newTableInfo: DBTableInfo) {
        newTableInfo.rows.forEach goNextColumn@ { newColumnInfo ->
            oldTableInfo.rows.forEach { oldColumnInfo ->
                if (oldColumnInfo.value == newColumnInfo.value) {
                    updateColumn(db, oldTableInfo, oldColumnInfo, newColumnInfo)
                    return@goNextColumn
                }
            }
            addColumn(db, oldTableInfo, newColumnInfo) //if column is not exist in the table, create new column
            oldTableInfo.rows.add(newColumnInfo)
            onDBUpdateListener?.onTableUpdateSusscess()
        }
    }

    /**
     * update table column
     */
    private fun updateColumn(db: SQLiteDatabase?, tableInfo: DBTableInfo, oldColumnInfo: DBColumnInfo, newColumnInfo: DBColumnInfo) {
        if (oldColumnInfo.type != newColumnInfo.type) {
            if (oldColumnInfo.type == "ntext" || newColumnInfo.type == "ntext") {
                updateColumn(db, tableInfo, newColumnInfo)
            }
            oldColumnInfo.clone(newColumnInfo)
            onDBUpdateListener?.onTableUpdateSusscess()
        }
    }

    /**
     * update column
     */
    private fun updateColumn(db: SQLiteDatabase?, tableInfo: DBTableInfo, columnInfo: DBColumnInfo): Boolean {
        return execSql(db, "alter table ${tableInfo.tableName} alter column ${columnInfo.value} ${if (columnInfo.type == "ntext") "ntext" else "varchar"}")
    }

    /**
     * add column
     */
    private fun addColumn(db: SQLiteDatabase?, tableInfo: DBTableInfo, columnInfo: DBColumnInfo): Boolean {
        return execSql(db, "alter table ${tableInfo.tableName} add column ${columnInfo.value} ${if (columnInfo.type == "ntext") columnInfo.type else "varchar"}")
    }

    /**
     * delete table from database
     */
    private fun dropTable(db: SQLiteDatabase?, tableInfo: DBTableInfo): Boolean {
        return execSql(db, "drop table if exists ${tableInfo.tableName}")
    }

    /**
     * return table sql
     */
    fun createSql(tableInfo: DBTableInfo): String? {
        if (tableInfo.rows.isNotEmpty()) {
            var sql = "create table if not exists ${tableInfo.tableName} (id integer primary key autoincrement"
            tableInfo.rows.forEach { row ->
                sql += ", ${if (row.value.isBlank()) row.key else row.value} ${if (row.type == "ntext") row.type else "varchar"}"
            }
            sql += ")"
            return sql
        }
        return null
    }

    /**
     * create table
     */
    fun createTable(db: SQLiteDatabase?, tableInfo: DBTableInfo): Boolean {
        var sql = createSql(tableInfo)
        return execSql(db, sql)
    }

    private fun execSql(db: SQLiteDatabase?, sql: String?): Boolean {
        sql?: return false
        db?: return false
        try {
            db.execSQL(sql)
        } catch (e: SQLException) {
            return false
        }
        return true
    }

    fun getTable(clazz: KClass<*>?): DBTableInfo? {
        clazz?: return null
        var tableClazzName = clazz.qualifiedName
        if (tableClazzName.isNullOrBlank()) return null
        tableInfos?.forEach { table ->
            if (tableClazzName == table.clazzName) {
                return table
            }
        }
        return null
    }

}


interface OnDatabaseUpdateListener {
    fun onTableUpdateSusscess()
    fun onDatabaseInitSuccess()
    fun onDBtabaseInitError(e: Exception)

}


class DBInfo {
    var version: Int = 1
    var tables = ArrayList<DBTableInfo>()
}

class DBTableInfo {
    lateinit var clazzName: String
    lateinit var tableName: String
    var rows = ArrayList<DBColumnInfo>()
}

class DBColumnInfo {
    var key: String = ""
    var value: String = ""
    var type: String? = null
    var primaryKey: Boolean = false
    var default: String? = null

    fun clone(info: DBColumnInfo) {
        key = info.key
        value = info.value
        type = info.type
        primaryKey = info.primaryKey
        default = info.default
    }
}