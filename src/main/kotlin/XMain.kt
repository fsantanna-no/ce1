import java.io.File
import java.io.PushbackReader
import java.io.StringReader

fun ce1_to_ce0 (file: String, ce1: String): Pair<Boolean,String> {
    All_restart(file, PushbackReader(StringReader(ce1), 2))
    Lexer.lex()
    try {
        val s = XParser().stmts()
        s.setUps(null)
        s.setScp1s()
        s.setEnvs(null)
        check_00_after_envs(s)
        s.xinfScp1s()
        check_01_before_tps(s)
        s.setScp2s()
        s.xinfTypes(null)
        return Pair(true, s.tostr())
    } catch (e: Throwable) {
        //throw e
        return Pair(false, e.message!!)
    }
}

fun main (args: Array<String>) {
    var xinp: String? = null
    var i = 0
    while (i < args.size) {
        when {
            (args[i] == "-cc") -> i++
            else               -> xinp = args[i]
        }
        i++
    }

    val inp = File(xinp!!).readText()
    val (ok1,out1) = ce1_to_ce0(xinp!!, inp)
    if (!ok1) {
        println(out1)
        return
    }
    File("out.ce0").writeText(out1)

    args[0] = "out.ce0"
    println((listOf("ce0") + args).joinToString(" "))
    val (_,out2) = exec(listOf("ce0") + args)
    print(out2)
}

