package com.uicole.android.lib.database.adapter

import java.lang.reflect.Type

interface JsonAdapter {
    fun toJSONStr(any: Any): String
    fun <T> toInstance(text: String, clazz: Class<T>) : T?
    fun <T> toInstanceList(text: String, clazz: Class<T>): List<T>?
    fun toInstance(text: String, clazz: Type): Any?
}