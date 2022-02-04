import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File
import java.io.PushbackReader
import java.io.StringReader

val nums = """
    var zero:  /</^> = <.0>
    var one:   /</^> = new <.1 zero>
    var two:   /</^> = new <.1 one>
    var three: /</^> = new <.1 two>
    var four:  /</^> = new <.1 three>
    var five:  /</^> = new <.1 four>
""".trimIndent()

fun Num (ptr: Boolean, scope: String): String {
    val ret = "</^$scope>"
    return if (!ptr) ret else "/"+ret+scope
}
val Num    = "/</^>"
val NumTL  = Num(true,  "@LOCAL")
val NumA1  = Num(true,  "@a1")
val NumA2  = Num(true,  "@a2")
val NumB1  = Num(true,  "@b1")
val NumC1  = Num(true,  "@c1")
val NumR1  = Num(true,  "@r1")
val _NumR1 = Num(false, "@r1")
val NumS1  = Num(true,  "@s1")

val clone = """
    var clone : func $Num -> $Num
    set clone = func $Num -> $Num {
        if arg\?0 {
            return <.0>
        } else {
            return new <.1 clone arg\!1>
        }
    }
""".trimIndent()

val add = """
    var add : func [$Num,$Num] -> $Num
    set add = func [$Num,$Num] -> $Num {
        var x = arg.1
        var y = arg.2
        if y\?0 {
            return clone x
        } else {
            return new <.1 add [x,y\!1]>
        }
    }
""".trimIndent()

val mul = """
    var mul : func [$Num,$Num] -> $Num
    set mul = func [$Num,$Num] -> $Num {
        var x = arg.1
        var y = arg.2
        if y\?0 {
            return <.0>
        } else {
            var z = mul [x, y\!1]
            return add [x,z]
        }
    }
""".trimIndent()

val lt = """
    var lt : func [$Num,$Num] -> _int
    set lt = func [$Num,$Num] -> _int {
        if arg.2\?0 {
            return _0
        } else {
            if arg.1\?0 {
                return _1
            } else {
                return lt [arg.1\!1,arg.2\!1]
            }
        }
    }
""".trimIndent()

val sub = """
    var sub : func [$Num,$Num] -> $Num
    set sub = func [$Num,$Num] -> $Num {
        var x = arg.1
        var y = arg.2
        if x\?0 {
            return <.0>
        } else {
            if y\?0 {
                return clone x
            } else {
                return sub [x\!1,y\!1]
            }
        }
    }
""".trimIndent()

val mod = """
    var mod : func [$Num,$Num] -> $Num
    set mod = func [$Num,$Num] -> $Num {
        if lt arg {
            return clone arg.1
        } else {
            var v = sub arg
            return mod [v,arg.2]
        }
    }    
""".trimIndent()

val eq = """
    var eq : func [$Num,$Num] -> _int
    set eq = func [$Num,$Num] -> _int {
        var x = arg.1
        var y = arg.2
        if x\?0 {
            return y\?0
        } else {
            if y\?0 {
                return _0
            } else {
                return eq [x\!1,y\!1]
            }
        }
    }
""".trimIndent()

val lte = """
    var lte : func  [$Num,$Num] -> _int
    set lte = func  [$Num,$Num] -> _int {
        var islt = lt [arg.1\!1,arg.2\!1]
        var iseq = eq [arg.1\!1,arg.2\!1]
        return _(${D}islt || ${D}iseq)
    }
""".trimIndent()

@TestMethodOrder(Alphanumeric::class)
class TBook {

    fun all (inp: String): String {
        println("nums:  ${nums.count  { it == '\n' }}")
        println("clone: ${clone.count { it == '\n' }}")
        println("add:   ${add.count   { it == '\n' }}")
        println("mul:   ${mul.count   { it == '\n' }}")
        println("lt:    ${lt.count    { it == '\n' }}")
        println("sub:   ${sub.count   { it == '\n' }}")
        println("mod:   ${mod.count   { it == '\n' }}")
        println("eq:    ${eq.count    { it == '\n' }}")
        println("lte:   ${lte.count   { it == '\n' }}")
        println("bton:  ${bton.count  { it == '\n' }}")
        println("ntob:  ${ntob.count  { it == '\n' }}")
        println("or:    ${or.count    { it == '\n' }}")
        println("and:   ${and.count   { it == '\n' }}")

        val all = All_new(PushbackReader(StringReader(inp), 2))
        lexer(all)
        val s = xparser_stmts(all, Pair(TK.EOF,null))
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

    @Test
    fun pre_01_nums() {
        val out = all(
            """
            var zero: /</^> = <.0>
            var one:   </^> = <.1 zero>
            var two:   </^> = <.1 /one>
            output std /two
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun pre_02_add() {
        val out = all(
            """
            $nums
            $clone
            $add
            output std add [two,one]
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }
    @Test
    fun pre_03_clone() {
        val out = all(
            """
            $nums
            $clone
            output std clone two
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun pre_04_mul() {
        val out = all(
            """
            $nums
            $clone
            $add
            $mul
            var x = add [two,one]
            output std mul [two, x]
            --output std mul [two, add [two,one]]
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>\n") { out }
    }
    @Test
    fun pre_05_lt() {
        val out = all(
            """
            $nums
            $lt
            output std lt [two, one]
            output std lt [one, two]
        """.trimIndent()
        )
        assert(out == "0\n1\n") { out }
    }
    @Test
    fun pre_06_sub() {
        val out = all(
            """
            $nums
            $clone
            $add
            $sub
            output std sub [three, two]
        """.trimIndent()
        )
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun pre_07_eq() {
        val out = all(
            """
            $nums
            $eq
            output std eq [three, two]
            output std eq [one, one]
        """.trimIndent()
        )
        assert(out == "0\n1\n") { out }
    }

    // CHAPTER 1.1

    @Test
    fun ch_01_01_square_pg02() {
        val out = all(
            """
            $nums
            $clone
            $add
            $mul
            var square = func $Num -> $Num {
                return mul [arg,arg]
            }
            output std square two
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.0>>>>>\n") { out }
    }

    @Test
    fun ch_01_01_smaller_pg02() {
        val out = all(
            """
            $nums
            $lt
            -- returns narrower scope, guarantees both alive
            var smaller = func [$NumA1,$NumA2] -> $NumA2 {
                if lt arg {
                    return arg.1
                } else {
                    return arg.2
                }
            }
            output std smaller [one,two]
            output std smaller [two,one]
        """.trimIndent()
        )
        assert(out == "<.1 <.0>>\n<.1 <.0>>\n") { out }
    }

    @Test
    fun ch_01_01_delta_pg03() {
        println("TODO")
    }

    // CHAPTER 1.2

    @Test
    fun ch_01_02_three_pg05() {
        val out = all(
            """
            $nums
            var f_three = func $Num -> $Num {
                return three
            }
            output std f_three one
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }
    @Disabled // TODO: infinite loop
    @Test
    fun ch_01_02_infinity_pg05() {
        val out = all(
            """
            var infinity : func () -> $Num
            set infinity = func () -> $Num {
                output std _10:_int
                return new <.1 infinity ()>
            }
            output std infinity ()
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }

    // CHAPTER 1.3

    @Test
    fun ch_01_03_multiply_pg09() {
        val out = all(
            """
            $nums
            $clone
            $add
            $mul
            var multiply = func [$Num,$Num] -> $Num {
                if arg.1\?0 {
                    return <.0>
                } else {
                    return mul [arg.1,arg.2]
                }
            }
            output std multiply [two,three]
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>\n") { out }
    }

    // CHAPTER 1.4

    @Test
    fun ch_01_04_currying_pg11() {
        val out = all(
            """
            $nums
            $clone
            $lt
            -- 19
            var smallerc = func $NumA1 -> (func @a1 -> $Num->$Num) {
                var x = arg
                return func @a1 -> $Num -> $Num [x] {
                    if (lt [x,arg]) {
                        return clone x     -- TODO: remove clone
                    } else {
                        return clone arg   -- TODO: remove clone
                    }
                }
            }
            -- 30
            var f: func @LOCAL -> $Num -> $Num
            set f = smallerc two
            output std f one
            output std f three
        """.trimIndent()
        )
        assert(out == "<.1 <.0>>\n<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun ch_01_04_twice_pg11() {
        val out = all(
            """
            $nums
            $clone
            $add
            $mul
            var square = func $Num -> $Num {
                return mul [arg,arg]
            }
            var twice = func [func $Num->$Num, $Num] -> $Num {
                return arg.1 (arg.1 arg.2)
            }
            output std twice [square,two]
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>>>>>>>>>>>\n") { out }
    }
    @Test
    fun ch_01_04_addc_pg12() {
        val out = all("""
            $nums
            $clone
            $add
            -- 25
            var plusc = func $NumA1 -> (func @a1->$Num->$Num) {
                var x = arg
                return func @a1->$Num->$Num [x] {
                    return add [x,arg]
                }
            }
            var f = plusc one
            output std f two
            output std f one
            output std (plusc one) zero
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n<.1 <.1 <.0>>>\n<.1 <.0>>\n") { out }
    }
    @Test
    fun ch_01_04_quad_pg12() {
        val out = all(
            """
            $nums
            $clone
            $add
            $mul
            var square: func $Num -> $Num
            set square = func $Num -> $Num {
                return mul [arg,arg]
            }
            var twicec = func (func $Num->$Num) -> (func @GLOBAL->$Num->$Num) {
                var f = arg
                return func @GLOBAL->$Num->$Num [f] {
                    return f (f arg)
                }
            }
            var quad = twicec square
            output std quad two
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>>>>>>>>>>>\n") { out }
    }
    @Test
    fun ch_01_04_curry_pg13() {
        val out = all(
            """
            $nums
            $clone
            $add
            var curry = func (func [$Num,$Num] -> $Num) -> (func @GLOBAL -> $NumA1 -> (func @a1->$Num->$Num)) {
                var f = arg
                return func @GLOBAL -> $NumA1 -> (func @a1->$Num->$Num) [f] {
                    var x = arg
                    var ff = f
                    return func @a1->$Num->$Num [ff,x] {
                        var y = arg
                        return ff [x,y]
                    }
                }
            }
            var addc = curry add
            output std (addc  one) two
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }
    @Test
    fun ch_01_04_uncurry_pg13() {
        val fadd  = "func [$NumA1,$Num] -> $Num"
        val fadd2 = "func @GLOBAL -> [$Num,$Num] -> $Num"
        val ret2  = "func @a1 -> $Num -> $Num"
        val ret1  = "func @GLOBAL -> $NumA1 -> $ret2"
        val out = all(
            """
            $nums
            $clone
            $add
            -- 25
            var curry: func $fadd -> $ret1
            set curry = func $fadd -> $ret1 {
                var f = arg
                return $ret1 [f] {
                    var x = arg
                    var ff = f
                    return $ret2 [ff,x] {
                        var y = arg
                        return ff [x,y]
                    }
                }
            }

            var uncurry: func $ret1 -> $fadd2
            set uncurry = func $ret1 -> $fadd2 {
                var f = arg
                return $fadd2 [f] {
                    return (f arg.1) arg.2
                }
            }
            
            var addc = curry add
            var addu = uncurry addc
            output std addu [one,two]
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }
    @Test
    fun ch_01_04_composition_pg15() {
        val out = all(
            """
            $nums
            $clone
            $add
            
            var inc = func $Num -> $Num {
                return add [one,arg]
            }
            output std inc two
            
            var compose = func [func $Num->$Num,func $Num->$Num] -> (func @GLOBAL->$Num->$Num) {
                var f = arg.1
                var g = arg.2
                return func @GLOBAL->$Num->$Num [f,g] {
                    var v = f arg
                    return g v
                }
            }
            output std (compose [inc,inc]) one
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n<.1 <.1 <.1 <.0>>>>\n") { out }
    }

    // CHAPTER 1.5

    @Test
    fun ch_01_05_fact_pg23 () {
        val out = all(
            """
            $nums
            $clone
            $add
            $mul
            
            var fact : func $Num->$Num
            set fact = func $Num->$Num {
                if arg\?0 {
                    return new <.1 <.0>>
                } else {
                    var x = fact arg\!1
                    return mul [arg,x]
                }
            }
            
            output std fact three
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>\n") { out }
    }

    // CHAPTER 1.6
    // CHAPTER 1.7

    // CHAPTER 2.1

    val B = "<(),()>"
    val and = """
        var and = func [$B,$B] -> $B {
            if arg.1?1 {
                return <.1>:<(),()>
            } else {
                return arg.2
            }
        }        
    """.trimIndent()
    val or = """
        var or = func [$B,$B] -> $B {
            if arg.1?2 {
                return <.2>:<(),()>
            } else {
                return arg.2
            }
        }        
    """.trimIndent()
    val not = """
        var not = func <(),()> -> <(),()> {
            if arg?1 {
                return <.2>:<(),()>
            } else {
                return <.1>:<(),()>
            }
        }        
    """.trimIndent()

    val beq = """
        var beq = func [$B,$B] -> $B {
            return or [and arg, and [not arg.1, not arg.2]] 
        }
        var bneq = func [$B,$B] -> $B {
            return not beq arg 
        }        
    """.trimIndent()

    val ntob = """
        var ntob = func _int -> $B {
            if arg {
                return <.2>:$B
            } else {
                return <.1>:$B
            } 
        }
    """.trimIndent()

    val bton = """
        var bton = func $B -> _int {
            if arg?2 {
                return _1: _int
            } else {
                return _0: _int
            } 
        }
    """.trimIndent()

    @Test
    fun ch_02_01_not_pg30 () {
        val out = all(
            """
            var not = func <(),()> -> <(),()> {
                if arg?1 {
                    return <.2>
                } else {
                    return <.1>
                }
            }
            var xxx = not <.1>
            output std /xxx
        """.trimIndent()
        )
        assert(out == "<.2>\n") { out }
    }

    @Test
    fun ch_02_01_and_pg30 () {
        val out = all(
            """
            var and = func [$B,$B] -> $B {
                if arg.1?1 {
                    return <.1>
                } else {
                    return arg.2
                }
            }
            var xxx = and [<.1>,<.2>]
            output std /xxx
            set xxx = and [<.2>,<.2>]
            output std /xxx
        """.trimIndent()
        )
        assert(out == "<.1>\n<.2>\n") { out }
    }
    @Test
    fun ch_02_01_or_pg30 () {
        val out = all(
            """
            var or = func [$B,$B] -> $B {
                if arg.1?2 {
                    return <.2>
                } else {
                    return arg.2
                }
            }
            var xxx = or [<.1>,<.2>]
            output std /xxx
            set xxx = or [<.2>,<.1>]
            output std /xxx
            set xxx = or [<.1>,<.1>]
            output std /xxx
        """.trimIndent()
        )
        assert(out == "<.2>\n<.2>\n<.1>\n") { out }
    }
    @Test
    fun ch_02_01_eq_neq_pg31 () {
        val out = all(
            """
            $not
            $and
            $or
            var eq = func [$B,$B] -> $B {
                return or [and arg, and [not arg.1, not arg.2]]
            }
            var neq = func [$B,$B] -> $B {
                return not eq arg 
            }
            var xxx = eq [<.1>,<.2>]
            output std /xxx
            set xxx = neq [<.2>,<.1>]
            output std /xxx
            set xxx = eq [<.1>,<.1>]
            output std /xxx
        """.trimIndent()
        )
        assert(out == "<.1>\n<.2>\n<.2>\n") { out }
    }

    @Test
    fun ch_02_01_mod_pg33 () {
        val out = all(
            """
            $nums
            $clone
            $add
            $lt
            $sub
            -- 51
            var mod : func [$Num,$Num] -> $Num
            set mod = func [$Num,$Num] -> $Num {
                if lt arg {
                    return clone arg.1
                } else {
                    var v = sub arg
                    return mod [v,arg.2]
                }
            }
            var v = mod [three,two]
            output std v
        """.trimIndent()
        )
        assert(out == "<.1 <.0>>\n") { out }
    }

    @Test
    fun ch_02_01_leap_pg33 () {
        val out = all(
            """
            $nums
            $clone
            $add
            $mul
            $lt
            $sub
            $mod
            $eq
            $or
            $and
            $ntob

            var n10 = mul [five,two]
            var n100 = mul [n10,n10]
            var n400 = mul [four,n100]
            
            var leap = func $Num -> $B {
                var mod4 = mod [arg,four]
                var mod100 = mod [arg,n100]
                var mod400 = mod [arg,n400]
                return or [ntob mod4\?0, and [ntob mod100\?1, ntob mod400\?0]]
            }
            
            var n2000 = mul [n400,five]
            var n20 = add [n10,n10]
            var n1980 = sub [n2000,n20]
            var n1979 = sub [n1980,one]
            var x = leap n1980
            output std /x
            set x = leap n1979
            output std /x
        """.trimIndent()
        )
        assert(out == "<.2>\n<.1>\n") { out }
    }

    @Test
    fun ch_02_01_triangles_pg33 () {
        val Tri = "<(),(),(),()>"
        val out = all(
            """
            $nums
            $clone
            $add
            $mul
            $lt
            $sub
            $eq
            $lte
            $bton
            $ntob
            $or
            -- 119
            var analyse = func [$Num,$Num,$Num] -> $Tri {
                var xy = add [arg.1,arg.2]
                if lte[xy,arg.3] {
                    return <.1>
                }
                if eq [arg.1,arg.3] {
                    return <.2>:$Tri
                }
                if bton (or [
                    ntob (eq [arg.1,arg.2]),
                    ntob (eq [arg.2,arg.3])
                ]) {
                    return <.3>
                }
                return <.4>
            }
            var n10 = mul [five,two]
            var v = analyse [n10,n10,n10]
            output std /v
            set v = analyse [one,five,five]
            output std /v
            set v = analyse [one,one,five]
            output std /v
            set v = analyse [two,four,five]
            output std /v
        """.trimIndent()
        )
        assert(out == "<.2>\n<.3>\n<.1>\n<.4>\n") { out }
    }
}