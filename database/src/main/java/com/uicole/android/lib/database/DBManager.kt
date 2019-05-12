package com.uicole.android.lib.database

import android.content.Context
import android.os.Handler
import android.os.Message
import com.uicole.android.lib.database.adapter.JsonAdapter
import com.uicole.android.lib.database.utils.FileUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField


/**
 * database manager
 *
 * note: column is can't drop by manager, because the direct operate is not admit by android system, and the other way is slow.
 *
 * we could create the other table, or keep the property null to make the manager run well
 *
 *
 * clazzArr: the array of kclass, which need create table by manager. if the table in the database is not in the clazzArr, will be droped from database
 *
 */
class DBManager(context: Context, clazzArr: Array<KClass<*>>?, var jsonAdapter: JsonAdapter, var executorService: ExecutorService?, var initListener: OnInitDatabaseLisener?): OnDatabaseUpdateListener {

    var dbHelper: DBHelper? = null
    var handler = DBHandler()
    var dbInfo: DBInfo? = null
    var tables = ArrayList<DBTableInfo>()
    lateinit var dbFile: File

    init {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor()
        }
        executorService!!.execute {
            initTable(clazzArr)
            if (tables.isEmpty()) {
                initListener?.onError(DatabaseNotInitException())
                return@execute
            }
            dbFile = File(context.externalCacheDir, "db_file")
            var dbContentStr = FileUtils.readFile(dbFile.absolutePath)
            if (!dbContentStr.isNullOrBlank()) {
                var dbInfo = jsonAdapter.toInstance(dbContentStr!!, DBInfo::class.java)
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
            initListener?.onInitSuccess()
        }
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

    override fun onTableUpdateSusscess() {
        FileUtils.writeFile(dbFile.absolutePath, jsonAdapter.toJSONStr(dbInfo!!))
    }

    override fun onDatabaseInitSuccess() {
        initListener?.onInitSuccess()
    }

    override fun onDBtabaseInitError(e: Exception) {
        initListener?.onError(e)
    }

    /**
     * delete object from table
     * any.property must annotate:primary key
     */
    fun delete(requestCode: Int, any: Any, callback: DBCallback?) {
        executorService!!.execute {
            var clazz = any::class
            var clazzName = clazz.qualifiedName
            var result = DBResult(requestCode, DBResultCode.INIT.code, null, null, callback)
            if (clazzName == null) {
                result.resultCode = DBResultCode.ERROR_CLASS_NAME.code
                result.msg = "class.qualifiedName is error"
            } else {
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
                        return@main
                    }
                }
                result.resultCode = DBResultCode.SUCCESS.code
            }
            if (callback != null) {
                handler.obtainMessage(0, result).sendToTarget()
            }
        }
    }

    /**
     * update object in the table
     * any.property must annotate:primary key
     */
    fun update(requestCode: Int, any: Any, callback: DBCallback?) {
        executorService!!.execute {
            var clazz = any::class
            var clazzName = clazz.qualifiedName
            var result = DBResult(requestCode, DBResultCode.INIT.code, null, null, callback)
            if (clazzName == null) {
                result.resultCode = DBResultCode.ERROR_CLASS_NAME.code
                result.msg = "class.qualifiedName is error"
            } else {
                dbInfo!!.tables.forEach { tableInfo ->
                    if (clazzName != tableInfo.clazzName) {
                        return@forEach
                    }
                    val db = dbHelper!!.writableDatabase
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
                }
                result.resultCode = DBResultCode.SUCCESS.code
            }
            if (callback != null) {
                handler.obtainMessage(0, result).sendToTarget()
            }
        }
    }

    fun <T> create(kclazz: Class<T>): T? {
        return Proxy.newProxyInstance(DBManager::class.java.classLoader, arrayOf(kclazz), object: InvocationHandler {
            override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                if (method == null || dbInfo == null) {
                    return null
                }
                return DBObservable(method, args, dbHelper!!, jsonAdapter)
            }
        }) as T
    }

    /**
     * clazz must have empty constructor
     */
    fun query(requestCode: Int, clazz: KClass<*>, selections: String?, selectionArgs: Array<String>?, orderBy: String? = null, limit: String?= null, callback: DBCallback?) {
        executorService!!.execute {
            var clazzName = clazz.qualifiedName
            var result = DBResult(requestCode, DBResultCode.INIT.code, null, null, callback)
            if (clazzName == null) {
                result.resultCode = DBResultCode.ERROR_CLASS_NAME.code
                result.msg = "class.qualifiedName is error"
            } else {
                tables.forEach table@ { tableInfo ->
                    if (clazzName != tableInfo.clazzName) {
                        return@table
                    }
                    var db = dbHelper!!.writableDatabase
                    var cursor = db.query(true, tableInfo.tableName, null, selections, selectionArgs, null, null, orderBy, limit)
                    var resultData = arrayOfNulls<Any>(cursor.count)
                    var index = 0
                    if (cursor != null && cursor.count > 0) {
                        while(cursor.moveToNext()) {
                            var instance = clazz.createInstance()
                            clazz.memberProperties.forEach property@ {property->
                                tableInfo.rows.forEach column@ {column ->
                                    if (column.key != property.name) {
                                        return@column
                                    }
                                    var index = cursor.getColumnIndex(if (column.value.isNullOrBlank()) column.key else column.value)
                                    if (index < 0) {
                                        return@property
                                    }
                                    var value = cursor.getString(index)
                                    if (value.isNullOrBlank()) {
                                        return@property
                                    }
                                    property.isAccessible = true
                                    try {
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
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    property.isAccessible = false
                                    return@property
                                }
                            }
                            resultData[index] = instance
                            index++
                        }
                    }
                    cursor?.close()
                    result.resultCode = DBResultCode.SUCCESS.code
                    result.data = resultData
                }
            }
            if (callback != null) {
                handler.obtainMessage(0, result).sendToTarget()
            }
        }
    }

    fun add(requestCode: Int, any: Any) {
        add(requestCode, any, null)
    }

    /**
     * add object to the table
     * requestCode request code user defined
     * any object
     */
    fun add(requestCode: Int, any: Any, callback: DBCallback?) {
        executorService!!.execute{
            var clazzName = any::class.qualifiedName
            var result = DBResult(requestCode, DBResultCode.INIT.code, null, null, callback)
            if (clazzName == null) {
                result.resultCode = DBResultCode.ERROR_CLASS_NAME.code
                result.msg = "class.qualifiedName is error"
            } else {
                var db = dbHelper!!.writableDatabase
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
                            result.resultCode = DBResultCode.ERROR_INSERT_DATA_EMPTY.code
                            result.msg = "data is empty"
                            return@main
                        }
                        var keyStr = ""
                        var dotStr = ""
                        var objArr = arrayOfNulls<Any>(param.size)
                        var index = 0
                        param.forEach { t, u ->
                            if (index != 0) {
                                keyStr += ","
                                dotStr += ","
                            }
                            keyStr += t
                            dotStr += "?"
                            objArr[index] = u
                            index++
                        }
                        db.beginTransaction()
                        var sql = "insert into ${tableInfo.tableName}($keyStr) values($dotStr)"
                        db.execSQL(sql, objArr)
                        db.setTransactionSuccessful()
                        db.endTransaction()
                        result.resultCode = DBResultCode.SUCCESS.code
                        return@main
                    }
                }
            }
            if (result.resultCode == DBResultCode.INIT.code) {
                result.resultCode = DBResultCode.ERROR_NO_APPROPRIATE_TABLE.code
                result.msg = "no appropriate table"
            }
            if (callback != null) {
                handler.obtainMessage(0, result).sendToTarget()
            }
        }
    }


    private fun initTable(clazzArr: Array<KClass<*>>?) {
        clazzArr?: return
        if (clazzArr.isNotEmpty()) {
            clazzArr.forEach {
                createTable(it)
            }
        }
    }

    private fun getTable(clazz: KClass<*>): DBTableInfo? {
        tables.forEach {
            if (it.clazzName == clazz.qualifiedName) {
                return it
            }
        }
        return null
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


    class DBResult(var requestCode: Int, var resultCode: String, var msg: String?, var data: Any?, var callback: DBCallback?)

    class DBHandler: Handler() {
        override fun handleMessage(msg: Message?) {
            super.handleMessage(msg)
            var result = msg?.obj
            result?: return
            when (result) {
                is DBResult -> {
                    result.callback?.onResult(result.requestCode, result.resultCode, result.msg, result.data)
                }
            }
        }
    }

    class DatabaseNotInitException: Exception("database is not init complete")

    interface OnInitDatabaseLisener {
        fun onInitSuccess()
        fun onError(e: Exception)
    }


}