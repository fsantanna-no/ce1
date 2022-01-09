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
        assert(out == "var x: [_int,_int]\nset x = [(_10: _int),input std: _int]\n") { out }
    }
    @Test
    fun a05_upref () {
        val out = all("""
            var y: _int = _10
            var x: /_int = /_y
            output std x\
        """.trimIndent())
        assert(out == """
            var y: _int
            set y = (_10: _int)
            var x: /_int@local
            set x = (/(_y: _int))
            output std (x\)
            
        """.trimIndent()) { out }
    }
    @Test
    fun a06_dnref () {
        val out = all("""
            var y: /_int = _10
            var x: _int = _y\
            output std x\
        """.trimIndent())
        assert(out == """
            var y: /_int@local
            set y = (_10: /_int@local)
            var x: _int
            set x = ((_y: /_int@local)\)
            output std (x\)
            
        """.trimIndent()) { out }
    }
    @Test
    fun a07_call () {
        val out = all("""
            var v = call _f ()
        """.trimIndent())
        assert(out == """
            var v: _
            set v = call (_f: _) ()

        """.trimIndent()) { out }
    }
    @Test
    fun a08_call () {
        val out = all("""
            var f = func {}->{}-><()>->() { set ret=arg }
            var v = call f <.1>
        """.trimIndent())
        assert(out == """
            var f: {} -> {} -> <()> -> ()
            set f = func {} -> {} -> (<()>) -> (()) {
            set ret = arg
            }
            
            var v: ()
            set v = call f <.1 ()>: <()>
            
        """.trimIndent()) { out }
    }
}