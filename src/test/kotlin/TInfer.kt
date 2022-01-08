import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class TInfer {

    fun all (inp: String): String {
        val all = All_new(PushbackReader(StringReader(inp), 2))
        lexer(all)
        val s = xparser_stmts(all, Pair(TK.EOF,null))
        aux_clear()
        s.aux_upsenvs(null, null)
        check_01_before_tps(s)
        s.aux_tps(null)
        return s.tostr()
    }

    @Test
    fun a01_var () {
        val out = all("var x = ()")
        assert(out == "var x: ()\nset x = ()\n") { out }
    }
    @Test
    fun a02_var () {
        val out = all("var x = <.1>:<(),()>")
        assert(out == "var x: <(),()>\nset x = <.1 ()>: <(),()>\n") { out }
    }
    @Test
    fun a03_input () {
        val out = all("var x: _int = input std")
        assert(out == "var x: _int\nset x = input std: _int\n") { out }
    }
    @Test
    fun a04_input () {
        val out = all("var x: [_int,_int] = [_10,input std]")
        assert(out == "var x: [_int,_int]\nset x = [_10,input std: _int]\n") { out }
    }
}