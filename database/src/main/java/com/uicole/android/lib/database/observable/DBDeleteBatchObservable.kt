package com.uicole.android.lib.database.observable

import com.uicole.android.lib.database.*
import com.uicole.android.lib.database.adapter.JsonAdapter
import io.reactivex.Observable
import io.reactivex.Observer
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.reflect.full.memberProperties

class DBDeleteBatchObservable(var requestCode: Int, var any: Any, var jsonAdapter: JsonAdapter, var tables: ArrayList<DBTableInfo>, var dbHelper: DBHelper): Observable<Int>() {

    override fun subscribeActual(observer: Observer<in Int>?) {

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
            tables.forEach { tableInfo ->
                if (clazzName != tableInfo.clazzName) {
                    return@forEach
                }
                var sqlStr = "delete from ${tableInfo.tableName} where "
                var queryList = arrayListOf<String>()
                var properties = list[0]::class.memberProperties
                var itemIndex: Int = 0
                properties.forEach checkProperty@ { property ->
                    var rows = tableInfo.rows
                    rows.forEach { row ->
                        if (row.key == property.name && row.primaryKey) {
                            var columnName = row.value
                            itemIndex = 0
                            list.forEach { item ->
                                var columnValue = property.call(item)
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
                                if (queryList.size <= itemIndex) {
                                    queryList.add(queryStr)
                                } else {
                                    queryList[itemIndex] = "${queryList[itemIndex]} and $queryStr"
                                }
                                itemIndex++
                            }
                            return@checkProperty
                        }
                    }
                }
                var querySet = HashSet<String>()
                querySet.addAll(queryList)
                for (i in queryList.indices) {
                    sqlStr = when (i) {
                        0 -> "$sqlStr(${queryList[i]})"
                        else -> "$sqlStr or (${queryList[i]})"
                    }
                }
                db.beginTransaction()
                db.execSQL(sqlStr)
                db.setTransactionSuccessful()
                db.endTransaction()
                return@main
            }
        }
        observer?.onComplete()


    }
}
