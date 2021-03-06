import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class TInfer {

    fun all (inp: String): String {
        All_restart(null, PushbackReader(StringReader(inp), 2))
        N = 1
        Lexer.lex()
        try {
            val s = XParser().stmts()
            s.setUps(null)
            s.setScp1s()
            s.setEnvs(null)
            //println(s.xtostr())
            check_00_after_envs(s)
            s.xinfScp1s()
            check_01_before_tps(s)
            s.xinfTypes(null)
            s.setScp2s()
            return s.tostr()
        } catch (e: Throwable) {
            if (THROW) {
                throw e
            }
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
            set l = (new (<.1 <.0>: /List @[GLOBAL] @GLOBAL>: </List @[GLOBAL] @GLOBAL>:+ List @[GLOBAL]): @GLOBAL)
            output std l

        """.trimIndent()) { out }
    }
    @Test
    fun a11_new () {
        val out = all("""
            type List = </List>
            var l = new List.1 <.0>
            --var l = new <.1 <.0>>:List
            output std l
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            var l: /List @[GLOBAL] @GLOBAL
            set l = (new (<.1 <.0>: /List @[GLOBAL] @GLOBAL>: </List @[GLOBAL] @GLOBAL> :+ List @[GLOBAL]): @GLOBAL)
            output std l

        """.trimIndent()) { out }
    }
    @Test
    fun a12_new () {
        val out = all("""
            type List = <Cons=/List>
            var l = new List.Cons <.0>
            --var l = new <.1 <.0>>:List
            output std l
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            var l: /List @[GLOBAL] @GLOBAL
            set l = (new (<.1 <.0>: /List @[GLOBAL] @GLOBAL>: </List @[GLOBAL] @GLOBAL> :+ List @[GLOBAL]): @GLOBAL)
            output std l

        """.trimIndent()) { out }
    }
    @Test
    fun a12_ucons () {
        val out = all("""
            type Xx = <()>
            var y = Xx.2 ()
        """.trimIndent())
        assert(out == "(ln 2, col 12): invalid union constructor : out of bounds") { out }
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
        //assert(out == "(ln 3, col 10): invalid inference : undetermined type") { out }
        assert(out == "(ln 3, col 15): invalid inference : type mismatch") { out }
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
            set (((arg\):- List @[j])!1) = <.0>: /List @[j] @j
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
            set (((arg\):- List @[j])!1) = <.0>: /List @[j] @j
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
            set (((arg\):- List @[j])!1) = (new (<.1 <.0>: /List @[j] @j>: </List @[j] @j>:+ List @[j]): @j)
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
            type List @[i] = /</List @[i] @i> @i
            var f: func @[i] -> List @[i] -> ()
            call (f @[GLOBAL] <.0>: List @[GLOBAL])

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
    @Test
    fun c11_rec_ptr () {
        val out = all("""
            type List = </List>
            { @A
                var x: /List @[A]
            }
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            { @A
            var x: /List @[A] @A
            }

        """.trimIndent()) { out }
    }
    @Test
    fun c12_rec_ptr () {
        val out = all("""
            type List = </List>
            { @A
                var x: /List @GLOBAL
            }
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            { @A
            var x: /List @[GLOBAL] @GLOBAL
            }

        """.trimIndent()) { out }
    }
    @Test
    fun c12_rec_ptr2 () {
        val out = all("""
            type List = </List>
            { @A
                { @B
                    var x: /List @A
                }
            }
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            { @A
            { @B
            var x: /List @[A] @A
            }
            }

        """.trimIndent()) { out }
    }
    @Test
    fun c13_rec_ptr () {
        val out = all("""
            type List = </List>
            var f: func @[a] -> /List @[a] -> ()
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            var f: func @[a] -> /List @[a] @a -> ()

        """.trimIndent()) { out }
    }
    @Test
    fun c14_rec_ptr () {
        val out = all("""
            type List = </List>
            var f: func @[a] -> /List @a -> ()
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            var f: func @[a] -> /List @[a] @a -> ()

        """.trimIndent()) { out }
    }
    @Test
    fun c15_rec_ptr () {
        val out = all("""
            type List = </List>
            var f: func @[i] -> /List @[i] -> ()
            { @A
                var x: /List @[A]
                { @B
                    var g: func /List @[i] -> ()
                    var y: /List @A
                    var z: /List @[A]
                }
            }
        """.trimIndent())
        assert(out == """
            type List @[i] = </List @[i] @i>
            var f: func @[i] -> /List @[i] @i -> ()
            { @A
            var x: /List @[A] @A
            { @B
            var g: func @[i] -> /List @[i] @i -> ()
            var y: /List @[A] @A
            var z: /List @[A] @A
            }
            }

        """.trimIndent()) { out }
    }

    // CLOSURE

    @Test
    fun noclo_d00_clo () {
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
            {
            var x: ()
            set x = ()
            var f: func @[] -> () -> ()
            set f = func @[] -> () -> () {
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
            set pa = (new (<.1 <.0>: /List @[LOCAL] @LOCAL>: </List @[LOCAL] @LOCAL>:+ List @[LOCAL]): @LOCAL)
            var f: func @[] -> () -> ()
            set f = func @[] -> () -> () {

            }
            
            call (f @[] ())
            }
            
        """.trimIndent()) { out }
    }
    @Test
    fun d03_clo () {
        val out = all("""
            var f: func ()->()
        """.trimIndent()
        )
        assert(out == """
            var f: func @[] -> () -> ()
            
        """.trimIndent()) { out }
    }
    @Test
    fun noclo_d10_clo () {
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
            {
            var x: ()
            set x = ()
            {
            var f: func @[] -> () -> ()
            set f = func @[] -> () -> () {
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
            if ((arg\)?0)
            {
            set ret = <.0>: /List @[l] @k
            return
            }
            else
            {
            set ret = (new (<.1 (clone @[j,j,l,l] (((arg\):- List @[j])!1): @l)>: </List @[l] @l>:+ List @[l]): @k)
            return
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
    @Test
    fun e07_ptr_num() {
        val out = all("""
            type Num = /<Num>    
            var zero:  Num = <.0>
            var one:   Num = new <.1 zero>
        """.trimIndent())
        assert(out == """
            type Num @[i] = /<Num @[i]> @i
            var zero: Num @[GLOBAL]
            set zero = <.0>: Num @[GLOBAL]
            var one: Num @[GLOBAL]
            set one = (new <.1 zero>: <Num @[GLOBAL]>: @GLOBAL)

        """.trimIndent()) { out }
    }

    // CLOSURE ERRORS

    @Test
    fun noclo_f01 () {
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
            set pa = (new (<.1 <.0>: /List @[A] @A>: </List @[A] @A>:+ List @[A]): @A)
            var f: func @[] -> () -> ()
            set f = func @[] -> () -> () {
            var pf: /List @[A] @A
            set pf = (new (<.1 <.0>: /List @[A] @A>: </List @[A] @A>:+ List @[A]): @A)
            set (((pa\):- List @[A])!1) = pf
            }
            
            call (f @[] ())
            output std pa
            }
           
        """.trimIndent()) { out }
    }
    @Test
    fun noclo_f05 () {
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
            {
            var x: _int
            set x = (_10: _int)
            set f = func @[] -> () -> _int {
            set ret = x
            return
            }
            
            }
            call (f @[] ())
            
        """.trimIndent()) { out }
    }

    // PAR

    @Test
    fun noclo_g01_spawn () {
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
        assert(out == """
            spawn (task @[] -> _ -> _ -> _ {
            var x: ()
            set x = ()
            spawn (task @[] -> _ -> _ -> _ {
            output std x
            }
             @[] ())
            spawn (task @[] -> _ -> _ -> _ {
            output std x
            }
             @[] ())
            }
             @[] ())

        """.trimIndent()) { out }
    }
    @Test
    fun noclo_g02_spawn_spawn () {
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
        assert(out == """
            spawn (task @[] -> _ -> _ -> _ {
            var x: ()
            set x = ()
            spawn (task @[] -> _ -> _ -> _ {
            spawn (task @[] -> _ -> _ -> _ {
            output std x
            }
             @[] ())
            }
             @[] ())
            spawn (task @[] -> _ -> _ -> _ {
            output std x
            }
             @[] ())
            }
             @[] ())

        """.trimIndent()) { out }
    }
    @Test
    fun noclo_g03_spawn_task () {
        val out = all("""
            var t = spawn {
                output std ()
            }
        """.trimIndent())
        assert(out == """
            var t: active task @[] -> _ -> _ -> _
            set t = spawn (task @[] -> _ -> _ -> _ {
            output std ()
            }
             @[] ())
            
        """.trimIndent()) { out }
    }
    @Test
    fun g04_task_type () {
        val out = all("""
            type Xask = task ()->_int->()
            var t : Xask
            set t = Xask {
                output std _2:_int
            }
            output std _1:_int
            var x : active Xask
            set x = spawn t ()
            var y = spawn t ()
            output std x.pub
            output std _3:_int
        """.trimIndent())
        assert(out == """
            type Xask @[] = task @[] -> () -> _int -> ()
            var t: Xask
            set t = (task @[] -> () -> _int -> () {
            output std (_2: _int)
            }
             :+ Xask)
            output std (_1: _int)
            var x: active Xask
            set x = spawn ((t:- Xask) @[] ())
            var y: active Xask
            set y = spawn ((t:- Xask) @[] ())
            output std ((x:- Xask).pub)
            output std (_3: _int)
            
        """.trimIndent()) { out }
    }
    @Test
    fun g05_task_type () {
        val out = all("""
            type Xask = task ()->()->()
            var t : Xask
            var xs : active {} Xask
            spawn t () in xs
        """.trimIndent())
        assert(out == """
            type Xask @[] = task @[] -> () -> () -> ()
            var t: Xask
            var xs: active {} Xask
            spawn ((t:- Xask) @[] ()) in xs

        """.trimIndent()) { out }
    }

    // WHERE / UNTIL / WCLOCK

    @Test
    fun h01_err () {
        val out = all("""
            var x: ()
            set x = y where {
                var y = ()
            }
            output std x
        """.trimIndent())
        assert(out == """
            var x: ()
            {
            var y: ()
            set y = ()
            set x = y
            }
            output std x

        """.trimIndent()) { out }
    }

    @Test
    fun h02_var () {
        val out = all("""
            var x = y where {
                var y = ()
            }
            output std x
        """.trimIndent())
        assert(out == """
            var x: ()
            {
            var y: ()
            set y = ()
            set x = y
            }
            output std x
            
        """.trimIndent()) { out }
    }

    @Test
    fun h03_until () {
        val out = all("""
            output std () until _0
        """.trimIndent())
        assert(out == """
            {
            loop {
            output std ()
            if (_0: _int)
            {
            break
            }
            else
            {
            
            }
            }
            }
            
        """.trimIndent()) { out }
    }
    @Test
    fun h04_until_where () {
        val out = all("""
            output std () until x where { var x = () }
        """.trimIndent())
        assert(out == """
            {
            loop {
            output std ()
            {
            var x: ()
            set x = ()
            if x
            {
            break
            }
            else
            {
            
            }
            }
            }
            }
    
        """.trimIndent()) { out }
    }
    @Test   // TODO: should it give an error?
    fun todo_h05_until_var_err () {
        val out = all("""
            var x = () until _0
        """.trimIndent())
        assert(out == """            
        """.trimIndent()) { out }
    }
    @Test
    fun h06_where_until_where () {
        val out = all("""
            output std y where { var y = () } until x where { var x:_int = _1 }
        """.trimIndent())
        assert(out == """
            {
            loop {
            {
            var y: ()
            set y = ()
            output std y
            }
            {
            var x: _int
            set x = (_1: _int)
            if x
            {
            break
            }
            else
            {

            }
            }
            }
            }

        """.trimIndent()) { out }
    }
    @Test
    fun h07_err () {
        val out = all("""
            output std v until _1 where {
                var v = ()
            }
        """.trimIndent())
        assert(out == "(ln 1, col 12): undeclared variable \"v\"") { out }
    }

    @Test
    fun h08_wclock () {
        val out = all("""
            type Event = <(),(),(),(),_int>
            var sub: func [_imt,_int] -> _int
            var lte: func [_imt,_int] -> _int
            spawn {
                await 1s
            }
        """.trimIndent())
        assert(out == """
            type Event @[] = <(),(),(),(),_int>
            var sub: func @[] -> [_imt,_int] -> _int
            var lte: func @[] -> [_imt,_int] -> _int
            spawn (task @[] -> _ -> _ -> _ {
            {
            var ms_8: _int
            set ms_8 = (_1000: _int)
            {
            {
            loop {
            await ((evt:- Event)?5)
            set ms_8 = (sub @[] [ms_8,((evt:- Event)!5)])
            if (lte @[] [ms_8,(_0: _int)])
            {
            break
            }
            else
            {
            
            }
            }
            }
            }
            }
            }
             @[] ())

        """.trimIndent()) { out }
    }
    @Test
    fun h09_wclock () {
        val out = all("""
            type Event = <(),(),(),(),_int>
            var sub: func [_imt,_int] -> _int
            var lte: func [_imt,_int] -> _int
            spawn {
                every 1h5min2s20ms {
                    output std ()
                }
            }
        """.trimIndent())
        assert(out == """
            type Event @[] = <(),(),(),(),_int>
            var sub: func @[] -> [_imt,_int] -> _int
            var lte: func @[] -> [_imt,_int] -> _int
            spawn (task @[] -> _ -> _ -> _ {
            {
            {
            loop {
            {
            var ms_12: _int
            set ms_12 = (_3902020: _int)
            {
            {
            loop {
            await ((evt:- Event)?5)
            set ms_12 = (sub @[] [ms_12,((evt:- Event)!5)])
            if (lte @[] [ms_12,(_0: _int)])
            {
            break
            }
            else
            {

            }
            }
            }
            }
            }
            {
            output std ()
            }
            }
            }
            }
            }
             @[] ())

        """.trimIndent()) { out }
    }

    // TUPLES / TYPE

    @Test
    fun j01_point () {
        val out = all("""
            type Point = [_int,_int]
            var xy: Point = [_1,_2]
            var x = xy.1
        """.trimIndent())
        //assert(out == "(ln 2, col 5): expected `in` : have end of file") { out }
        assert(out == """
            type Point @[] = [_int,_int]
            var xy: Point
            set xy = ([(_1: _int),(_2: _int)]:+ Point)
            var x: _int
            set x = ((xy:- Point).1)
            
        """.trimIndent()) { out }
    }
    @Test
    fun j02 () {
        val out = all("""
            type Point = [_int,_int]
            type Dims  = [_int,_int]
            type Rect  = [Point,Dims]
            var r: Rect = [[_1,_2],[_1,_2]]
            var h = r.2.2
        """.trimIndent())
        assert(out == """
            type Point @[] = [_int,_int]
            type Dims @[] = [_int,_int]
            type Rect @[] = [Point,Dims]
            var r: Rect
            set r = ([([(_1: _int),(_2: _int)]:+ Point),([(_1: _int),(_2: _int)]:+ Dims)]:+ Rect)
            var h: _int
            set h = ((((r:- Rect).2):- Dims).2)
            
        """.trimIndent()) { out }
    }
    @Test
    fun j03 () {
        val out = all("""
            type TPico = <()>
            spawn {
                output std TPico.1
            }
        """.trimIndent())
        assert(out == """
            type TPico @[] = <()>
            spawn (task @[] -> _ -> _ -> _ {
            output std (<.1 ()>: <()> :+ TPico)
            }
             @[] ())
            
        """.trimIndent()) { out }
    }
    @Test
    fun j04 () {
        val out = all("""
            type TPico = <(),[_int,_int]>
            spawn {
                output std TPico.2 [_1,_2]
            }
        """.trimIndent())
        assert(out == """
            type TPico @[] = <(),[_int,_int]>
            spawn (task @[] -> _ -> _ -> _ {
            output std (<.2 [(_1: _int),(_2: _int)]>: <(),[_int,_int]> :+ TPico)
            }
             @[] ())
            
        """.trimIndent()) { out }
    }
    @Test
    fun f06_tst () {
        val out = all("""
            var isPointInsideRect = func Point -> _int {
                return _1
            }
        """.trimIndent())
        assert(out == "(ln 1, col 30): undeclared type \"Point\"") { out }
    }
    @Test
    fun f07_tst () {
        val out = all("""
            var f = func () -> () {                                                 
                return g ()                                                         
            }                                                                       
        """.trimIndent())
        assert(out == "(ln 2, col 12): undeclared variable \"g\"") { out }
    }
    @Test
    fun f08_err_e_not_declared () {
        val out = all("""
            output std e?3
        """.trimIndent())
        assert(out == "(ln 1, col 12): undeclared variable \"e\"") { out }
    }
    @Test
    fun f09_func_alias () {
        val out = all("""
            type Int2Int = func @[] -> _int -> _int
            
            var f: Int2Int
            set f = Int2Int {
                set ret = arg
            } 
            
            var x: _int
            set x = f _10:_int
            
            output std x
       """.trimIndent())
        assert(out == """
            type Int2Int @[] = func @[] -> _int -> _int
            var f: Int2Int
            set f = (func @[] -> _int -> _int {
            set ret = arg
            }
             :+ Int2Int)
            var x: _int
            set x = ((f:- Int2Int) @[] (_10: _int))
            output std x
            
        """.trimIndent()) { out }
    }

    @Test
    fun f10_await_ret () {
        val out = all("""
            type Event = <(),_uint64_t,_int>
            var f = task @[]->_int->()->_int {
                return arg
            }
            spawn {
                var x = await f _1
                output std x
            }
        """.trimIndent())
        assert(out == """
type Event @[] = <(),_uint64_t,_int>
var f: task @[] -> _int -> () -> _int
set f = task @[] -> _int -> () -> _int {
set ret = arg
return
}

spawn (task @[] -> _ -> _ -> _ {
var x: _int
{
var tsk_14: active task @[] -> _int -> () -> _int
set tsk_14 = spawn (f @[] (_1: _int))
var st_14: _int
set st_14 = (tsk_14.state)
if (_(${D}st_14 == TASK_AWAITING): _int)
{
await tsk_14
}
else
{

}
set x = (tsk_14.ret)
}
output std x
}
 @[] ())

        """.trimIndent()) { out }
    }
}