package com.uicole.android.lib.database.observable

import com.uicole.android.lib.database.*
import com.uicole.android.lib.database.adapter.JsonAdapter
import io.reactivex.Observable
import io.reactivex.Observer
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.reflect.full.memberProperties

class DBDeleteObservable(var requestCode: Int, var any: Any, var jsonAdapter: JsonAdapter, var tables: ArrayList<DBTableInfo>, var dbHelper: DBHelper): Observable<Int>() {

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
                var db = dbHelper!!.writableDatabase
                var sqlStr = "delete from ${tableInfo.tableName} where "
                var properties = any::class.memberProperties
                properties.forEach { property ->
                    var rows = tableInfo.rows
                    rows.forEach { row ->
                        if (row.key == property.name && row.primaryKey) {
                            var columnName = row.value
                            var columnValue = property.call(any)
                            var queryStr = ""
                            when (columnValue) {
                                null -> {
                                    queryStr += "$columnName=\'\'"
                                }
                                is Int, is Long, is Double, is Float, is Short, is Byte, is String -> {
                                    queryStr += "$columnName=\'$columnValue\'"
                                }
                                is Boolean -> {
                                    queryStr += "$columnName=\'${if (columnValue) 1 else 0}\'"
                                }
                                is Date -> {
                                    queryStr += "$columnName=\'${columnValue.time}\'"
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
                            sqlStr += queryStr
                        }
                    }
                }
                if (sqlStr.contains(",")) {
                    sqlStr = sqlStr.substring(0, sqlStr.length - 1)
                }
                if (sqlStr.contains(",")) {
                    var index = sqlStr.lastIndexOf(",")
                    sqlStr = "${sqlStr.subSequence(0, index)} and ${sqlStr.subSequence(index + 1, sqlStr.length)}"
                }
                db.beginTransaction()
                db.execSQL(sqlStr)
                db.setTransactionSuccessful()
                db.endTransaction()
                observer?.onNext(requestCode)
                return@main
            }
        }
        observer?.onComplete()


    }
}
