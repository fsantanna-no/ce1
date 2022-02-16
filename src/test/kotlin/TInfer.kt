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
        N = 1
        Lexer.lex()
        try {
            val s = XParser().stmts()
            s.setUps(null)
            s.setScp1s()
            s.setEnvs(null)
            s.xinfScp1s()
            check_01_before_tps(s)
            s.xinfTypes(null)
            s.setScp2s()
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
    fun a03_var () {
        val out = all("var rct = [_1]")
        assert(out == "(ln 1, col 12): invalid inference : undetermined type") { out }
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
            var x: /_int @GLOBAL
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
            var y: /_int @GLOBAL
            set y = (_10: /_int @GLOBAL)
            var x: _int
            set x = ((_y: /_int @GLOBAL)\)
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
            type List @[] = </List @GLOBAL>
            var l: /List @GLOBAL
            set l = <.0>: /List @GLOBAL
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
            var l: /List @[GLOBAL] @GLOBAL
            set l = <.0>: /List @[GLOBAL] @GLOBAL
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
            var l: /List @[GLOBAL] @GLOBAL
            set l = (new <.1 <.0>: /List @[GLOBAL] @GLOBAL>: List @[GLOBAL]: @GLOBAL)
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
            var l: /List @[GLOBAL] @GLOBAL
            set l = (new <.1 <.0>: /List @[GLOBAL] @GLOBAL>: List @[GLOBAL]: @GLOBAL)
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
            var x: /</<[/^^ @GLOBAL,/^ @GLOBAL]> @GLOBAL> @GLOBAL
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
            set ((arg\)!1) = (new <.1 <.0>: /List @[j] @j>: List @[j]: @j)
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
            var v: /List @[GLOBAL] @GLOBAL
            set v = <.0>: /List @[GLOBAL] @GLOBAL

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
            call (f @[GLOBAL] <.0>: List @[GLOBAL])

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
            call (f @[GLOBAL,GLOBAL] <.0>: List @[GLOBAL,GLOBAL])

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
            var v: /List @[GLOBAL] @GLOBAL
            set v = (f @[GLOBAL,GLOBAL,GLOBAL,GLOBAL] <.0>: /List @[GLOBAL] @GLOBAL: @GLOBAL)
            output std (f @[GLOBAL,GLOBAL,GLOBAL,GLOBAL] v: @GLOBAL)

        """.trimIndent()) { out }
    }

    // CLOSURE

    @Test
    fun d00_clo () {
        val out = all("""
            {
                var x = ()
                var f = func () -> () {
                    output std x
                }
                call f ()
            }
        """.trimIndent())
        assert(out == """
            { @X15
            var x: ()
            set x = ()
            var f: func @X15 -> @[] -> () -> ()
            set f = func @X15 -> @[] -> () -> () {
            output std x
            }
            
            call (f @[] ())
            }

        """.trimIndent()) { out }
    }
    @Test
    fun d01_clo () {
        val out = all("""
            type List = </List>
            {
                var pa: /List = new <.1 <.0>>
                var f = func () -> () {
                }
                call f ()
            }
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            {
            var pa: /List @[LOCAL] @LOCAL
            set pa = (new <.1 <.0>: /List @[LOCAL] @LOCAL>: List @[LOCAL]: @LOCAL)
            var f: func @[] -> () -> ()
            set f = func @[] -> () -> () {

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
            var f: func @GLOBAL -> @[] -> () -> ()
            
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
            var f: func @GLOBAL -> @[] -> () -> ()
            
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
            var f: func @GLOBAL -> @[] -> () -> ()
            set f = (g @[GLOBAL] (): @GLOBAL)
            
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
            var f: func @GLOBAL -> @[] -> () -> ()
            set f = (g @[GLOBAL] (): @GLOBAL)
            call (f @[] ())
            
        """.trimIndent()) { out }
    }
    @Test
    fun d07_clo () {
        val out = all("""
            var cnst = func @[a1]->/_int@a1 -> (func @a1->()->/_int@a1) {
                var x: /_int@a1 = arg
                return func @a1->()->/_int@a1 {
                    return x
                }
            }
        """.trimIndent())
        assert(out == "(ln 4, col 16): undeclared variable \"x\"") { out }
        /*
        assert(out == """
            var cnst: func @[a1] -> /_int @a1 -> func @a1 -> @[] -> () -> /_int @a1
            set cnst = func @[a1] -> /_int @a1 -> func @a1 -> @[] -> () -> /_int @a1 {
            var x: /_int @a1
            set x = arg
            set ret = func @a1 -> @[] -> () -> /_int @a1 {
            set ret = x
            return
            }
            
            return
            }
            
            
        """.trimIndent()) { out }
         */
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
            var g: func @[a] -> /_int @a -> func @a -> @[] -> () -> /_int @a
            var five: _int
            var f: func @GLOBAL -> @[] -> () -> /_int @GLOBAL
            set f = (g @[GLOBAL] (/five): @GLOBAL)
            var v: /_int @GLOBAL
            set v = (f @[] (): @GLOBAL)

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
            var g: func @[a] -> /_int @a -> func @a -> @[] -> () -> /_int @a
            { @SSFIVE
            var five: _int
            var f: func @LOCAL -> @[] -> () -> /_int @LOCAL
            set f = (g @[LOCAL] (/five): @LOCAL)
            var v: /_int @LOCAL
            set v = (f @[] (): @LOCAL)
            }

        """.trimIndent()) { out }
    }
    @Test
    fun d10_clo () {
        val out = all("""
            { --@Y
                var x = ()
                {
                    var f = func () -> () {
                        output std x
                    }
                    call f ()
                }
            }
        """.trimIndent())
        assert(out == """
            { @X17
            var x: ()
            set x = ()
            {
            var f: func @X17 -> @[] -> () -> ()
            set f = func @X17 -> @[] -> () -> () {
            output std x
            }
            
            call (f @[] ())
            }
            }

        """.trimIndent()) { out }
    }

    // NUMS

    @Test
    fun e01_clone () {
        val out = all("""
            type List = </List>
            var clone : func /List -> /List
            set clone = func /List -> /List {
                if arg\?0 {
                    return <.0>
                } else {
                    return new <.1 clone arg\!1>
                }
            }
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            var clone: func @[i,j,k,l] -> /List @[j] @i -> /List @[l] @k
            set clone = func @[i,j,k,l] -> /List @[j] @i -> /List @[l] @k {
            if ((arg\)?0){
            {
            set ret = <.0>: /List @[l] @k
            return
            }
            } else {
            {
            set ret = (new <.1 (clone @[j,j,l,l] ((arg\)!1): @l)>: List @[l]: @k)
            return
            }
            }
            }


        """.trimIndent()) { out }
    }
    @Test
    fun e02_ff () {
        val out = all("""
            type List = </List>
            var f : func /List -> /List
            output std f (f <.0>)
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            var f: func @[i,j,k,l] -> /List @[j] @i -> /List @[l] @k
            output std (f @[GLOBAL,GLOBAL,GLOBAL,GLOBAL] (f @[GLOBAL,GLOBAL,GLOBAL,GLOBAL] <.0>: /List @[GLOBAL] @GLOBAL: @GLOBAL): @GLOBAL)

        """.trimIndent()) { out }
    }
    @Test
    fun e03_clo () {
        val out = all(
            """
            var f: func (func ()->()) -> (func @GLOBAL->()->())
        """.trimIndent()
        )
        assert(out == "var f: func @[] -> func @[] -> () -> () -> func @GLOBAL -> @[] -> () -> ()\n") { out }
    }
    @Test
    fun e04_ctrs () {
        val out = all(
            """
            var smaller: func @[a1,a2: a2>a1] -> [/_int@a1,/_int@a2] -> /_int@a2
        """.trimIndent()
        )
        assert(out == "var smaller: func @[a1,a2: a2>a1] -> [/_int @a1,/_int @a2] -> /_int @a2\n") { out }
    }
    @Test
    fun e05_notype() {
        val out = all(
            """
            var zero: /Num = <.0>
            var one:   Num = <.1 zero>
        """.trimIndent()
        )
        assert(out == "(ln 1, col 12): undeclared type \"Num\"") { out }
    }
    @Test
    fun e06_type() {
        val out = all("""
            type List = /<List>
            var l1: List = <.0>
            var l2: List = new <.1 <.0>>
            var l3: List = new <.1 l2>
        """.trimIndent())
        assert(out == """
            type List @[i] = /<List @[i]> @i
            var l1: List @[GLOBAL]
            set l1 = <.0>: List @[GLOBAL]
            var l2: List @[GLOBAL]
            set l2 = (new <.1 <.0>: List @[GLOBAL]>: <List @[GLOBAL]>: @GLOBAL)
            var l3: List @[GLOBAL]
            set l3 = (new <.1 l2>: <List @[GLOBAL]>: @GLOBAL)

        """.trimIndent()) { out }
    }

    // CLOSURE ERRORS

    @Test
    fun f01 () {
        val out = all("""
            type List = </List>
            { @A
                var pa: /List = new <.1 <.0>>
                var f = func ()->() {
                    var pf: /(List @[A])@A = new <.1 <.0>>
                    set pa\!1 = pf
                }
                call f ()
                output std pa
            }
        """.trimIndent())
        //assert(out == "<.1 <.1 <.0>>>\n") { out }
        //assert(out == "(ln 6, col 13): undeclared variable \"pa\"") { out }
        assert(out == """
            type List @[i] = </List @[i] @i>
            { @A
            var pa: /List @[A] @A
            set pa = (new <.1 <.0>: /List @[A] @A>: List @[A]: @A)
            var f: func @A -> @[] -> () -> ()
            set f = func @A -> @[] -> () -> () {
            var pf: /List @[A] @A
            set pf = (new <.1 <.0>: /List @[A] @A>: List @[A]: @A)
            set ((pa\)!1) = pf
            }
            
            call (f @[] ())
            output std pa
            }
           
        """.trimIndent()) { out }
    }
    @Test
    fun f02 () {
        val out = all(
            """
            type List = </List>
            var g = func @[a1] -> () -> (func @a1->()->()) {
                var x: /(List @[a1])@a1 = new <.1 <.0>>
                return func @a1->()->() {
                    output std x
                }
            }
            var f: (func @LOCAL->()->()) = g ()
            call f ()
        """.trimIndent()
        )
        //assert(out == "<.1 <.0>>\n") { out }
        assert(out == "(ln 5, col 20): undeclared variable \"x\"") { out }
    }
    @Test
    fun f03 () {
        val out = all(
            """
            var cnst = func @[a1]->/_int@a1 -> (func @a1->()->/_int@a1) {
                var x: /_int@a1 = arg
                return func @a1->()->/_int@a1 {
                    return x
                }
            }
            {
                var five = _5: _int
                var f: func @LOCAL -> () -> /_int@LOCAL = cnst /five
                var v: /_int = f ()
                output std v\
            }
        """.trimIndent()
        )
        //assert(out == "5\n") { out }
        assert(out == "(ln 4, col 16): undeclared variable \"x\"") { out }
    }
    @Test
    fun f04 () {
        val out = all(
            """
            var f = func (func ()->()) -> (func @GLOBAL->()->()) {
                var ff = arg
                return func @GLOBAL->()->() {
                    call ff ()
                }
            }
            var u = func ()->() {
                output std ()
            }
            var ff = f u
            call ff ()
        """.trimIndent()
        )
        assert(out == "(ln 4, col 14): undeclared variable \"ff\"") { out }
    }
    @Test
    fun f05 () {
        val out = all("""
            var f: func () -> _int          -- 1. `f` is a reference to a function
            {
                var x: _int = _10
                set f = func () -> _int {   -- 2. `f` is created
                    return x                -- 3. `f` needs access to `x`
                }
            }                               -- 4. `x` goes out of scope
            call f ()                       -- 5. `f` still wants to access `x`
        """.trimIndent()
        )
        //assert(out == "()\n") { out }
        assert(out == """
            var f: func @[] -> () -> _int
            { @X14
            var x: _int
            set x = (_10: _int)
            set f = func @X14 -> @[] -> () -> _int {
            set ret = x
            return
            }
            
            }
            call (f @[] ())
            
        """.trimIndent()) { out }
    }

    // PAR

    @Test
    fun g01_spawn () {
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
        assert(out == """
            spawn (task @GLOBAL -> @[] -> () -> () -> () {
            var x: ()
            set x = ()
            spawn (task @LOCAL -> @[] -> () -> () -> () {
            output std x
            }
             @[] ())
            spawn (task @LOCAL -> @[] -> () -> () -> () {
            output std x
            }
             @[] ())
            }
             @[] ())

        """.trimIndent()) { out }
    }
    @Test
    fun g02_spawn_spawn () {
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
        assert(out == """
            spawn (task @GLOBAL -> @[] -> () -> () -> () {
            var x: ()
            set x = ()
            spawn (task @LOCAL -> @[] -> () -> () -> () {
            spawn (task @LOCAL -> @[] -> () -> () -> () {
            output std x
            }
             @[] ())
            }
             @[] ())
            spawn (task @LOCAL -> @[] -> () -> () -> () {
            output std x
            }
             @[] ())
            }
             @[] ())

        """.trimIndent()) { out }
    }

}