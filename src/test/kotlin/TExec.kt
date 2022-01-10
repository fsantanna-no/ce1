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
        s.setUps(null)
        ENV.clear()
        s.setEnvs(null)
        s.xinfFuncs()
        check_01_before_tps(s)
        s.xinfTypes(null)
        s.xinfCalls()
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
            var f = func (_int -> _int) {
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
            var f = func ()->() {
                var x = _10:_int
                output std x
            }
            var g = func ()->() {
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
        var inv = func (<(),()> -> <(),()>) {
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

    @Test
    fun b01_new () {
        val out = all("""
            var xxx: /<(),/</^^,/^>>
            set xxx =
                new <.2
                    new <.1
                        new <.1>
                    >
                >
            output std xxx
        """.trimIndent())
        assert(out == "<.2 <.1 <.1>>>\n") { out }
    }
    @Test
    fun b02_new () {
        val out = all("""
            var x: /<(),/</^^ @local,/^ @local> @local> @local
            set x = new <.2 new <.2 new <.2 <.0>>>>
            output std x
        """.trimIndent())
        assert(out == "<.2 <.2 <.2 <.0>>>>\n") { out }
    }
    @Test
    fun b03_new () {
        val out = all("""
            var x: /<(),/</^^ @local,/^ @local> @local> @local
            set x = new <.2 new <.1 new <.1>>>
            output std x
        """.trimIndent())
        assert(out == "<.2 <.1 <.1>>>\n") { out }
    }
    @Test
    fun b04_new () {
        val out = all("""
            var x: /<(),/</^^ @local,/^ @local> @local> @local
            set x = new <.2 new <.2 new <.1 new <.1>>>>
            output std x
        """.trimIndent())
        assert(out == "<.2 <.2 <.1 <.1>>>>\n") { out }
    }
    @Test
    fun b05_new () {
        val out = all("""
            var x: /</<[/^^,/^]>>
            set x = new <.1 new <.1 [<.0>,<.0>]>>
            output std x
        """.trimIndent())
        assert(out == "<.1 <.1 [<.0>,<.0>]>>\n") { out }
    }
    @Test
    fun b06_new () {
        val out = all("""
            var x: /</<[/^^ @local,/^ @local]> @local> @local
            set x = new <.1 new <.1 [<.0>,new <.1 [<.0>,<.0>]>]>>
            output std x
        """.trimIndent())
        assert(out == "<.1 <.1 [<.0>,<.1 [<.0>,<.0>]>]>>\n") { out }
    }
    @Test
    fun b08_new () {
        val out = all("""
            var e: /<(),<(),/^^ @local>>
            set e = new <.2 <.2 new <.1>>>
            var s: <(),/<(),<(),/^^>>>
            set s = <.2 e>
            output std /s
        """.trimIndent())
        assert(out == "<.2 <.2 <.2 <.1>>>>\n") { out }
    }
    @Test
    fun b09_union () {
        val out = all("""
            var x: /</^ @local> @local = <.0>
            var y: <//</^ @local> @local @local>
            set y = <.1 /x>
            output std /y
            output std y!1\
        """.trimIndent())
        //assert(out == "(ln 3, col 7): invalid assignment of \"x\" : borrowed in line 2") { out }
        assert(out == "<.1 _>\n<.0>\n") { out }
    }
    @Test
    fun b10_new () {
        val out = all("""
            var x: /< [<(),//^^ @local @local>,/^ @local]> @local
            set x = new <.1 [<.1>,<.0>]>
            var y: /< [<(),//^^>,/^]>
            set y = new <.1 [<.1>,x]>
            set y\!1.2\!1.1 = <.1>
            -- <.1 [<.1>,<.1 [<.1>,<.0>]>]>
            output std y
        """.trimIndent())
        assert(out == "<.1 [<.1>,<.1 [<.1>,<.0>]>]>\n") { out }
    }
    @Test
    fun b11_new_self () {
        val out = all("""
            var x: /< [<(),//^^ @local @local>,/^ @local]> @local
            set x = new <.1 [<.1>,<.0>]>
            var y: /< [<(),//^^>,/^]>
            set y = new <.1 [<.1>, x]>
            set y\!1.2\!1.1 = <.2 /y>
            output std y
        """.trimIndent())
        assert(out == "<.1 [<.1>,<.1 [<.2 _>,<.0>]>]>\n") { out }
    }
    @Test
    fun b12_new_self () {
        val out = all("""
            var x: /<[(),/^ @local]> @local
            set x = new <.1 [(),<.0>]>
            var y: [(),/<[(),/^]>]
            set y = [(), new <.1 [(),<.0>]>]
            var z: [(),//<[(),/^]>]
            set z = [(), /x]
            output std z.2\\!1.2\!0
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun b13_new_self () {
        val out = all("""
            var x: /< [//^ @local @local,/^ @local]> @local
            set x = new <.1 [_,<.0>]>
            set x\!1.1 = /x
            output std x
            output std x\!1.1\
        """.trimIndent())
        assert(out == "<.1 [_,<.0>]>\n<.1 [_,<.0>]>\n") { out }
    }
    @Test
    fun b14_new_self () {
        val out = all("""
            var x: /< [<(),//^^>,/^]>
            set x = new <.1 [<.1>,<.0>]>
            set x\!1.1 = <.2 /x>  -- ok
            output std x
            output std x\!1.1!2\
        """.trimIndent())
        assert(out == "<.1 [<.2 _>,<.0>]>\n<.1 [<.2 _>,<.0>]>\n") { out }
    }
    @Test
    fun b15_new_self () {
        val out = all("""
            var x: /< <(),//^^ @local @local>> @local
            set x = new <.1 <.1>>
            output std x
        """.trimIndent())
        assert(out == "<.1 <.1>>\n") { out }
        //assert(out == "(ln 1, col 14): invalid type declaration : unexpected `^´") { out }
    }
    @Test
    fun b16_new () {
        val out = all("""
            var l: /<(),/^> = new <.2 new <.1>>
            var t1 = [l]
            var t2 = [t1.1]
            output std /t2
        """.trimIndent())
        assert(out == "[<.2 <.1>>]\n") { out }
    }
    @Test
    fun b17_new () {
        val out = all("""
            var l: /<(),/^> = new <.2 new <.1>>
            var t1 = [(), l]
            var t2 = [(), t1.2]
            output std /t2
        """.trimIndent())
        assert(out == "[(),<.2 <.1>>]\n") { out }
    }
    @Test
    fun b18_new () {
        val out = all("""
            var v1: /<(),/<[/^^,/^]>> = new <.2 <.0>>
            var v2: /<(),/<[/^^,/^]>>
            set v2 = new <.2 new <.1 [new <.1>,<.0>]>>
            output std v1
            output std v2
        """.trimIndent())
        assert(out == "<.2 <.0>>\n<.2 <.1 [<.1>,<.0>]>>\n") { out }
    }
    @Test
    fun b19_new () {
        val out = all("""
            var x: /< [<(),//^^ @local @local>,/^ @local]> @local = new <.1 [<.1>,<.0>]>
            var y: /< [<(),//^^ @local @local>,/^ @local]> @local = new <.1 [<.1>,x]>
            set y\!1.2\!1.1 = <.1>
            output std y
        """.trimIndent())
        assert(out == "<.1 [<.1>,<.1 [<.1>,<.0>]>]>\n") { out }
        //assert(out == "(ln 1, col 16): invalid type declaration : unexpected `^´") { out }
    }
    @Test
    fun b20_new () {
        val out = all("""
            var x: /< [<(),//^^ @local @local>,/^ @local]> @local = new <.1 [<.1>,new <.1 [<.1>,<.0>]>]>
            set x\!1.2 = <.0>
            output std x
        """.trimIndent())
        //assert(out == "(ln 1, col 14): invalid type declaration : unexpected `^´") { out }
        //assert(out == "out.exe: out.c:133: main: Assertion `(*(x))._1._2 == NULL' failed.\n") { out }
        assert(out == "<.1 [<.1>,<.0>]>\n") { out }
    }
    @Test
    fun b21_new () {
        val out = all("""
            var x: /<(),/^ @local> @local = new <.2 new <.1>>
            var y = x
            output std x
            output std y
        """.trimIndent())
        assert(out == "<.2 <.1>>\n<.2 <.1>>\n") { out }
    }
    @Test
    fun b22_new () {
        val out = all("""
            var x: /<(),[(),/^]> = new <.2 [(),new <.1>]>
            var y = [(), x\!2.2]
            output std x
            output std /y
        """.trimIndent())
        assert(out == "<.2 [(),<.1>]>\n[(),<.1>]\n") { out }
    }

    // FUNC / CALL

    @Test
    fun c01 () {
        val out = all("""
        var f = func /_int@_1 -> () {
           set arg\ = _(*arg+1)
           return
        }
        var x: _int = _1
        call f {@local} /x
        output std x
        """.trimIndent())
        assert(out == "2\n") { out }
    }
}