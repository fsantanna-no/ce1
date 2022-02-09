import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class TInfer {

    fun all (inp: String): String {
        All_new(PushbackReader(StringReader(inp), 2))
        Lexer.lex()
        try {
            val s = xparser_stmts()
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
    fun a01_var_err () {
        val out = all("var x; set x=()")
        assert(out == "(ln 1, col 6): expected type declaration : have `;´") { out }
    }
    @Test
    fun a02_var () {
        val out = all("var x = <.1>:<(),()>")
        assert(out == "var x: <(),()>\nset x = <.1 ()>: <(),()>\n") { out }
    }
    @Test
    fun a03_input () {
        val out = all("var x: _int = input std ()")
        assert(out == "var x: _int\nset x = input std (): _int\n") { out }
    }
    @Disabled // no more expr
    @Test
    fun a04_input () {
        val out = all("var x: [_int,_int] = [_10,input std ()]")
        assert(out == "var x: [_int,_int]\nset x = [(_10: _int),input std (): _int]\n") { out }
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
            var x: /_int @LOCAL
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
            var y: /_int @LOCAL
            set y = (_10: /_int @LOCAL)
            var x: _int
            set x = ((_y: /_int @LOCAL)\)
            output std (x\)
            
        """.trimIndent()) { out }
    }
    @Test
    fun a07_call () {
        val out = all("""
            var v = _f ()
        """.trimIndent())
        assert(out == """
            var v: _
            set v = ((_f: _) @[] ())

        """.trimIndent()) { out }
    }
    @Test
    fun a08_call () {
        val out = all("""
            var f = func <()>->() { return arg }
            var v = f <.1>
        """.trimIndent())
        assert(out == """
            var f: func @[] -> <()> -> ()
            set f = func @[] -> <()> -> () {
            set ret = arg
            return
            }
            
            var v: ()
            set v = (f @[] <.1 ()>: <()>)
            
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
    fun a10_type1 () {
        val out = all("""
            type List = </List @LOCAL>
            var l: /List = <.0>
            output std l
        """.trimIndent())
        assert(out == """
            type List @[] = </List @LOCAL>
            var l: /List @LOCAL
            set l = <.0>: /List @LOCAL
            output std l
            
        """.trimIndent()) { out }
    }
    @Test
    fun a10_type2 () {
        val out = all("""
            type List = </List>
            var l: /List = <.0>
            output std l
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            var l: /List @[LOCAL] @LOCAL
            set l = <.0>: /List @[LOCAL] @LOCAL
            output std l

        """.trimIndent()) { out }
    }
    @Test
    fun a10_new () {
        val out = all("""
            type List = </List>
            var l: /List = new <.1 <.0>>
            output std l
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            var l: /List @[LOCAL] @LOCAL
            set l = (new <.1 <.0>: /List @[LOCAL] @LOCAL>: </List @[LOCAL] @LOCAL>: @LOCAL)
            output std l

        """.trimIndent()) { out }
    }
    @Test
    fun a11_new () {
        val out = all("""
            type List = </List>
            var l = new <.1 <.0>>:List
            output std l
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            var l: /List @[LOCAL] @LOCAL
            set l = (new <.1 <.0>: /List @[LOCAL] @LOCAL>: List @[LOCAL]: @LOCAL)
            output std l

        """.trimIndent()) { out }
    }
    @Test
    fun a12_ucons () {
        val out = all("""
            type X = <()>
            var y = <.2 ()>: X
        """.trimIndent())
        assert(out == "(ln 2, col 11): invalid union constructor : out of bounds") { out }
    }
    @Test
    fun a13_input_ptr () {
        val out = all("""
            var input_pico_Unit = func /() -> () {}
            var e: () = ()
            input pico /e
        """.trimIndent())
        assert(out == """
            var input_pico_Unit: func @[i] -> /() @i -> ()
            set input_pico_Unit = func @[i] -> /() @i -> () {

            }

            var e: ()
            set e = ()
            input pico (/e): ()

        """.trimIndent()) { out }
    }
    @Test
    fun a14_nat1 () {
        val out = all("var x: _int = _10")
        assert(out == "var x: _int\nset x = (_10: _int)\n") { out }
    }
    @Test
    fun a15_nat2 () {
        val out = all("var x: _int ; set x = _10")
        assert(out == "var x: _int\nset x = (_10: _int)\n") { out }
    }


    // inference error

    @Disabled
    @Test
    fun b01 () {
        val out = all("""
            var x: /<(),/</^^,/^>>
            set x = <.1>
        """.trimIndent())
        assert(out == "(ln 2, col 11): invalid inference : type mismatch") { out }
    }
    @Disabled
    @Test
    fun b02 () {
        val out = all("""
            var x: /<(),/</^^,/^>>
            set x = new <.2 new <.1 <.1>>>
        """.trimIndent())
        assert(out == "(ln 2, col 27): invalid inference : type mismatch") { out }
    }
    @Disabled
    @Test
    fun b03 () {
        val out = all("""
            var x: /</<[/^^ @LOCAL,/^ @LOCAL]> @LOCAL> @LOCAL
            set x = new <.1 <.1 [<.0>,<.0>]>>
        """.trimIndent())
        assert(out == "(ln 2, col 19): invalid inference : type mismatch") { out }
    }
    @Disabled
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
    @Test
    fun b05_nat () {
        val out = all("""
            var output_pico = func () -> () {
                native _{
                    pico_output(*(Pico_IO*)&arg);
                }
            }
        """.trimIndent())
        assert(out == """
            var output_pico: func @[] -> () -> ()
            set output_pico = func @[] -> () -> () {
            native _{
                    pico_output(*(Pico_IO*)&arg);
                }
            }

    
        """.trimIndent()) { out }
    }
    @Test
    fun b06_inp_err () {
        val out = all("""
            input pico ()
        """.trimIndent())
        assert(out == "input pico (): ()\n") { out }
    }

    // POINTER ARGUMENTS / SCOPES

    @Test
    fun c01 () {
        val out = all("""
        var f: func /_int -> ()
        """.trimIndent())
        assert(out == "var f: func @[i] -> /_int @i -> ()\n") { out }
    }
    @Test
    fun c02 () {
        val out = all("""
        var f = func /_int -> () {}
        """.trimIndent())
        assert(out == """
            var f: func @[i] -> /_int @i -> ()
            set f = func @[i] -> /_int @i -> () {
            
            }
            
            
        """.trimIndent()) { out }
    }
    @Test
    fun c03 () {
        val out = all("""
        var f: func /_int@k1 -> ()
        var x: _int = _1
        call f /x
        """.trimIndent())
        assert(out == """
            var f: func @[k1] -> /_int @k1 -> ()
            var x: _int
            set x = (_1: _int)
            call (f @[GLOBAL] (/x))
            
        """.trimIndent()) { out }
    }
    @Test
    fun c04 () {
        val out = all("""
        var f: func /_int@k -> ()
        {
            var x: _int = _1
            var y: _int = _1
            call f /x
            call f /y
        }
        """.trimIndent())
        assert(out == """
            var f: func @[k] -> /_int @k -> ()
            { @SSX
            var x: _int
            set x = (_1: _int)
            var y: _int
            set y = (_1: _int)
            call (f @[SSX] (/x))
            call (f @[SSX] (/y))
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
    fun c06_new_return0 () {
        val out = all("""
            type List @[x] = </List @[x] @x>
            var f = func /List->() {
                set arg\!1 = <.0>
            }
        """.trimIndent())
        assert(out == """
            type List @[x] = </List @[x] @x>
            var f: func @[i,j] -> /List @[j] @i -> ()
            set f = func @[i,j] -> /List @[j] @i -> () {
            set ((arg\)!1) = <.0>: /List @[j] @j
            }


        """.trimIndent()) { out }
    }
    @Test
    fun c06_new_return1 () {
        val out = all("""
            type List = </List>
            var f = func /List->() {
                set arg\!1 = <.0>
            }
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            var f: func @[i,j] -> /List @[j] @i -> ()
            set f = func @[i,j] -> /List @[j] @i -> () {
            set ((arg\)!1) = <.0>: /List @[j] @j
            }


        """.trimIndent()) { out }
    }
    @Test
    fun c06_new_return2 () {
        val out = all("""
            type List = </List>
            var f = func /List->() {
                set arg\!1 = new <.1 <.0>>
            }
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            var f: func @[i,j] -> /List @[j] @i -> ()
            set f = func @[i,j] -> /List @[j] @i -> () {
            set ((arg\)!1) = (new <.1 <.0>: /List @[j] @j>: </List @[j] @j>: @j)
            }

        """.trimIndent()) { out }
    }
    @Test
    fun c07_null () {
        val out = all("""
            var v = <.0>
        """.trimIndent())
        assert(out == "(ln 1, col 11): invalid inference : undetermined type") { out }
    }
    @Test
    fun c08_null () {
        val out = all("""
            type List = </List>
            var v: /List = <.0>
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            var v: /List @[LOCAL] @LOCAL
            set v = <.0>: /List @[LOCAL] @LOCAL

        """.trimIndent()) { out }
    }
    @Test
    fun c09_null () {
        val out = all("""
            type List @[i] = /</List @[i] @i> @i
            var f : func List -> ()
            call f <.0>
        """.trimIndent())
        assert(out == """
            type List @[i] = /</List @[i] @i> @i
            var f: func @[i] -> List @[i] -> ()
            call (f @[LOCAL] <.0>: List @[LOCAL])

        """.trimIndent()) { out }
    }
    @Test
    fun c09_null2 () {
        val out = all("""
            type List = /</List>
            var f : func List -> ()
            call f <.0>
        """.trimIndent())
        assert(out == """
            type List @[i,j] = /</List @[i,j] @j> @i
            var f: func @[i,j] -> List @[i,j] -> ()
            call (f @[LOCAL,LOCAL] <.0>: List @[LOCAL,LOCAL])

        """.trimIndent()) { out }
    }
    @Test
    fun c10_ff () {
        val out = all("""
            type List = </List>
            var f : func /List -> /List
            var v = f <.0>
            output std f v
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            var f: func @[i,j,k,l] -> /List @[j] @i -> /List @[l] @k
            var v: /List @[LOCAL] @LOCAL
            set v = (f @[LOCAL,LOCAL,LOCAL,LOCAL] <.0>: /List @[LOCAL] @LOCAL: @LOCAL)
            output std (f @[LOCAL,LOCAL,LOCAL,LOCAL] v: @LOCAL)

        """.trimIndent()) { out }
    }

    // CLOSURE

    @Test
    fun d01_clo () {
        val out = all("""
            type List = </List>
            {
                var pa: /List = new <.1 <.0>>
                var f = func () -> () [pa] {
                }
                call f ()
            }
        """.trimIndent())
        assert(out == """
            { @SS
            var pa: /</^@LOCAL>@LOCAL
            set pa = (new <.1 <.0>: /</^@LOCAL>@LOCAL>: </^@LOCAL>: @LOCAL)
            var f: func @SS -> @[] -> () -> ()
            set f = func @SS -> @[] -> () -> () [pa] {

            }
            
            call (f @[] ())
            }
            
        """.trimIndent()) { out }
    }
    @Test
    fun d02_clo () {
        val out = all("""
            var f: func () -> (func @a1->()->())
        """.trimIndent()
        )
        assert(out == """
            var f: func @[a1] -> () -> func @a1 -> @[] -> () -> ()
            
        """.trimIndent()) { out }
    }
    @Test
    fun d03_clo () {
        val out = all("""
            var f: func @LOCAL->()->()
        """.trimIndent()
        )
        assert(out == """
            var f: func @LOCAL -> @[] -> () -> ()
            
        """.trimIndent()) { out }
    }
    @Test
    fun d04_clo () {
        val out = all("""
            var g: func () -> (func @a1->()->())
            var f: func @LOCAL -> () -> ()
        """.trimIndent()
        )
        assert(out == """
            var g: func @[a1] -> () -> func @a1 -> @[] -> () -> ()
            var f: func @LOCAL -> @[] -> () -> ()
            
        """.trimIndent()) { out }
    }
    @Test
    fun d05_clo () {
        val out = all("""
            var g: func () -> (func @a1->()->())
            var f: func @LOCAL -> () -> ()
            set f = g ()
        """.trimIndent()
        )
        assert(out == """
            var g: func @[a1] -> () -> func @a1 -> @[] -> () -> ()
            var f: func @LOCAL -> @[] -> () -> ()
            set f = (g @[LOCAL] ())
            
        """.trimIndent()) { out }
    }
    @Test
    fun d06_clo () {
        val out = all("""
            var g: func () -> (func @a1->()->())
            var f: func @LOCAL -> () -> ()
            set f = g ()
            call f ()
        """.trimIndent()
        )
        assert(out == """
            var g: func @[a1] -> () -> func @a1 -> @[] -> () -> ()
            var f: func @LOCAL -> @[] -> () -> ()
            set f = (g @[LOCAL] ())
            call (f @[] ())
            
        """.trimIndent()) { out }
    }
    @Test
    fun d07_clo () {
        val out = all("""
            var cnst = func @[a1]->/_int@a1 -> (func @a1->()->/_int@a1) {
                var x: /_int@a1 = arg
                return func @a1->()->/_int@a1 [x] {
                    return x
                }
            }
        """.trimIndent())
        assert(out == """
            var cnst: func @[a1] -> /_int@a1 -> func @a1 -> @[] -> () -> /_int@a1
            set cnst = func @[a1] -> /_int@a1 -> func @a1 -> @[] -> () -> /_int@a1 {
            var x: /_int@a1
            set x = arg
            set ret = func @a1 -> @[] -> () -> /_int@a1 [x] {
            set ret = x
            return
            }
            
            return
            }
            
            
        """.trimIndent()) { out }
    }
    @Test
    fun d08_clo () {
        val out = all("""
            var g: func @[a]->/_int@a -> (func @a->()->/_int@a)
            var five: _int
            var f: func @LOCAL->()->/_int@LOCAL = g /five
            var v: /_int = f ()
        """.trimIndent())
        assert(out == """
            var g: func @[a] -> /_int@a -> func @a -> @[a] -> () -> /_int@a
            var five: _int
            var f: func @LOCAL -> @[] -> () -> /_int@LOCAL
            set f = (g @[LOCAL] (/five))
            var v: /_int@LOCAL
            set v = (f @[LOCAL] (): @LOCAL)

        """.trimIndent()) { out }
    }
    @Test
    fun d09_clo () {
        val out = all("""
            var g: func @[a]->/_int@a -> (func @a->()->/_int@a)
            {
                var five: _int
                var f: func @LOCAL->()->/_int@LOCAL = g /five
                var v: /_int = f ()
            }
        """.trimIndent())
        assert(out == """
            var g: func @[a] -> /_int@a -> func @a -> @[a] -> () -> /_int@a
            { @SSFIVE
            var five: _int
            var f: func @LOCAL -> @[] -> () -> /_int@LOCAL
            set f = (g @[LOCAL] (/five))
            var v: /_int@LOCAL
            set v = (f @[LOCAL] (): @LOCAL)
            }

        """.trimIndent()) { out }
    }

    // NUMS

    @Test
    fun e01_clone () {
        val out = all("""
            var clone : func /</^> -> /</^>
            set clone = func /</^> -> /</^> {
                if arg\?0 {
                    return <.0>
                } else {
                    return new <.1 clone arg\!1>
                }
            }
        """.trimIndent())
        assert(out == """
            var clone: func @[@i1,@j1] -> /</^@i1>@i1 -> /</^@j1>@j1
            set clone = func @[@i1,@j1] -> /</^@i1>@i1 -> /</^@j1>@j1 {
            if ((arg\)?0){
            {
            set ret = <.0>: /</^@j1>@j1
            return
            }
            } else {
            {
            set ret = (new <.1 (clone @[@i1,@j1] ((arg\)!1): @j1)>: </^@j1>: @j1)
            return
            }
            }
            }


        """.trimIndent()) { out }
    }
    @Test
    fun e02_ff () {
        val out = all("""
            var f : func /</^> -> /</^>
            output std f (f <.0>)
        """.trimIndent())
        assert(out == """
            var f: func @[@i1,@j1] -> /</^@i1>@i1 -> /</^@j1>@j1
            output std (f @[@LOCAL,@LOCAL] (f @[@LOCAL,@LOCAL] <.0>: /</^@LOCAL>@LOCAL: @LOCAL): @LOCAL)

        """.trimIndent()) { out }
    }
}