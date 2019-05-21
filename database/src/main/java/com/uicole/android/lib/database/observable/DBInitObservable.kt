package com.uicole.android.lib.database.observable

import android.content.Context
import com.uicole.android.lib.database.*
import com.uicole.android.lib.database.adapter.JsonAdapter
import com.uicole.android.lib.database.utils.FileUtils
import io.reactivex.Observable
import io.reactivex.Observer
import java.io.File
import java.util.ArrayList
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties

class DBInitObservable(var context: Context, var dbFile: File, var clazzArr: Array<KClass<*>>?, var jsonAdapter: JsonAdapter): Observable<Any>(), OnDatabaseUpdateListener {

    var tables = ArrayList<DBTableInfo>()
    var dbInfo: DBInfo? = null
    var dbHelper: DBHelper? = null
    var observer: Observer<in Any>? = null

    override fun subscribeActual(observer: Observer<in Any>?) {
        observer?: return
        this.observer = observer
        initTable(clazzArr)
        if (tables.isEmpty()) {
            observer.onError(DBManager.DatabaseNotInitException())
            return
        }
        var dbContentStr = FileUtils.readFile(dbFile.absolutePath)
        if (!dbContentStr.isNullOrBlank()) {
            var dbInfo = jsonAdapter.toInstance(dbContentStr, DBInfo::class.java)
            if (dbInfo != null) {
                this.dbInfo = dbInfo
            }
        }
        if (dbInfo == null) {
            dbInfo = DBInfo()
        } else {
            var isSame = false
            run checkNewTable@ {
                tables.forEach {tableInfo ->
                    run checkName@ {
                        dbInfo!!.tables.forEach {currentTableInfo ->
                            if (tableInfo.tableName == currentTableInfo.tableName) {
                                isSame = checkTableIsSame(tableInfo, currentTableInfo)
                                return@checkName
                            }
                        }
                    }
                    if (!isSame) {
                        return@checkNewTable
                    }
                }
            }
            if (!isSame) {
                dbInfo!!.version += 1
                onTableUpdateSusscess()
            }
        }
        dbHelper = DBHelper(context, dbInfo!!, tables, this)
        observer.onNext(mapOf("dbinfo" to dbInfo, "tables" to tables, "dbhelper" to dbHelper))
        observer.onComplete()
    }

    override fun onTableUpdateSusscess() {
        FileUtils.writeFile(dbFile.absolutePath, jsonAdapter.toJSONStr(dbInfo!!))
    }

    override fun onDBtabaseInitError(e: Exception) {
        observer?.onError(e)
    }

    private fun checkTableIsSame(table1: DBTableInfo, table2: DBTableInfo): Boolean {
        if (table1.rows.size != table2.rows.size) {
            return false
        }
        table1.rows.forEach column1@ { columnInfo1 ->
            table2.rows.forEach column2@ { columnInfo2 ->
                if (columnInfo1.value == columnInfo2.value) {
                    if (columnInfo1.type != columnInfo2.type || columnInfo1.key != columnInfo2.key || columnInfo1.primaryKey != columnInfo2.primaryKey) {
                        return false
                    } else {
                        return@column1
                    }
                }
            }
        }
        return true
    }


    private fun initTable(clazzArr: Array<KClass<*>>?) {
        clazzArr?: return
        if (clazzArr.isNotEmpty()) {
            clazzArr.forEach {
                createTable(it)
            }
        }
    }


    private fun createTable(clazz: KClass<*>) {
        var tableName = clazz.simpleName
        var isInherit = false
        if (clazz.annotations.isNotEmpty()) {
            clazz.annotations.forEach {anno ->
                if (anno.annotationClass.qualifiedName == DBA::class.qualifiedName) {
                    anno as DBA
                    if (!anno.table.isNullOrBlank()) {
                        tableName = anno.table
                    }
                    isInherit = anno.isInherit
                }
            }
        }
        if (clazz.qualifiedName.isNullOrBlank() || tableName.isNullOrBlank()) {
            return
        }
        var tableInfo = DBTableInfo()
        tableInfo.clazzName = clazz.qualifiedName!!
        tableInfo.tableName = tableName!!

        var properties = if (isInherit) clazz.memberProperties else clazz.declaredMemberProperties
        if (properties.isNotEmpty()) {
            properties.forEach {property ->
                var returnType = property.returnType.classifier?.toString()
                if (returnType.isNullOrBlank() || returnType!!.contains("Function")) {
                    return@forEach
                }
                var columnInfo = DBColumnInfo()
                var returnTypeArr = returnType.split(" ")
                if (returnTypeArr.size <= 1) {
                    return@forEach
                }

                var columnType: String
                when (returnTypeArr[1]) {
                    "kotlin.Byte", "kotlin.Int", "kotlin.Short" -> {
                        columnType = "integer"
                    }
                    "kotlin.Float" -> {
                        columnType = "float"
                    }
                    "kotlin.Double" -> {
                        columnType = "double"
                    }
                    "kotlin.Long" -> {
                        columnType = "long"
                    }
                    "kotlin.Boolean" -> {
                        columnType = "boolean"
                    }
                    "java.util.Date" -> {
                        columnType = "date"
                    }
                    else -> {
                        columnType = "ntext"
                    }
                }
                columnInfo.key = property.name
                columnInfo.type = columnType
                var isColumn = true
                property.annotations.forEach {anno ->
                    if (anno.annotationClass.qualifiedName == DBA::class.qualifiedName) {
                        anno as DBA
                        if (anno.row.isNotBlank()) {
                            columnInfo.value = anno.row
                            isColumn = anno.isColumn
                        }
                        columnInfo.primaryKey = anno.primaryKey
                    }
                }
                if (isColumn && columnInfo.key.isNotBlank()) {
                    if (columnInfo.value.isNullOrBlank()) {
                        columnInfo.value = columnInfo.key
                    }
                    tableInfo.rows.add(columnInfo)
                }
            }
        }
        tables.add(tableInfo)
    }

}