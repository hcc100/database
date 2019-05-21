package com.uicole.android.lib.database

import android.content.Context
import android.os.Handler
import android.os.Message
import com.uicole.android.lib.database.adapter.JsonAdapter
import com.uicole.android.lib.database.observable.DBAddObservable
import com.uicole.android.lib.database.observable.DBDeleteObservable
import com.uicole.android.lib.database.observable.DBInitObservable
import com.uicole.android.lib.database.observable.DBUpdateObservable
import com.uicole.android.lib.database.observer.DBInitObserver
import com.uicole.android.lib.database.observer.DBUpdateObserver
import com.uicole.android.lib.database.utils.FileUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
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
import kotlin.reflect.full.memberProperties


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
class DBManager(context: Context, clazzArr: Array<KClass<*>>?, var jsonAdapter: JsonAdapter, var executorService: ExecutorService?, var callback: OnInitDatabaseCallback?): OnDatabaseUpdateListener {

    var dbHelper: DBHelper? = null
    var dbInfo: DBInfo? = null
    var tables = ArrayList<DBTableInfo>()
    var dbFile: File

    init {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor()
        }
        dbFile = File(context.externalCacheDir, "db_file")
        DBInitObservable(context, dbFile, clazzArr, jsonAdapter)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(DBInitObserver(object : OnInitDatabaseLisener {
                override fun onInitSuccess(tables: List<DBTableInfo>, dbInfo: DBInfo?, dbHelper: DBHelper?) {
                    this@DBManager.tables.addAll(tables)
                    this@DBManager.dbInfo = dbInfo
                    this@DBManager.dbHelper = dbHelper
                    callback?.onInitSuccess()
                }

                override fun onError(e: Throwable?) {
                    callback?.onError(e)
                }
            }))
    }

    override fun onTableUpdateSusscess() {
        FileUtils.writeFile(dbFile.absolutePath, jsonAdapter.toJSONStr(dbInfo!!))
    }

    override fun onDBtabaseInitError(e: Exception) {
        callback?.onError(e)
    }

    /**
     * delete object from table
     * any.property must annotate:primary key
     */
    fun delete(requestCode: Int, any: Any, callback: DBCallback?) {
        DBDeleteObservable(requestCode, any, jsonAdapter, tables, dbHelper!!)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({t ->
                callback?.onResult(t, DBResultCode.SUCCESS.code)
            }, {e ->
                callback?.onResult(requestCode, DBResultCode.ERROR.code, e.message)
            })
    }

    /**
     * update object in the table
     * any.property must annotate:primary key
     */
    fun update(requestCode: Int, any: Any, callback: DBCallback?) {
        DBUpdateObservable(requestCode, any, jsonAdapter, tables, dbHelper!!)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({t ->
                callback?.onResult(t, DBResultCode.SUCCESS.code)
            }, {e ->
                callback?.onResult(requestCode, DBResultCode.ERROR.code, e.message)
            })
    }

    fun <T> create(kclazz: Class<T>): T? {
        return Proxy.newProxyInstance(DBManager::class.java.classLoader, arrayOf(kclazz), object: InvocationHandler {
            override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                if (method == null || dbInfo == null) {
                    return null
                }
                var annos = kclazz.annotations
                if (annos != null && annos.isNotEmpty()) {
                    annos.forEach { anno ->
                        when (anno) {
                            is DBQuery -> {
                                if (anno.tableClazz != Any::class) {
                                    return DBObservable(anno.tableClazz, method, args, dbHelper!!, jsonAdapter)
                                }
                            }
                        }
                    }
                }
                return DBObservable(method, args, dbHelper!!, jsonAdapter)
            }
        }) as T
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
        DBAddObservable(requestCode, any, jsonAdapter, tables, dbHelper!!)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({t ->
                callback?.onResult(t, DBResultCode.SUCCESS.code)
            }, {e ->
                callback?.onResult(requestCode, DBResultCode.ERROR.code, e.message)
            })
    }


    class DBResult(var requestCode: Int, var resultCode: String, var msg: String?, var data: Any?, var callback: DBCallback?)



    class DatabaseNotInitException: Exception("database is not init complete")
    class ClassQualifiedNameException: Exception("class qualifiedName is null")

    interface OnInitDatabaseCallback {
        fun onInitSuccess()
        fun onError(e: Throwable?)
    }

    interface OnInitDatabaseLisener {
        fun onInitSuccess(tables: List<DBTableInfo>, dbInfo: DBInfo?, dbHelper: DBHelper?)
        fun onError(e: Throwable?)
    }


}