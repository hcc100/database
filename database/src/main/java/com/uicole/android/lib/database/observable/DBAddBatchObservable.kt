package com.uicole.android.lib.database.observable

import com.uicole.android.lib.database.*
import com.uicole.android.lib.database.adapter.JsonAdapter
import io.reactivex.Observable
import io.reactivex.Observer
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.reflect.full.memberProperties

class DBAddBatchObservable(var requestCode: Int, var any: Any, var jsonAdapter: JsonAdapter, var tables: ArrayList<DBTableInfo>, var dbHelper: DBHelper): Observable<Any?>() {

    override fun subscribeActual(observer: Observer<in Any?>?) {
        var list = ArrayList<Any>()
        when (any) {
            is List<*>, is Set<*> -> {
                list.addAll(any as Collection<Any>)
            }
            is Array<*> -> {
                list.addAll(any as Array<Any>)
            }
        }
        var clazzName: String? = null

        if (list.isNotEmpty()) {
            run checkClass@{
                list.forEach { item ->
                    var tempName = item::class.qualifiedName
                    if (clazzName.isNullOrBlank()) {
                        clazzName = tempName
                    } else if (clazzName != tempName) {
                        clazzName = null
                        return@checkClass
                    }
                }
            }
        }

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
                var params = arrayOfNulls<HashMap<String, String>>(list.size)
                var keys = arrayOfNulls<Any>(list.size)
                var properties = (list[0])::class.memberProperties
                var paramIndex: Int = -1
                properties.forEach checkProperty@ { property ->
                    var rows = tableInfo.rows
                    rows.forEach checkRow@ { row ->
                        if (row.key == property.name) {
                            var columnName = if (row.value.isNullOrBlank()) row.key else row.value
                            paramIndex = -1
                            list.forEach checkItem@ { item ->
                                ++paramIndex
                                if (params[paramIndex] == null) {
                                    params[paramIndex] = HashMap()
                                }
                                var param = params[paramIndex]
                                var columnValue = property.call(item)
                                if (row.primaryKey) {
                                    keys[paramIndex] = columnValue
                                }
                                columnValue?: return@checkItem
                                when (columnValue) {
                                    is Int, is Long, is Double, is Float, is Short, is Byte -> {
                                        param!![columnName] = columnValue.toString()
                                    }
                                    is String -> {
                                        param!![columnName] = columnValue
                                    }
                                    is Boolean -> {
                                        param!![columnName] = "${if (columnValue) 1 else 0}"
                                    }
                                    is Date -> {
                                        param!![columnName] = columnValue.time.toString()
                                    }
                                    is kotlin.Array<*>, is List<*> -> {
                                        param!![columnName] = jsonAdapter.toJSONStr(columnValue)
                                    }
                                    is JSONObject, is JSONArray -> {
                                        param!![columnName] = columnValue.toString()
                                    }
                                    else -> {
                                        param!![columnName] = jsonAdapter.toJSONStr(columnValue)
                                    }
                                }
                            }
                        }
                    }
                }
                for (i in 0..(params.size - 1)) {
                    var param = params[i]
                    param?: continue
                    if (param.isEmpty()) {
                        continue
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
                    observer?.onNext(keys[i])
                }
                return@main
            }
        }
        observer?.onComplete()
    }
}
