# database
DatabaseManager is a kotlin plugin which could operate database, such as create table, add, update ,query,and delete by a fool

1,how to integrate the manager into your project
   open module build.gradle, add the dependency below->
   
    	implementation 'com.uicole.lib:fool-database-manager:1.0.0'
	#implementation "org.jetbrains.kotlin:kotlin-reflect:$kotlin_version"
	implementation "io.reactivex.rxjava2:rxjava:$rx_java2_version"

2,add the jar into your dependency

3,create your json adapter

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

4, create database bean

@DBA(table = "ves", isInherit = false)
class Ves(@DBA(row = "s") var ss: String = "1d", @DBA(row = "sd", isColumn = false) var sss: String = "cd") {

    @DBA(row = "sdx", isColumn = true) var tt: String = "43"
    var dd = true
    var kk: String? = null
    var arr: Array<String> = arrayOf("ksf")
    var vesss = ArrayList<Ve>()
    var v: Ve?= Ve()
    var b:Boolean = false
    @DBA(primaryKey = true) var i: Int = 6
    var ks: Float = 64f
    var ds: Double = 5.0
    var date = Date()
    var newDate = Date()
    var finll = HashMap<String, String>()
    
}

note: 
"id" column is not admit, because this string is occupied by the manager

DBA:

     table-> table name which manager create table used is class simple name by default, you could specify table name by this value
	
     row -> specify the column name, use the property name by default
     
     isColumn -> specify the property is or not a column in the table ,true is default
     
     isInherit -> specify the table is or not inherit from their parent, false is default
     
     primariKey -> default is false, every bean must specify a primary key

5, create dao

@DBQuery(tableClazz = Ves::class)

interface VesDao {

    @DBQuery(selectionArr = [Q(express = "date <= ?")])
    fun queryAll(date: Date): io.reactivex.Observable<Array<Ves>>

}


