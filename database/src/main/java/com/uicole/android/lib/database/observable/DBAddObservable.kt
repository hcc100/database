package com.uicole.android.lib.database.observable

import com.uicole.android.lib.database.*
import com.uicole.android.lib.database.adapter.JsonAdapter
import io.reactivex.Observable
import io.reactivex.Observer
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.full.memberProperties

class DBAddObservable(var requestCode: Int, var any: Any, var jsonAdapter: JsonAdapter, var tables: ArrayList<DBTableInfo>, var dbHelper: DBHelper): Observable<Int>() {


    override fun subscribeActual(observer: Observer<in Int>?) {

        var clazzName = any::class.qualifiedName

        if (clazzName == null) {
            observer?.onError(DBManager.ClassQualifiedNameException())
            return
        }
        var db = dbHelper.writableDatabase
        run main@ {
            tables.forEach checkTable@ { tableInfo ->
                if (clazzName != tableInfo.clazzName) {
                    return@checkTable
                }
                var param = HashMap<String, String>()
                var properties = any::class.memberProperties
                properties.forEach checkProperty@ { property ->
                    var rows = tableInfo.rows
                    rows.forEach checkRow@ { row ->
                        if (row.key == property.name) {
                            var columnName = if (row.value.isNullOrBlank()) row.key else row.value
                            var columnValue = property.call(any)
                            columnValue?: return@checkProperty
                            when (columnValue) {
                                is Int, is Long, is Double, is Float, is Short, is Byte -> {
                                    param[columnName] = columnValue.toString()
                                }
                                is String -> {
                                    param[columnName] = columnValue
                                }
                                is Boolean -> {
                                    param[columnName] = "${if (columnValue) 1 else 0}"
                                }
                                is Date -> {
                                    param[columnName] = columnValue.time.toString()
                                }
                                is kotlin.Array<*>, is List<*> -> {
                                    param[columnName] = jsonAdapter.toJSONStr(columnValue)
                                }
                                is JSONObject, is JSONArray -> {
                                    param[columnName] = columnValue.toString()
                                }
                                else -> {
                                    param[columnName] = jsonAdapter.toJSONStr(columnValue)
                                }
                            }
                        }
                    }
                }
                if (param.isEmpty()) {
                    return@main
                }
                var keyStr = ""
                var dotStr = ""
                var objArr = arrayOfNulls<Any>(param.size)
                var index = 0

                param.entries.forEach {entry ->
                    if (index != 0) {
                        keyStr += ","
                        dotStr += ","
                    }
                    keyStr += entry.key
                    dotStr += "?"
                    objArr[index] = entry.value
                    index++
                }
                db.beginTransaction()
                var sql = "insert into ${tableInfo.tableName}($keyStr) values($dotStr)"
                db.execSQL(sql, objArr)
                db.setTransactionSuccessful()
                db.endTransaction()
                return@main
            }
        }
        observer?.onNext(requestCode)
        observer?.onComplete()
    }
}
