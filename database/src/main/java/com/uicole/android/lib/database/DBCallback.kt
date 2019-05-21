package com.uicole.android.lib.database

import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import java.lang.reflect.Method
import kotlin.reflect.KClass
import android.database.Cursor
import com.uicole.android.lib.database.adapter.JsonAdapter
import java.util.*
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.kotlinFunction


interface DBQueryCallback {
    fun onResult(requestCode: Int, resultCode: String, msg: String?, dataArr: Any?, startId: Int = -1, endId: Int = -1, num: Int = 0)
}

interface DBCallback {
    fun onResult(requestCode: Int, resultCode: String, msg: String? = null, dataArr: Any? = null)
}

class DBObservable(var method: Method, var args: Array<out Any>?, var dbHelper: DBHelper, var jsonAdapter: JsonAdapter): Observable<Any>() {

    var rootClazz: KClass<*>? = null

    constructor(rootClazz: KClass<*>, method: Method, args: Array<out Any>?, dbHelper: DBHelper, jsonAdapter: JsonAdapter)
            : this(method, args, dbHelper, jsonAdapter) {
        this.rootClazz = rootClazz
    }

    override fun subscribeActual(observer: Observer<in Any>?) {

        var targetClazz: KClass<*>? = null
        var selections: String?
        var selectionArr: Array<Q>?
        var orderBy: String?
        var limit: Int = 50
        var queryAnno: Annotation? = null
        for (anno in method.annotations) {
            if (anno is DBQuery) {
                queryAnno = anno
                break
            }
        }
        if (queryAnno == null) {
            observer?.onError(Throwable("method have not query conditions"))
            return
        }
        queryAnno as DBQuery
        if (rootClazz != Any::class) {
            targetClazz = rootClazz
        } else if (queryAnno.tableClazz != Any::class) {
            targetClazz = queryAnno.tableClazz
        }
        if (targetClazz == Any::class) {
            observer?.onError(Throwable("class can not be Any::class"))
            return
        }
        var tableInfo = dbHelper.getTable(targetClazz!!)
        if (tableInfo == null) {
            observer?.onError(Throwable("no table pairs in memory"))
            return
        }
        selectionArr = queryAnno.selectionArr
        selections = if (queryAnno.selections.isNullOrBlank()) null else queryAnno.selections
        orderBy = if (queryAnno.orderBy.isNullOrBlank()) null else queryAnno.orderBy
        limit = queryAnno.limit
        var selectionList = ArrayList<Q>()
        var argList = ArrayList<String>()
        var argArray = getQueryArgArray(args, method)
        if (argArray != null) {
            argList.addAll(argArray)
        }
        if (selections.isNullOrBlank() && selectionArr.isNotEmpty()) {
            selectionArr.forEach { selection ->
                var express = selection.express
                if (express.contains("?") && argArray == null) {
                    if (args == null || args!!.isEmpty()) {
                        return@forEach
                    }
                    var key = express.split(" ")[0]
                    method.kotlinFunction?.valueParameters?.forEach {valueParameter ->
                        var parameterName = valueParameter.name
                        var parameterAnnos = valueParameter.annotations
                        var isExist = false
                        run annotation@ {
                            parameterAnnos.forEach { annotation ->
                                when (annotation) {
                                    is Q -> {
                                        if (key == annotation.key) {
                                            isExist = true
                                            return@annotation
                                        }
                                    }
                                }
                            }
                        }
                        if (!isExist && parameterName == key) {
                            isExist = true
                        }
                        if (isExist) {
                            selectionList.add(selection)
                            argList.add("${args!![valueParameter.index - 1]}")
                        }
                    }
                } else {
                    selectionList.add(selection)
                }
            }
        }
        var resultSeletionList = selectionList.toArray(arrayOfNulls<Q>(selectionList.size)) as Array<Q>?
        var resultArgList = argList.toArray(arrayOfNulls<String>(argList.size)) as Array<String>?

        var cursor = dbHelper.readableDatabase.query(true, tableInfo.tableName, null
                , if (selections.isNullOrBlank()) createSeletions(resultSeletionList, resultArgList) else selections, resultArgList
                , null, null, orderBy, limit.toString())
        observer?.onNext(handleQueryCursorResult(targetClazz, tableInfo, cursor))
        observer?.onComplete()
    }

    private fun createSeletions(arr: Array<out Q>?, args: Array<out String>?): String? {
        if (arr == null || arr.isEmpty() || args == null || args.isEmpty()) {
            return null
        }
        var selections: String = ""
        var index = 0
        arr.forEach {
            selections = "$selections ${it.express}${if (index <= (arr.size - 1)) "," else ""}"
            index++
        }
        if (selections.isNotBlank()) {
            selections = selections.substring(0, selections.length - 1)
        }
        return selections
    }

    private fun getQueryArgArray(args: Array<out Any>?, method: Method): Array<String>? {
        if (args == null || args.isEmpty()) {
            return null
        }
        var argList = ArrayList<String>()
        args.forEach { arg ->
            when (arg) {
                is Int, is Float, is Double, is Long, is Byte, is Short, is Char -> argList.add("$arg")
                is String -> argList.add(arg)
                is Boolean -> argList.add("${if (arg) 1 else 0}")
                is Date -> argList.add(arg.time.toString())
                else -> argList.add(jsonAdapter.toJSONStr(arg))
            }
        }
        return argList.toArray(arrayOfNulls(argList.size))
    }


    private fun handleQueryCursorResult(kClazz: KClass<*>, tableInfo: DBTableInfo, cursor: Cursor?): Result {
        var result = Result()
        if (cursor == null || cursor.count == 0) {
            result.datas = emptyArray()
            return result
        }
        var resultData = arrayOfNulls<Any>(cursor.count)
        var index = 0
        while (cursor.moveToNext()) {
            var instance = kClazz.createInstance()

            var idIndex = cursor.getColumnIndex("id")
            var idValue = cursor.getInt(idIndex)
            if (index == 0) {
                result.startId = idValue
                result.endId = idValue
            } else {
                result.endId = idValue
            }

            kClazz.memberProperties.forEach property@ {property ->
                tableInfo.rows.forEach column@ {column ->
                    if (column.key != property.name) {
                        return@column
                    }
                    var columnIndex = cursor.getColumnIndex(column.value)
                    if (columnIndex < 0) {
                        return@property
                    }
                    var value = cursor.getString(columnIndex)
                    if (value.isNullOrBlank()) {
                        return@property
                    }
                    property.isAccessible = true
                    when(column.type) {
                        "integer" -> {
                            var result = value.toIntOrNull()
                            if (result != null) {
                                property.javaField?.set(instance, result)
                            }
                        }
                        "float" -> {
                            var result = value.toFloatOrNull()
                            if (result != null) {
                                property.javaField?.set(instance, result)
                            }
                        }
                        "double" -> {
                            var result = value.toDoubleOrNull()
                            if (result != null) {
                                property.javaField?.set(instance, result)
                            }
                        }
                        "long" -> {
                            var result = value.toLongOrNull()
                            if (result != null) {
                                property.javaField?.set(instance, result)
                            }
                        }
                        "date" -> {
                            var result = value.toLongOrNull()
                            if (result != null) {
                                var date = Date(result)
                                property.javaField?.set(instance, date)
                            }
                        }
                        "boolean" -> {
                            var result: Int? = value.toIntOrNull()
                            if (result != null) {
                                property.javaField?.set(instance, (result == 1))
                            }
                        }

                        "ntext" -> {
                            var propertyType = property.javaField?.type
                            if (propertyType!!.name == "java.lang.String") {
                                property.javaField?.set(instance, value)
                            } else {
                                var datas = jsonAdapter.toInstance(value, propertyType)
                                property.javaField?.set(instance, datas)
                            }
                        }
                    }
                    property.isAccessible = false
                }
            }
            resultData[index] = instance
            index++
        }
        cursor.close()
        result.datas = resultData as Array<Any>
        result.num = resultData.size
        return result
    }


}

class DBObserver(val requestCode: Int, val callback: DBQueryCallback): Observer<Any> {
    override fun onComplete() {

    }

    override fun onSubscribe(d: Disposable) {
    }

    override fun onNext(value: Any) {
        value as Result
        callback.onResult(requestCode, DBResultCode.SUCCESS.code, null, value.datas, value.startId, value.endId, value.num)
    }

    override fun onError(e: Throwable) {
        callback.onResult(requestCode, DBResultCode.ERROR.code, e?.message, null)
    }
}

class Result {
    var datas: Array<Any>? = null
    var startId: Int = -1
    var endId: Int = -1
    var num: Int = 0
}

enum class DBResultCode(val code: String) {
    INIT("-1"), SUCCESS("00"), ERROR("01"), ERROR_CLASS_NAME("02"), ERROR_INSERT_DATA_EMPTY("03")
    , ERROR_NO_APPROPRIATE_TABLE("04")
}