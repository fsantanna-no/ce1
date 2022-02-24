import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class TTask {

    fun all (inp: String): String {
        All_new(PushbackReader(StringReader(inp), 2))
        Lexer.lex()
        val s = XParser().stmts()
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
    fun a01_output () {
        val out = all("""
            var f = task ()->()->() {
                output std _1:_int
            }
            var x = spawn f ()
            output std _2:_int
        """.trimIndent())
        assert(out == "1\n2\n") { out }
    }
    @Test
    fun a02_await_err2 () {
        val out = all("""
            await ()
        """.trimIndent())
        assert(out.startsWith("(ln 1, col 1): invalid condition : type mismatch")) { out }
    }
    @Test
    fun a02_await () {
        val out = all("""
            type Event = <(),_int>
            var f = task ()->()->() {
                output std _1:_int
                await evt?2
                output std _3:_int
            }
            var x = spawn f ()
            output std _2:_int
            --awake x _1:_int
            emit @GLOBAL Event.2 _1:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n") { out }
    }
    @Test
    fun a02_await_err () {
        val out = all("""
            type Event = <(),_int>
            var f = task ()->()->() {
                output std _1:_int
                await evt?2
                output std _3:_int
            }
            var x = spawn f ()
            output std _2:_int
            emit <.2 _1>
            emit <.2 _1>
        """.trimIndent())
        //assert(out.endsWith("Assertion `(global.x)->task0.state == TASK_AWAITING' failed.\n")) { out }
        assert(out.endsWith("1\n2\n3\n")) { out }
    }
    @Test
    fun a03_var () {
        val out = all("""
            type Event = <(),_int>
            var f = task ()->()->() {
                var x = _10:_int
                await evt?2
                output std x
            }
            var x = spawn f ()
            emit <.2 _1>
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun a04_vars () {
        val out = all("""
            type Event = <(),_int>
            var f = task @[]->()->()->() {
                {
                    var x = _10:_int
                    await evt?2
                    output std x
                }
                {
                    var y = _20:_int
                    await evt?2
                    output std y
                }
            }
            var x = spawn f ()
            emit <.2 _1>
            emit <.2 _1>
        """.trimIndent())
        assert(out == "10\n20\n") { out }
    }
    @Test
    fun a05_args_err () {
        val out = all("""
            var f : task ()->()->()
            var x : active task [()]->()->() = spawn f ()
        """.trimIndent())
        assert(out == "(ln 3, col 9): invalid `spawn` : type mismatch :\n    task @[] -> [()] -> () -> ()\n    task @[] -> () -> () -> ()\n") { out }
    }
    @Test
    fun a05_args () {
        val out = all("""
            type Event = <(),_int>
            var f = task @[]->_(char*)->()->() {
                output std arg
                await evt?2
                output std evt!2
                await evt?2
                output std evt!2
            }
            var x = spawn f _("hello")
            emit <.2 _10>
            emit <.2 _20>
        """.trimIndent())
        assert(out == "\"hello\"\n10\n20\n") { out }
    }
    @Test
    fun a06_par_err () {
        val out = all("""
            type Event = <(),_int>
            var build = func () -> task ()->()->() {
                set ret = task ()->()->() {    -- ERR: not the same @LOCAL
                    output std _1:_int
                    await evt?2
                    output std _2:_int
                }
            }
        """.trimIndent())
        assert(out.startsWith("(ln 4, col 9): invalid return : type mismatch")) { out }
    }
    @Test
    fun a06_par1 () {
        val out = all("""
            type Event = <(),_int>
            var build = task ()->()->() {
                output std _1:_int
                await evt?2
                output std _2:_int
            }
            output std _10:_int
            var f = spawn build ()
            output std _11:_int
            var g = spawn build ()
            emit <.2 _1>
            output std _12:_int
        """.trimIndent())
        assert(out == "10\n1\n11\n1\n2\n2\n12\n") { out }
    }
    @Test
    fun a07_emit () {
        val out = all("""
            type Event = <(),_int,_int>
            var f = task ()->()->() {
                await evt?3
                var e = evt!3
                output std _(${D}e+0):_int
            }
            var x = spawn f ()
            
            var g = task @[]->()->()->() {
                await evt?3
                var e1 = evt!3
                output std _(${D}e1+10):_int
                await evt?3
                var e2 = evt!3
                output std _(${D}e2+10):_int
            }
            var y = spawn g ()
            
            emit Event.3 _1
            emit Event.3 _2
        """.trimIndent())
        assert(out == "1\n11\n12\n") { out }
    }
    @Test
    fun a08_emit_block () {
        val out = all("""
            type Event = <(),_int,_int>
            var f = task ()->()->() {
                await evt?1
                var e = evt!1
                output std _0:_int    -- only on kill
            }
            var x = spawn f ()
            
            {
                var g = task ()->()->() {
                    await evt?3
                    var e1 = evt!3
                    output std _(${D}e1+10):_int
                    await evt?3
                    var e2 = evt!3
                    output std _(${D}e2+10):_int
                }
                var y = spawn g ()
                emit @LOCAL <.3 _1>
                emit @LOCAL <.3 _2>
            }            
        """.trimIndent())
        assert(out == "11\n12\n0\n") { out }
    }
    @Test
    fun a09_nest () {
        val out = all("""
            type Event = <(),_int>
            var f = task ()->()->() {
                output std _1:_int
                await evt?2
                var g = task ()->()->() {
                    output std _2:_int
                    await evt?2
                    output std _3:_int
                }
                var xg = spawn g ()
                await evt?2
                output std _4:_int
            }
            var x = spawn f ()
            output std _10:_int
            emit <.2 _1>
            output std _11:_int
            emit <.2 _1>
            output std _12:_int
        """.trimIndent())
        assert(out == "1\n10\n2\n11\n3\n4\n12\n") { out }
    }
    @Test
    fun a10_block_out () {
        val out = all("""
            type Event = <(),_int>
            var f = task ()->()->() {
                output std _10:_int
                {
                    var g = task ()->()->() {
                        output std _20:_int
                        await _1
                        output std _21:_int
                        await _1
                        if evt?1 {
                            output std _0:_int      -- only on kill
                        } else {
                            output std _22:_int     -- can't execute this one
                        }
                    }
                    var y = spawn g ()
                    await evt?2
                }
                output std _11:_int
                var h = task ()->()->() {
                    output std _30:_int
                    await evt?2
                    output std _31:_int
                }
                var z = spawn h ()
                await evt?2
                output std _12:_int
            }
            var x = spawn f ()
            emit <.2 _1>
            emit <.2 _1>
        """.trimIndent())
        assert(out == "10\n20\n21\n0\n11\n30\n31\n12\n") { out }
    }
    @Test
    fun a11_self_kill () {
        val out = all("""
            type Event = <(),_int>
            var g = task ()->()->() {
                var f = task ()->()->() {
                    output std _1:_int
                    await evt?2
                    output std _4:_int
                    emit <.2 _1>
                    output std _999:_int
                }
                var x = spawn f ()
                output std _2:_int
                await evt?2
                output std _5:_int
            }
            output std _0:_int
            var y = spawn g ()
            output std _3:_int
            emit <.2 _1>
            output std _6:_int
       """.trimIndent())
        assert(out == "0\n1\n2\n3\n4\n5\n6\n") { out }
    }
    @Test
    fun a12_self_kill () {
        val out = all("""
            type Event = <(),_int>
            var g = task ()->()->() {
                var f = task ()->()->() {
                    output std _1:_int
                    await evt?2
                    output std _4:_int
                    var kkk = func ()->() {
                        emit <.2 _1>
                    }
                    call kkk ()
                    output std _999:_int
                }
                var x = spawn f ()
                output std _2:_int
                await evt?2
                output std _5:_int
            }
            output std _0:_int
            var y = spawn g ()
            output std _3:_int
            emit <.2 _1>
            output std _6:_int
       """.trimIndent())
        assert(out == "0\n1\n2\n3\n4\n5\n6\n") { out }
    }

    // DEFER

    @Test
    fun b01_defer () {
        val out = all("""
            type Event = <(),_int>
            var f = task ()->()->() {
                var defer = task ()->()->() {
                    await evt?1
                    output std _2:_int
                }
                var xdefer = spawn defer ()
                output std _0:_int
                await evt?2
                output std _1:_int
            }
            var x = spawn f ()
            emit <.2 _1>
        """.trimIndent())
        assert(out == "0\n1\n2\n") { out }
    }
    @Test
    fun b02_defer_block () {
        val out = all("""
            type Event = <(),_int>
            var f = task ()->()->() {
                {
                    var defer = task ()->()->() {
                        await evt?1
                        output std _2:_int
                    }
                    var xdefer = spawn defer ()
                    output std _0:_int
                    await evt?2
                }
                output std _1:_int
            }
            var x = spawn f ()
            emit <.2 _1>
        """.trimIndent())
        assert(out == "0\n2\n1\n") { out }
    }

    // THROW / CATCH

    @Test
    fun c00_err () {
        val out = all("""
            var f : task ()->()->()
            var x : task ()->()->()
            set x = spawn f ()
        """.trimIndent())
        assert(out.startsWith("(ln 3, col 5): invalid `spawn` : type mismatch : expected active task")) { out }
    }
    @Test
    fun c00_throw () {
        val out = all("""
            type Event = <(),_int>
            var h = task ()->()->() {
               catch {
                    var f = task ()->()->() {
                        await _1
                        output std _1:_int
                    }
                    var x = spawn f ()
                    throw
               }
               output std _2:_int
           }
           var z = spawn h ()
           output std _3:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n") { out }
    }
    @Test
    fun c01_throw () {
        val out = all("""
            type Event = <(),_int,_int>
            var h = task ()->()->() {
                catch {
                    var f = task ()->()->() {
                        await evt?3
                        output std _999:_int
                    }
                    var g = task ()->()->() {
                        await _1
                        output std _1:_int
                    }
                    var x = spawn f ()
                    var y = spawn g ()
                    output std _0:_int
                    throw
                    output std _999:_int
                }
                output std _2:_int
           }
           var z = spawn h ()
           output std _3:_int
        """.trimIndent())
        assert(out == "0\n1\n2\n3\n") { out }
    }
    @Test
    fun c02_throw_par2 () {
        val out = all("""
            type Event = <(),_int,_int>
            var main = task ()->()->() {
                var fg = task ()->()->() {
                    var f = task ()->()->() {
                        await evt?3
                        output std _999:_int
                    }
                    var g = task ()->()->() {
                        await evt?1
                        output std _2:_int
                    }
                    await evt?3
                    var xf = spawn f ()
                    var xg = spawn g ()
                    throw
                }
                var h = task ()->()->() {
                    await evt?1
                    output std _1:_int
                }
                var xfg : active task @[]->()->()->()
                var xh : active task @[]->()->()->()
                catch {
                    set xfg = spawn fg ()
                    set xh = spawn h ()
                    emit <.3 _5>
                    output std _999:_int
                }
            }
            var xmain = spawn main ()
            output std _3:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n") { out }
    }
    @Test
    fun c03_throw_func () {
        val out = all("""
            type Event = <(),_int>
            var err = func ()->() {
                throw
            }
            var h = task ()->()->() {
               catch {
                    var f = task ()->()->() {
                        await _1
                        output std _1:_int
                    }
                    var xf: active task ()->()->()
                    set xf = spawn f ()
                    call err ()
                    output std _999:_int
               }
               output std _2:_int
           }
           var xh = spawn h ()
           output std _3:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n") { out }
    }

    // FIELDS

    @Test
    fun d01_field () {
        val out = all("""
            var f = task ()->_int->() {
                set pub = _3
                output std _1:_int
            }
            var xf = spawn f ()
            output std _2:_int
            output std xf.pub
            set xf.pub = _4
            output std xf.pub
        """.trimIndent())
        assert(out == "1\n2\n3\n4\n") { out }
    }

    // SPAWN / DYNAMIC

    @Test
    fun e01_spawn () {
        val out = all("""
            spawn task @[]->()->()->() {
                output std ()
            } ()
        """.trimIndent())
        //assert(out == "(ln 2, col 5): expected `in` : have end of file") { out }
        assert(out == "()\n") { out }
    }
    @Test
    fun e01_spawn_err2 () {
        val out = all("""
            var f : func ()->()
            var fs : active {} task ()->()->()
            spawn f () in fs
        """.trimIndent())
        assert(out == "(ln 3, col 8): invalid `spawn` : type mismatch : expected task : have func @[] -> () -> ()\n") { out }
    }
    @Test
    fun e01_spawn_err3 () {
        val out = all("""
            var f : task ()->()->()
            spawn f () in ()
        """.trimIndent())
        assert(out == "(ln 2, col 21): invalid `spawn` : type mismatch : expected active tasks : have ()\n") { out }
    }
    @Test
    fun e01_spawn_err4 () {
        val out = all("""
            var f : task ()->()->()
            var fs : active {} task [()]->()->()
            spawn f () in fs
        """.trimIndent())
        assert(out == "(ln 3, col 1): invalid `spawn` : type mismatch :\n    task @[] -> [()] -> () -> ()\n    task @[] -> () -> () -> ()\n") { out }
    }
    @Test
    fun e02_spawn_free () {
        val out = all("""
            type Event = <(),_int>
            var f = task ()->()->() {
                output std _1:_int
                await evt?2
                output std _3:_int
            }
            var fs : active {} task ()->()->()
            spawn f () in fs
            output std _2:_int
            emit <.2 _1>
            output std _4:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n4\n") { out }
    }

    // POOL / TASKS / LOOPT

    @Test
    fun f01_err () {
        val out = all("""
            var xs: active {} task ()->_int->()
            var x:  task ()->_int->()
            loop x in xs {
            }
        """.trimIndent())
        assert(out == "(ln 4, col 6): invalid `loop` : type mismatch : expected task type : have task @[] -> () -> _int -> ()\n") { out }

    }
    @Test
    fun f02_err () {
        val out = all("""
            var xs: active {} task [()]->_int->()
            var x:  active task ()->_int->()
            loop x in xs {
            }
        """.trimIndent())
        assert(out == "(ln 4, col 1): invalid `loop` : type mismatch :\n    active task @[] -> () -> _int -> ()\n    active {} task @[] -> [()] -> _int -> ()\n") { out }

    }
    @Test
    fun f03_err () {
        val out = all("""
            var x: ()
            loop x in () {
            }
        """.trimIndent())
        assert(out == "(ln 3, col 6): invalid `loop` : type mismatch : expected task type : have ()\n") { out }
    }
    @Test
    fun f04_err () {
        val out = all("""
            var x: active task ()->_int->()
            loop x in () {
            }
        """.trimIndent())
        assert(out == "(ln 3, col 11): invalid `loop` : type mismatch : expected tasks type : have ()\n") { out }
    }

    @Test
    fun f05_loop () {
        val out = all("""
            var fs: active {} task ()->_int->()
            var f: active task ()->_int->()
            loop f in fs {
            }
            output std ()
        """.trimIndent())
        assert(out == "()\n") { out }
    }

    @Test
    fun f06_pub () {
        val out = all("""
            type Event = <(),_int,_int>
            var f = task ()->_int->() {
                set pub = _3
                output std _1:_int
                await evt?3
            }
            var fs: active {} task ()->_int->()
            spawn f () in fs
            var x: active task ()->_int->()
            loop x in fs {
                output std x.pub
            }
        """.trimIndent())
        assert(out == "1\n3\n") { out }
    }

    @Test
    fun f07_kill () {
        val out = all("""
            var f : task ()->_int->()
            set f = task ()->_int->() {
                set pub = _3
                output std _1:_int
            }
            var fs: active {} task ()->_int->()
            spawn f () in fs
            var x: active task ()->_int->()
            loop x in fs {
                output std x.pub
            }
        """.trimIndent())
        assert(out == "1\n") { out }
    }

    @Test
    fun f08_natural () {
        val out = all("""
            type Event = <(),_int,_int>
            var f = task _int->_int->() {
                set pub = arg
                output std pub
                await evt?3
            }
            var g = task _int->_int->() {
                set pub = arg
                output std pub
                await evt?3
                await evt?3
            }

            var xs: active {} task _int->_int->()
            spawn f _1 in xs
            spawn g _2 in xs

            var x: active task _int->_int->()
            loop x in xs {
                output std x.pub
            }
            
            emit <.3 _10>
            
            loop x in xs {
                output std x.pub
            }
            
            output std ()
        """.trimIndent())
        assert(out == "1\n2\n1\n2\n2\n()\n") { out }
    }

    //@Disabled
    @Test   // TODO: can't kill itself b/c i becomes dangling
    fun todo_f09_dloop_kill () {
        val out = all("""
            var f = task ()->_int->() {
                set pub = _3
                output std _1:_int
                await _1
            }
            var fs: active {} task ()->_int->()
            spawn f () in fs
            var x: active task ()->_int->()
            loop x in fs {
                emit <.2 _5>
                output std x.pub
            }
        """.trimIndent())
        assert(out == "1\n") { out }
    }

    @Test
    fun f10_task_type () {
        val out = all("""
            type Xask = task ()->()->()
            type Xunc = func ()->()
            var t = Xask {
                output std _1:_int
            }
            var f = Xunc {
                output std _2:_int
            }
            var x = spawn t ()
            call f () 
            output std _2:_int
        """.trimIndent())
        assert(out == "1\n2\n") { out }
    }
}