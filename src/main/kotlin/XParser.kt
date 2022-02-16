import java.io.PushbackReader
import java.io.StringReader

class XParser: Parser()
{
    override fun type(tasks: Boolean): Type {
        return when {
            all.accept(TK.XID) -> {
                val tk0 = all.tk0.astype()
                val scps = if (all.accept(TK.ATBRACK)) {
                    val ret = this.scp1s { it.asscope() }
                    all.accept_err(TK.CHAR, ']')
                    ret
                } else {
                    null
                }
                Type.Alias(tk0, false, scps?.map { Scope(it,null) })
            }
            all.accept(TK.CHAR, '/') -> {
                val tk0 = all.tk0 as Tk.Chr
                val pln = this.type(false)
                val scp = if (all.accept(TK.CHAR, '@')) {
                    all.accept_err(TK.XID)
                    all.tk0.asscope()
                } else {
                    null
                }
                Type.Pointer(tk0, if (scp==null) null else Scope(scp,null), pln)
            }
            all.accept(TK.FUNC) || all.accept(TK.TASK) || (tasks && all.accept(TK.TASKS)) -> {
                val tk0 = all.tk0 as Tk.Key
                val clo = if (all.accept(TK.CHAR, '@')) {
                    all.accept_err(TK.XID)
                    val tk = all.tk0.asscope()
                    all.accept_err(TK.ARROW)
                    tk
                } else {
                    null
                }
                val (scps, ctrs) = if (all.check(TK.ATBRACK)) {
                    val (x, y) = this.scopepars()
                    all.accept_err(TK.ARROW)
                    Pair(x, y)
                } else {
                    Pair(null, null)
                }

                // input & pub & output
                val inp = this.type(false)
                val pub = if (tk0.enu != TK.FUNC) {
                    all.accept_err(TK.ARROW)
                    this.type(false)
                } else null
                all.accept_err(TK.ARROW)
                val out = this.type(false) // right associative

                Type.Func(tk0,
                    Triple(
                        clo.let { if (it==null) null else Scope(it,null) },
                        if (scps==null) null else scps.map { Scope(it,null) },
                        ctrs),
                    inp, pub, out)
            }
            else -> super.type(tasks)
        }
    }

    override fun expr_one (): Expr {
        return when {
            all.accept(TK.XNAT) -> {
                val tk0 = all.tk0 as Tk.Nat
                val tp = if (!all.accept(TK.CHAR, ':')) null else {
                    this.type(false)
                }
                Expr.Nat(tk0, tp)
            }
            all.accept(TK.CHAR, '<') -> {
                all.accept_err(TK.CHAR, '.')
                all.accept_err(TK.XNUM)
                val tk0 = all.tk0 as Tk.Num
                val cons = when {
                    (tk0.num == 0) -> null
                    all.check(TK.CHAR, '>') -> Expr.Unit(Tk.Sym(TK.UNIT, all.tk1.lin, all.tk1.col, "()"))
                    else -> this.expr()
                }
                all.accept_err(TK.CHAR, '>')
                val tp = if (!all.accept(TK.CHAR, ':')) null else this.type(false)
                if (tk0.num == 0) {
                    Expr.UNull(tk0, tp)
                } else {
                    Expr.UCons(tk0, tp, cons!!)
                }
            }
            all.accept(TK.NEW) -> {
                val tk0 = all.tk0
                val e = this.expr()
                all.assert_tk(tk0, e is Expr.UCons && e.tk_.num != 0) {
                    "invalid `new` : expected constructor"
                }

                val scp = if (all.accept(TK.CHAR, ':')) {
                    all.accept_err(TK.CHAR, '@')
                    all.accept_err(TK.XID)
                    all.tk0.asscope()
                } else {
                    null
                }
                Expr.New(tk0 as Tk.Key, if (scp==null) null else Scope(scp,null), e as Expr.UCons)
            }
            else -> super.expr_one()
        }
    }

    override fun expr (): Expr {
        var e = this.expr_dots()

        // call
        if (all.checkExpr() || all.check(TK.ATBRACK)) {
            val iscps = if (!all.accept(TK.ATBRACK)) null else {
                val ret = this.scp1s { it.asscope() }
                all.accept_err(TK.CHAR, ']')
                ret
            }
            val arg = this.expr()
            val oscp = if (!all.accept(TK.CHAR, ':')) null else {
                all.accept_err(TK.CHAR, '@')
                all.accept_err(TK.XID)
                all.tk0.asscope()
            }
            e = Expr.Call(e.tk, e, arg,
                Pair(
                    if (iscps==null) null else iscps.map { Scope(it,null) },
                    if (oscp==null) null else Scope(oscp,null)
                ))
        }
        return e
    }

    override fun stmt(): Stmt {
        return when {
            all.accept(TK.VAR) -> {
                all.accept_err(TK.XID)
                val tk_id = all.tk0 as Tk.Id
                val tp = if (!all.accept(TK.CHAR, ':')) null else {
                    this.type(false)
                }
                if (all.accept(TK.CHAR, '=')) {
                    val tk0 = all.tk0 as Tk.Chr
                    val dst = Expr.Var(tk_id)
                    val src = when {
                        all.check(TK.INPUT) -> {
                            all.accept(TK.INPUT)
                            val tk = all.tk0 as Tk.Key
                            all.accept_err(TK.XID)
                            val lib = (all.tk0 as Tk.Id)
                            val arg = this.expr()
                            val inp = if (!all.accept(TK.CHAR, ':')) null else this.type(false)
                            Stmt.Input(tk, inp, dst, lib, arg)
                        }
                        all.check(TK.SPAWN) -> {
                            val s = this.stmt()
                            All_assert_tk(s.tk, s is Stmt.SSpawn) { "unexpected dynamic `spawn`" }
                            val ss = s as Stmt.SSpawn
                            Stmt.SSpawn(ss.tk_, dst, ss.call)
                        }
                        else -> {
                            val src = this.expr()
                            Stmt.Set(tk0, dst, src)
                        }
                    }
                    Stmt.Seq(tk_id, Stmt.Var(tk_id, tp), src)
                } else {
                    if (tp == null) {
                        all.err_expected("type declaration")
                    }
                    Stmt.Var(tk_id, tp)
                }
            }
            all.accept(TK.INPUT) -> {
                val tk = all.tk0 as Tk.Key
                all.accept_err(TK.XID)
                val lib = (all.tk0 as Tk.Id)
                val arg = this.expr()
                val tp = if (!all.accept(TK.CHAR, ':')) null else this.type(false)
                Stmt.Input(tk, tp, null, lib, arg)
            }
            all.accept(TK.IF) -> {
                val tk0 = all.tk0 as Tk.Key
                val tst = this.expr()
                val true_ = this.block()
                val false_ = if (all.accept(TK.ELSE)) {
                    this.block()
                } else {
                    Stmt.Block(Tk.Chr(TK.CHAR, all.tk1.lin, all.tk1.col, '{'), false, null, Stmt.Nop(all.tk0))
                }
                Stmt.If(tk0, tst, true_, false_)
            }
            all.accept(TK.RETURN) -> {
                val tk0 = all.tk0 as Tk.Key
                val e = if (all.checkExpr()) {
                    this.expr()
                } else {
                    Expr.Unit(Tk.Sym(TK.UNIT, all.tk1.lin, all.tk1.col, "()"))
                }
                Stmt.Seq(
                    tk0,
                    Stmt.Set(
                        Tk.Chr(TK.CHAR, tk0.lin, tk0.col, '='),
                        Expr.Var(Tk.Id(TK.XID, tk0.lin, tk0.col, "ret")),
                        e
                    ),
                    Stmt.Return(tk0)
                )
            }
            all.accept(TK.TYPE) -> {
                all.accept_err(TK.XID)
                val id = all.tk0.astype()
                val scps = if (all.check(TK.ATBRACK)) this.scopepars() else Pair(null, null)
                all.accept_err(TK.CHAR, '=')
                val tp = this.type(false)
                Stmt.Typedef(id, scps, tp)
            }
            all.accept(TK.SPAWN) -> {
                val tk0 = all.tk0 as Tk.Key
                if (all.check(TK.CHAR,'{')) {
                    val block = this.block()
                    val old = All_nest("""
                        spawn task () -> () -> () {
                            ${block.body.xtostr()}
                        } ()
                        
                    """.trimIndent())
                    val ret = this.stmt()
                    all = old
                    ret
                } else {
                    val e = this.expr()
                    All_assert_tk(tk0, e is Expr.Call) { "expected call expression" }
                    if (all.accept(TK.IN)) {
                        val tsks = this.expr()
                        Stmt.DSpawn(tk0, tsks, e as Expr.Call)
                    } else {
                        Stmt.SSpawn(tk0, null, e as Expr.Call)
                    }
                }
            }
            all.accept(TK.PAR) -> {
                var pars = mutableListOf<Stmt.Block>()
                pars.add(this.block())
                while (all.accept(TK.WITH)) {
                    pars.add(this.block())
                }
                val srcs = pars.map { "spawn { ${it.body.xtostr()} }" }.joinToString("\n")
                val old = All_nest(srcs + "await _0\n")
                val ret = this.stmts()
                all = old
                ret
            }
            all.accept(TK.PARAND) || all.accept(TK.PAROR) -> {
                val op = if (all.tk0.enu==TK.PARAND) "&&" else "||"
                var pars = mutableListOf<Stmt.Block>()
                pars.add(this.block())
                while (all.accept(TK.WITH)) {
                    pars.add(this.block())
                }
                val spws = pars.mapIndexed { i,x -> "var tk_$i = spawn { ${x.body.xtostr()} }" }.joinToString("\n")
                val oks  = pars.mapIndexed { i,_ -> "var ok_$i: _int = _((${D}tk_$i->task0.state == TASK_DEAD))" }.joinToString("\n")
                val sets = pars.mapIndexed { i,_ -> "set ok_$i = _(${D}ok_$i || (((uint64_t)${D}tk_$i)==${D}tk_x))" }.joinToString("\n")
                val chks = pars.mapIndexed { i,_ -> "${D}ok_$i" }.joinToString(" $op ")

                val old = All_nest("""
                    {
                        $spws
                        $oks
                        loop {
                            if _($chks) {
                                break
                            }
                            await evt?2
                            var tk_x = evt!2
                            $sets
                        }
                    }

                """.trimIndent()) //.let{println(it);it})
                val ret = this.stmt()
                all = old
                ret
            }
            else -> super.stmt()
        }
    }
}
