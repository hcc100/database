package com.uicole.android.lib.database

import kotlin.reflect.KClass

enum class QueryAction {
    QUERY, DELETE
}

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class Q(val express: String = "", val key: String = "")

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class DBA(val table: String = "", val row: String = "", val isColumn: Boolean = true
                     , val isInherit: Boolean = false, val primaryKey: Boolean = false)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class DBQuery(val action: QueryAction = QueryAction.QUERY, val tableClazz: KClass<*> = Any::class, val selections: String = "", val selectionArr: Array<Q> = [], val orderBy: String = "", val limit: Int = 50)