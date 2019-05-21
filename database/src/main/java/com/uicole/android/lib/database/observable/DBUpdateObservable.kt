package com.uicole.android.lib.database.observable

import com.uicole.android.lib.database.*
import com.uicole.android.lib.database.adapter.JsonAdapter
import io.reactivex.Observable
import io.reactivex.Observer
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.reflect.full.memberProperties

class DBUpdateObservable(var requestCode: Int, var any: Any, var jsonAdapter: JsonAdapter, var tables: ArrayList<DBTableInfo>, var dbHelper: DBHelper): Observable<Int>() {

    override fun subscribeActual(observer: Observer<in Int>?) {

        var clazz = any::class
        var clazzName = clazz.qualifiedName
        if (clazzName == null) {
            observer?.onError(DBManager.ClassQualifiedNameException())
            return
        }
        run main@ {
            tables.forEach { tableInfo ->
                if (clazzName != tableInfo.clazzName) {
                    return@forEach
                }
                val db = dbHelper.writableDatabase
                var sqlStr = "update ${tableInfo.tableName} set "
                var whereStr = " where "
                var properties = any::class.memberProperties
                properties.forEach { property ->
                    var rows = tableInfo.rows
                    rows.forEach { row ->
                        if (row.key == property.name) {
                            var columnName = if (row.value.isNotBlank()) row.value else row.key
                            var columnValue = property.call(any)
                            var queryStr = ""
                            when (columnValue) {
                                null -> {
                                    queryStr += "$columnName=\'\'"
                                }
                                is Int, is Long, is Double, is Float, is Short, is Byte, is String -> {
                                    queryStr += "$columnName=\'$columnValue\'"
                                }
                                is Date -> {
                                    queryStr += "$columnName=\'${columnValue.time}\'"
                                }
                                is Boolean -> {
                                    queryStr += "$columnName=\'${if (columnValue) 1 else 0}\'"
                                }
                                is kotlin.Array<*>, is List<*> -> {
                                    queryStr += "$columnName=\'${jsonAdapter.toJSONStr(columnValue)}\'"
                                }
                                is JSONObject, is JSONArray -> {
                                    queryStr += "$columnName=\'$columnValue\'"
                                }
                                else -> {
                                    queryStr += "$columnName=\'${jsonAdapter.toJSONStr(columnValue)}\'"
                                }
                            }
                            queryStr += ","
                            if (row.primaryKey) whereStr += queryStr else sqlStr += queryStr
                        }
                    }
                }
                if (sqlStr.contains(",")) {
                    sqlStr = sqlStr.substring(0, sqlStr.length - 1)
                }
                if (whereStr.contains(",")) {
                    whereStr = whereStr.substring(0, whereStr.length - 1)
                }
                if (whereStr.contains(",")) {
                    var index = whereStr.lastIndexOf(",")
                    whereStr = "${whereStr.subSequence(0, index)} and ${whereStr.subSequence(index + 1, whereStr.length)}"
                }
                db.beginTransaction()
                db.execSQL(sqlStr + whereStr)
                db.setTransactionSuccessful()
                db.endTransaction()
                return@main
            }
        }
        observer?.onComplete()
    }
}
