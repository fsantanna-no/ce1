import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class TPar {

    fun all (inp: String): String {
        All_restart(null, PushbackReader(StringReader(inp), 2))
        Lexer.lex()
        val s = XParser().stmts()
        s.setUps(null)
        s.setScp1s()
        s.setEnvs(null)
        check_00_after_envs(s)
        s.xinfScp1s()
        check_01_before_tps(s)
        s.xinfTypes(null)
        s.setScp2s()
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
    @Test
    fun a05_spawn_task () {
        val out = all("""
            var t = spawn {
                output std ()
            }
        """.trimIndent())
        //assert(out == "(ln 2, col 5): expected `in` : have end of file") { out }
        assert(out == "()\n") { out }
    }
    @Test
    fun a6_dollar () {
        val out = all("""
            spawn {
                var x: _int
                set x = _10:_int
                spawn {
                    output std _(${D}x): _int
                }
            }
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun a7_anon () {
        val out = all("""
            var t = task () -> _int -> () {
                spawn {
                    set pub = _10
                }
            }
            var xt = spawn t ()
            output std xt.pub
        """.trimIndent())
        assert(out == "10\n") { out }
    }

    // PAR

    @Test
    fun b01_par () {
        val out = all("""
            type Event = <(),_int>
            spawn {
                par {
                    output std ()
                } with {
                    output std ()
                }
                output std ()   -- never printed
            }
        """.trimIndent())
        //assert(out == "(ln 2, col 5): expected `in` : have end of file") { out }
        assert(out == "()\n()\n") { out }
    }
    @Test
    fun b02_parand () {
        val out = all("""
            type Event = <(),_uint64_t,()>
            spawn {
                parand {
                    await evt?3
                    output std _1:_int
                } with {
                    output std _2:_int
                    await evt?3
                }
                output std _3:_int
            }
            emit @GLOBAL <.3 ()>
            output std _4:_int
            
        """.trimIndent())
        assert(out == "2\n1\n3\n4\n") { out }
    }
    @Test
    fun b03_paror () {
        val out = all("""
            type Event = <(),_uint64_t,()>
            spawn {
                paror {
                    await evt?3
                    await evt?3
                    output std _1:_int
                } with {
                    await evt?3
                    output std _2:_int
                }
                output std _3:_int
            }
            emit @GLOBAL <.3 ()>
            output std _4:_int
            
        """.trimIndent())
        assert(out == "2\n3\n4\n") { out }
    }
    @Test
    fun b04_watching () {
        val out = all("""
            type Event = <(),_uint64_t,()>
            spawn {
                watching evt?3 {
                    await _0
                }
                output std ()
            }
            emit @GLOBAL <.3 ()>
            
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun b05_spawn_every () {
        val out = all("""
            type Event = <(),_uint64_t,_int>
            spawn {
                every evt?3 {
                    output std ()
                }
            }
            emit @GLOBAL <.3 _10>
            emit @GLOBAL <.3 _10>
        """.trimIndent())
        assert(out == "()\n()\n") { out }
    }

    // WCLOCK

    @Test
    fun c01_clk () {
        val out = all("""
            type Event = <(),_int,(),(),_int>
            var sub = func [_int,_int] -> _int {
                return _(${D}arg._1 - ${D}arg._2)
            }
            var lte = func [_int,_int] -> _int {
                return _(${D}arg._1 <= ${D}arg._2)
            }
            spawn {
                output std _1:_int
                await 1s
                output std _4:_int
            }
            output std _2:_int
            emit @GLOBAL Event.5 _999
            output std _3:_int
            emit @GLOBAL Event.5 _1
            output std _5:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n4\n5\n") { out }
    }
    @Test
    fun c02_clk () {
        val out = all("""
            type Event = <(),_int,(),(),_int>
            var sub = func [_int,_int] -> _int {
                return _(${D}arg._1 - ${D}arg._2)
            }
            var lte = func [_int,_int] -> _int {
                return _(${D}arg._1 <= ${D}arg._2)
            }
            spawn {
                every 1s {
                    output std _1:_int
                }
            }
            emit @GLOBAL Event.5 _1000
            emit @GLOBAL Event.5 _1000
            emit @GLOBAL Event.5 _1000
        """.trimIndent())
        assert(out == "1\n1\n1\n") { out }
    }

    // PAUSE

    @Test
    fun d01_pause () {
        val out = all("""
            type Event = <(),_uint64_t,_int,()>
            spawn {
                pauseif evt?3 {
                    output std _1:_int
                    await evt?4
                    output std _5:_int
                }
            }
            output std _2:_int
            emit @GLOBAL Event.3 _1
            output std _3:_int
            emit @GLOBAL Event.4
            emit @GLOBAL Event.3 _0
            output std _4:_int
            emit @GLOBAL Event.4
        """.trimIndent())
        assert(out == "1\n2\n3\n4\n5\n") { out }
    }

}