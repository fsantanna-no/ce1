import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class TPar {

    fun all (inp: String): String {
        All_new(PushbackReader(StringReader(inp), 2))
        Lexer.lex()
        val s = XParser().stmts()
        s.setUps(null)
        s.setEnvs(null)
        s.xinfScp1s()
        check_01_before_tps(s)
        s.setScp2s()
        s.xinfTypes(null)
        val ce0 = s.tostr()
        File("out.ce").writeText(ce0)
        val (ok,out) = exec("ce0 out.ce")
        assert(ok)
        return out
    }

    // SPAWN

    @Test
    fun a01_spawn () {
        val out = all("""
            spawn {
                output std ()
            }
            spawn {
                output std ()
            }
        """.trimIndent())
        //assert(out == "(ln 2, col 5): expected `in` : have end of file") { out }
        assert(out == "()\n()\n") { out }
    }
    @Test
    fun a02_spawn_var () {
        val out = all("""
            var x = ()
            spawn {
                output std x
            }
            spawn {
                output std x
            }
        """.trimIndent())
        //assert(out == "(ln 2, col 5): expected `in` : have end of file") { out }
        assert(out == "()\n()\n") { out }
    }
    @Test
    fun a03_spawn_spawn_var () {
        val out = all("""
            spawn {
                var x = ()
                spawn {
                    output std x
                }
                spawn {
                    output std x
                }
            }
        """.trimIndent())
        //assert(out == "(ln 2, col 5): expected `in` : have end of file") { out }
        assert(out == "()\n()\n") { out }
    }
    @Test
    fun a04_spawn_spawn_spawn_var () {
        val out = all("""
            spawn {
                var x = ()
                spawn {
                    spawn {
                        output std x
                    }
                }
                spawn {
                    output std x
                }
            }
        """.trimIndent())
        //assert(out == "(ln 2, col 5): expected `in` : have end of file") { out }
        assert(out == "()\n()\n") { out }
    }
}