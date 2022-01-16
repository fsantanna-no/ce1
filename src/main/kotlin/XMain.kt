import java.io.File
import java.io.PushbackReader
import java.io.StringReader

fun ce1_to_ce0 (ce1: String): Pair<Boolean,String> {
    val all = All_new(PushbackReader(StringReader(ce1), 2))
    lexer(all)
    try {
        val s = xparser_stmts(all, Pair(TK.EOF,null))
        s.setUps(null)
        s.setEnvs(null)
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
    var xcc = ""
    var i = 0
    while (i < args.size) {
        when {
            (args[i] == "-cc") -> xcc  = "-cc " + args[++i]
            else               -> xinp = args[i]
        }
        i++
    }
    val inp = File(xinp).readText()
    val (ok1,out1) = ce1_to_ce0(inp)
    if (!ok1) {
        println(out1)
        return
    }
    File("out.ce0").writeText(out1)
    val (_,out2) = exec("ce0 $xcc out.ce0")
    print(out2)
}

