package com.uicole.android.lib.database.observer

import com.uicole.android.lib.database.DBHelper
import com.uicole.android.lib.database.DBInfo
import com.uicole.android.lib.database.DBManager
import com.uicole.android.lib.database.DBTableInfo
import io.reactivex.Observer
import io.reactivex.disposables.Disposable

class DBInitObserver(var initListener: DBManager.OnInitDatabaseLisener?): Observer<Any> {
    override fun onComplete() {
    }

    override fun onSubscribe(d: Disposable) {
    }

    override fun onNext(value: Any) {
        value as Map<String, Any>
        initListener?.onInitSuccess(value["tables"] as List<DBTableInfo>, value["dbinfo"] as DBInfo, value["dbhelper"] as DBHelper)
    }

    override fun onError(e: Throwable) {
        initListener?.onError(e)
    }


}