package com.uicole.android.lib.database

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.alibaba.fastjson.JSON
import com.uicole.android.lib.database.adapter.JsonAdapter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var manager: DBManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        manager = DBManager(this, arrayOf(Ves::class), IJsonAdapter(), null, object : DBManager.OnInitDatabaseLisener {
            override fun onError(e: Exception) {
            }

            override fun onInitSuccess() {
                var vesss = Ves()
                vesss.finll["hs"] = "sfdf"
                vesss.finll["hsa"] = "sfdfsd"

                vesss.vesss.add(Ve())
                manager.add(100, vesss, object : DBCallback {
                    override fun onResult(requestCode: Int, resultCode: String, msg: String?, dataArr: Any?) {
                        var vesDao = manager.create(VesDao::class.java)
                        var observable = vesDao!!.queryAll(0)
                        observable.subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(DBObserver(1, object : DBCallback {
                                override fun onResult(requestCode: Int, resultCode: String, msg: String?, dataArr: Any?) {
                                    println("$requestCode")
                                }
                            }))
                    }
                })


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
}



interface VesDao {
    @DBQuery(tableClazz = Ves::class, selectionArr = [Q(express = "i = ?")])
    fun queryAll(i: Int): io.reactivex.Observable<Array<Ves>>
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
    @DBA(primaryKey = true) var i: Int = 0
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