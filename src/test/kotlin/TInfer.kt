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
        try {
            val s = xparser_stmts(all, Pair(TK.EOF, null))
            s.setUps(null)
            s.setEnvs(null)
            s.xinfScp1s()
            check_01_before_tps(s)
            s.setScp2s()
            s.xinfTypes(null)
            return s.tostr()
        } catch (e: Throwable) {
            //throw e
            return e.message!!
        }
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
            set v = call (_f: _) {} ()

        """.trimIndent()) { out }
    }
    @Test
    fun a08_call () {
        val out = all("""
            var f = func <()>->() { set ret=arg }
            var v = call f <.1>
        """.trimIndent())
        assert(out == """
            var f: func {} -> {} -> <()> -> ()
            set f = func {} -> {} -> <()> -> () {
            set ret = arg
            }
            
            var v: ()
            set v = call f {} <.1 ()>: <()>
            
        """.trimIndent()) { out }
    }
    @Test
    fun a09_union () {
        val out = all("""
            var x : <<()>>
            set x = <.1 <.1>>
        """.trimIndent())
        assert(out == """
            var x: <<()>>
            set x = <.1 <.1 ()>: <()>>: <<()>>
            
        """.trimIndent()) { out }
    }
    @Test
    fun a10_new () {
        val out = all("""
            var l: /</^ @local> @local
            set l = new <.1 <.0>>
        """.trimIndent())
        assert(out == """
            var l: /</^@local>@local
            set l = (new <.1 <.0 ()>: /</^@local>@local>: </^@local>: @local)

        """.trimIndent()) { out }
    }
    @Test
    fun a11_new () {
        val out = all("""
            var l = new <.1 <.0>>:</^>
        """.trimIndent())
        assert(out == """
            var l: /</^@local>@local
            set l = (new <.1 <.0 ()>: /</^@local>@local>: </^@local>: @local)
            
        """.trimIndent()) { out }
    }

    // inference error

    @Test
    fun b01 () {
        val out = all("""
            var x: /<(),/</^^,/^>>
            set x = <.1>
        """.trimIndent())
        assert(out == "(ln 2, col 11): invalid inference : type mismatch") { out }
    }
    @Test
    fun b02 () {
        val out = all("""
            var x: /<(),/</^^,/^>>
            set x = new <.2 new <.1 <.1>>>
        """.trimIndent())
        assert(out == "(ln 2, col 27): invalid inference : type mismatch") { out }
    }
    @Test
    fun b03 () {
        val out = all("""
            var x: /</<[/^^ @local,/^ @local]> @local> @local
            set x = new <.1 <.1 [<.0>,<.0>]>>
        """.trimIndent())
        assert(out == "(ln 2, col 19): invalid inference : type mismatch") { out }
    }
    @Test
    fun b04_self () {
        val out = all("""
            var z: /< [<(),//^^>,_int,/^]> = <.0>
            var x: /< [<(),//^^>,_int,/^]> = new <.1 [z,_1,new <.1 [z,_2,z]>]>
            set x!1.3!1.1 = <.1 /x>
            set x!1.1 = <.1 /x!1.3>
            output std x!1.3!1.2
            output std x!1.1!1\!1.1!1\!1.2
        """.trimIndent())
        assert(out == "(ln 4, col 17): invalid operand to `/´ : union discriminator") { out }
    }

    // POINTER ARGUMENTS / SCOPES

    @Test
    fun c01 () {
        val out = all("""
        var f: func /_int -> ()
        """.trimIndent())
        assert(out == "var f: func {} -> {@i_1} -> /_int@i_1 -> ()\n") { out }
    }
    @Test
    fun c02 () {
        val out = all("""
        var f = func /_int -> () {}
        """.trimIndent())
        assert(out == """
            var f: func {} -> {@i_1} -> /_int@i_1 -> ()
            set f = func {} -> {@i_1} -> /_int@i_1 -> () {
            
            }
            
            
        """.trimIndent()) { out }
    }
    @Test
    fun c03 () {
        val out = all("""
        var f: func /_int@_1 -> ()
        var x: _int = _1
        call f /x
        """.trimIndent())
        assert(out == """
            var f: func {} -> {@_1} -> /_int@_1 -> ()
            var x: _int
            set x = (_1: _int)
            call f {@global} (/x)
            
        """.trimIndent()) { out }
    }
    @Test
    fun c04 () {
        val out = all("""
        var f: func /_int@_1 -> ()
        {
            var x: _int = _1
            var y: _int = _1
            call f /x
            call f /y
        }
        """.trimIndent())
        assert(out == """
            var f: func {} -> {@_1} -> /_int@_1 -> ()
            { @ssx
            var x: _int
            set x = (_1: _int)
            var y: _int
            set y = (_1: _int)
            call f {@ssx} (/x)
            call f {@ssx} (/y)
            }
            
        """.trimIndent()) { out }
    }
    @Test
    fun c05_fact () {
        val out = all(
            """
            var fact = func [/_int,_int] -> () {
                var x = _1:_int
                call fact [/x,_]
            }
        """.trimIndent()
        )
        assert(out == "(ln 3, col 10): invalid inference : undetermined type") { out }
    }
    @Test
    fun c06_new_return () {
        val out = all("""
            var f = func /</^>->() {
                set arg\!1 = new <.1 <.0>>
            }
        """.trimIndent())
        assert(out == """
            var f: func {} -> {@i_1,@j_1} -> /</^@i_1>@j_1 -> ()
            set f = func {} -> {@i_1,@j_1} -> /</^@i_1>@j_1 -> () {
            set ((arg\)!1) = (new <.1 <.0 ()>: /</^@i_1>@i_1>: </^@i_1>: @i_1)
            }


        """.trimIndent()) { out }
    }

    // CLOSURE

    @Test
    fun d01_clo () {
        val out = all("""
            {
                var pa: /</^> = new <.1 <.0>>
                var f = func () -> () [pa] {
                }
                call f ()
            }
        """.trimIndent())
        assert(out == """
            { @ss
            var pa: /</^@local>@local
            set pa = (new <.1 <.0 ()>: /</^@local>@local>: </^@local>: @local)
            var f: func {@ss} -> {} -> () -> ()
            set f = func {@ss} -> {} -> () -> () [pa] {

            }
            
            call f {} ()
            }
            
        """.trimIndent()) { out }
    }
    @Test
    fun d02_clo () {
        val out = all("""
            var f: func () -> (func @a_1->()->())
        """.trimIndent()
        )
        assert(out == """
            var f: func {} -> {@a_1} -> () -> func {@a_1} -> {@i_1} -> () -> ()
            
        """.trimIndent()) { out }
    }
    @Test
    fun d03_clo () {
        val out = all("""
            var f: func @local->()->()
        """.trimIndent()
        )
        assert(out == """
            var f: func {@local} -> {@i_1} -> () -> ()
            
        """.trimIndent()) { out }
    }
}