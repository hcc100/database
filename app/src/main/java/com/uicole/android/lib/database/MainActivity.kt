package com.uicole.android.lib.database

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.alibaba.fastjson.JSON
import com.uicole.android.lib.database.adapter.JsonAdapter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.lang.reflect.Type
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    lateinit var manager: DBManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        manager = DBManager(this, arrayOf(Ves::class), IJsonAdapter(), null, object : DBManager.OnInitDatabaseCallback {

            override fun onInitSuccess() {
                var vesss1 = Ves()
                var vesss2 = Ves()
                vesss2.i = 2
                vesss2.j = 3


                manager.add(arrayListOf(vesss1, vesss2), object : DBCallback {
                    override fun onResult(key: Any?, resultCode: String, msg: String?, dataArr: Any?) {
                        if (resultCode == DBResultCode.STEP_SUCCESS.code) {
                        } else {
                            var vesDao = manager.create(VesDao::class.java)
                            var observable = vesDao!!.query()
                            observable.subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(DBObserver(1, object : DBQueryCallback {

                                    override fun onResult(
                                        requestCode: Int,
                                        resultCode: String,
                                        msg: String?,
                                        dataArr: Any?,
                                        startId: Int,
                                        endId: Int,
                                        num: Int
                                    ) {
                                        println("$requestCode")
                                        dataArr as Array<Any>
                                        (dataArr[0] as Ves).tt = "40"
                                        manager.delete(arrayListOf(vesss1, vesss2), object : DBCallback {
                                            override fun onResult(
                                                primary: Any?,
                                                resultCode: String,
                                                msg: String?,
                                                dataArr: Any?
                                            ) {
                                                println(primary)
                                            }
                                        })
                                    }
                                }))
                        }
                    }
                }, 100)
            }

            override fun onError(e: Throwable?) {
            }

        })
    }
}




class IJsonAdapter: JsonAdapter {
    override fun toJSONStr(any: Any): String {
        return JSON.toJSONString(any)
    }

    override fun <T> toInstance(text: String, clazz: Class<T>): T? {
        return JSON.parseObject(text, clazz)
    }

    override fun <T> toInstanceList(text: String, clazz: Class<T>): List<T>? {
        return JSON.parseArray(text, clazz)
    }


    override fun toInstance(text: String, clazz: Type): Any? {
        return JSON.parseObject(text, clazz)
    }

}


@DBQuery(tableClazz = Ves::class)
interface VesDao {
    @DBQuery
    fun query(): io.reactivex.Observable<Array<Ves>>
    @DBQuery(selectionArr = [Q(express = "date >= ?")])
    fun queryAll(date: Date): io.reactivex.Observable<Array<Ves>>
    @DBQuery(selectionArr = [Q(express = "i = ?")])
    fun queryTt(tt: String): io.reactivex.Observable<Array<Ves>>
}

@DBA(table = "ves", isInherit = false)
class Ves(@DBA(row = "s") var ss: String = "1d", @DBA(row = "sd", isColumn = false) var sss: String = "cd") {
    @DBA(row = "sdx", isColumn = true) var tt: String = "43"
    var dd = true
    var kk: String? = null
    var arr: Array<String> = arrayOf("ksf")
    var vesss = ArrayList<Ve>()
    var v: Ve?= Ve()
    var b:Boolean = false
    @DBA(primaryKey = true) var i: Int = 1
    @DBA(primaryKey = true) var j: Int = 2
    var ks: Float = 64f
    var ds: Double = 5.0
    var date = Date()
    var newDate = Date()
    var finll = HashMap<String, String>()
}

class Ve {
    var s = 50
    var info = true
}