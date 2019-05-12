package com.uicole.android.lib.database.adapter

interface JsonAdapter {
    fun toJSONStr(any: Any): String
    fun <T> toInstance(text: String, clazz: Class<T>) : T?
    fun <T> toInstanceList(text: String, clazz: Class<T>): List<T>?
}