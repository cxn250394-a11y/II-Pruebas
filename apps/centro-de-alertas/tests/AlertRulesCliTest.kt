import com.charly.alertcenter.AlertRules

fun main() {
    var passed = 0
    fun checkRule(name: String, condition: Boolean) {
        if (!condition) throw AssertionError("FAIL: $name")
        println("PASS: $name")
        passed++
    }
    checkRule("Acentos y búsqueda", AlertRules.matchesQuery("Córner", "Minuto 32", "corner"))
    checkRule("Búsqueda con espacios", AlertRules.matchesQuery("gol del Barcelona", "", "  GOL   del  "))
    checkRule("Búsqueda negativa", !AlertRules.matchesQuery("Partido terminado", "0-0", "tarjeta"))
    checkRule("Palabra exacta", AlertRules.hasPriorityWord("¡GOL!", "Minuto 91", "gol"))
    checkRule("Sin falsos positivos gol/golpe", !AlertRules.hasPriorityWord("Golpe fuerte", "", "gol"))
    checkRule("Frase roja", AlertRules.hasPriorityWord("Tarjeta ROJA", "al 88", "tarjeta roja"))
    checkRule("Frase con acento", AlertRules.hasPriorityWord("Córner", "", "corner"))
    checkRule("Desduplicación estable", AlertRules.fingerprint("org.telegram.messenger", 0, "Hola", "Mensaje") == AlertRules.fingerprint("org.telegram.messenger", 0, " hola ", "Mensaje"))
    checkRule("Apps distintas no se unen", AlertRules.fingerprint("app.nicegram", 0, "Hola", "Mensaje") != AlertRules.fingerprint("org.telegram.messenger", 0, "Hola", "Mensaje"))
    checkRule("Perfiles duales distintos", AlertRules.fingerprint("org.telegram.messenger", 0, "Hola", "Mensaje") != AlertRules.fingerprint("org.telegram.messenger", 999, "Hola", "Mensaje"))
    checkRule("Código OTP oculto", AlertRules.protectSensitiveText("Telegram", "Your verification code is 123456") == "[Contenido sensible oculto]")
    checkRule("Mensaje ordinario intacto", AlertRules.protectSensitiveText("Telegram", "Gol de Chivas al minuto 30") == "Gol de Chivas al minuto 30")
    checkRule("ID de paquete válido", AlertRules.validPackageId("eu.livesport.LiveSport"))
    checkRule("ID de paquete inválido", !AlertRules.validPackageId("com.a.espacio malicioso"))
    checkRule("Diferentes claves notifican dos eventos iguales", AlertRules.fingerprint("org.telegram.messenger", 0, "Hola", "Mensaje", "key-A") != AlertRules.fingerprint("org.telegram.messenger", 0, "Hola", "Mensaje", "key-B"))
    checkRule("El título sensible también se oculta", AlertRules.isLikelySensitive("Verification code: 123456", "") )
    checkRule("Flashscore México detectado", AlertRules.builtInSources.any { it.packageId == "eu.livesport.FlashScore_com" })
    checkRule("Novibet México detectado", AlertRules.builtInSources.any { it.packageId == "mex.novibet.sports.betting" })
    checkRule("Límites de términos", AlertRules.terms((1..60).joinToString(",") { "p$it" }).size == 40)
    checkRule("Límites de texto", AlertRules.clip("123456789", 4) == "1234")
    println("RESULTADO: $passed/$passed PASS")
}
