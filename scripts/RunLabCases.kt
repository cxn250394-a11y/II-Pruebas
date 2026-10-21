import com.charly.alertcenter.LabCases
fun main() {
    var passed = 0
    LabCases.all.forEach { (name, test) ->
        check(test()) { "FAIL: $name" }
        println("PASS: $name")
        passed++
    }
    println("RESULTADO: $passed/${LabCases.all.size} PASS (pure Kotlin, not Android instrumentation)")
}
