import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class TExec {

    fun all (inp: String): String {
        val all = All_new(PushbackReader(StringReader(inp), 2))
        lexer(all)
        val s = xparser_stmts(all, Pair(TK.EOF,null))
        aux_clear()
        s.aux_upsenvs(null, null)
        check_01_before_tps(s)
        s.aux_tps(null)
        val ce0 = s.tostr()
        File("out.ce").writeText(ce0)
        val (ok,out) = exec("ce0 out.ce")
        assert(ok)
        return out
    }

    @Test
    fun a01_output () {
        val out = all("output std ()")
        assert(out == "()\n") { out }
    }
    @Test
    fun a02_int_abs () {
        val out = all("""
            var x: _int
            set x = call _abs _(-1)
            output std x
        """.trimIndent())
        assert(out == "1\n") { out }
    }
    @Test
    fun a03_tuple () {
        val out = all("""
            var v = [(),()]
            output std /v
        """.trimIndent())
        assert(out == "[(),()]\n") { out }
    }
    @Test
    fun a04_tuples () {
        val out = all("""
            var v = [(),()]
            var x = [(),v]
            var y = x.2
            var z = y.2
            output std z
            output std /x
        """.trimIndent())
        assert(out == "()\n[(),[(),()]]\n") { out }
    }
    @Test
    fun a05_nat () {
        val out = all("""
            var y: _(char*) = _{"hello"}
            var n: _{int} = _10
            var x = [n,y]
            output std /x
        """.trimIndent())
        assert(out == "[10,\"hello\"]\n") { out }
    }
    @Test
    fun a06_call () {
        val out = all("""
            var f = func ({}->{}-> _int -> _int) {
                return arg
            }
            var x = call f _10
            output std x
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun a07_call_fg () {
        val out = all("""
            var f = func {}->{}-> ()->() {
                var x = _10:_int
                output std x
            }
            var g = func {}->{}-> ()->() {
                return call f ()
            }
            call g ()
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun a08_union () {
        val out = all("""
            var a = <.1>:<(),()>
            var b : <(),()> = <.2>
            output std /a
            output std /b
        """.trimIndent())
        assert(out == "<.1>\n<.2>\n") { out }
    }
    @Test
    fun a09_func_if () {
        val out = all("""
        var inv = func ({}->{}-> <(),()> -> <(),()>) {
            if arg?1 {
                return <.2>
            } else {
                return <.1>
            }
        }
        var a: <(),()> = <.2>
        var b = call inv a
        output std /b
        """.trimIndent())
        assert(out == "<.1>\n") { out }
    }
    @Test
    fun a10_loop () {
        val out = all("""
        var i: _int = _1
        var n = _0: _int
        loop {
            set n = _(n + i)
            set i = _(i + 1)
            if _(i > 5) {
                break
            }
        }
        output std n
        """.trimIndent())
        assert(out == "15\n")
    }
    @Test
    fun a11_unions () {
        val out = all("""
            var z = <.1()>:<()>
            var y : <<()>> = <.1 z>
            var x = <.1 y>:<<<()>>>
            var yy: <<()>> = x!1
            var zz = yy!1
            output std /zz
        """.trimIndent())
        assert(out == "<.1>\n") { out }
    }
    @Test
    fun a12_tuple_nat () {
        val out = all("""
            var s: [_int,_int,_int,_int] = [_1,_2,_3,_4]
            output std /s
        """.trimIndent())
        assert(out == "[1,2,3,4]\n") { out }
    }
    @Test
    fun a13_union_nat () {
        val out = all("""
            var s: <[_int,_int,_int,_int],_int,_int> = <.1 [_1,_2,_3,_4]>
            output std /s
        """.trimIndent())
        assert(out == "<.1 [1,2,3,4]>\n") { out }
    }
    @Test
    fun a14_pred () {
        val out = all("""
            var z = <.1>: <()>
            output std z?1
        """.trimIndent())
        assert(out == "1\n") { out }
    }
    @Test
    fun a15_disc () {
        val out = all("""
            var z: <(),()> = <.2>
            output std z!2
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun a16_dots () {
        val out = all("""
            var x: <<<()>>> = <.1 <.1 <.1>>>
            output std x!1!1!1
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun a17_if () {
        val out = all("""
            var x: <(),()> = <.2>
            if x?2 { output std () } else { }
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun a18_loop () {
        val out = all("""
        loop {
           break
        }
        output std ()
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun a19_ptr () {
        val out = all("""
            var y: _int = _10
            var x = /y
            output std x\
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun i05_ptr_block_err () {
        val out = all("""
            var p1: /_int
            var p2: /_int
            {
                var v: _int = _10: _int
                set p1 = /v  -- no
            }
            output std p1\
        """.trimIndent())
        assert(out == "(ln 6, col 8): invalid assignment : type mismatch\n") { out }
    }

    // old disabled

}