fun xparser_type (all: All): Type {
    return when {
        all.accept(TK.UNIT) -> Type.Unit(all.tk0 as Tk.Sym)
        all.accept(TK.XNAT) -> Type.Nat(all.tk0 as Tk.Nat)
        all.accept(TK.XUP) -> Type.Rec(all.tk0 as Tk.Up)
        all.accept(TK.CHAR, '/') -> {
            val tk0 = all.tk0 as Tk.Chr
            val pln = xparser_type(all)
            val scp = if (all.accept(TK.XSCPCST) || all.accept(TK.XSCPVAR)) {
                all.tk0 as Tk.Scp1
            } else {
                null
            }
            Type.Ptr(tk0, scp, null, pln)
        }
        all.accept(TK.CHAR, '(') -> {
            val tp = xparser_type(all)
            all.accept_err(TK.CHAR, ')')
            return tp
        }
        all.accept(TK.CHAR, '[') || all.accept(TK.CHAR, '<') -> {
            val tk0 = all.tk0 as Tk.Chr
            val tp = xparser_type(all)
            val tps = arrayListOf(tp)
            while (true) {
                if (!all.accept(TK.CHAR, ',')) {
                    break
                }
                val tp2 = xparser_type(all)
                tps.add(tp2)
            }
            if (tk0.chr == '[') {
                all.accept_err(TK.CHAR, ']')
                Type.Tuple(tk0, tps.toTypedArray())
            } else {
                all.accept_err(TK.CHAR, '>')
                fun f(tp: Type, n: Int): Boolean {
                    return when (tp) {
                        is Type.Ptr -> tp.pln.let {
                            f(it, n) || (it is Type.Rec && n == it.tk_.up)
                        }
                        //is Type.Rec   -> return n <= tp.tk_.up
                        is Type.Tuple -> tp.vec.any { f(it, n) }
                        is Type.Union -> tp.vec.any { f(it, n + 1) }
                        else -> false
                    }
                }

                val vec = tps.toTypedArray()
                val isrec = vec.any { f(it, 1) }
                Type.Union(tk0, isrec, vec)
            }
        }
        all.accept(TK.FUNC) -> {
            val tk0 = all.tk0 as Tk.Key

            // closure
            val clo = if (all.accept(TK.XSCPCST) || all.accept(TK.XSCPVAR)) {
                val tk = all.tk0 as Tk.Scp1
                all.accept_err(TK.ARROW)
                tk
            } else {
                null
            }

            // scopes
            val scp1s = if (!all.accept(TK.CHAR, '{')) null else {
                val ret = mutableListOf<Tk.Scp1>()
                while (all.accept(TK.XSCPVAR)) {
                    ret.add(all.tk0 as Tk.Scp1)
                    if (!all.accept(TK.CHAR, ',')) {
                        break
                    }
                }
                all.accept_err(TK.CHAR, '}')
                all.accept_err(TK.ARROW)
                ret.toTypedArray()
            }

            // input & output
            val inp = xparser_type(all)
            all.accept_err(TK.ARROW)
            val out = xparser_type(all) // right associative

            Type.Func(tk0, Pair(clo,scp1s), null, inp, out)
        }
        else -> {
            all.err_expected("type")
            error("unreachable")
        }
    }
}

fun xparser_expr (all: All): Expr {
    var e = when {
        all.accept(TK.UNIT) -> Expr.Unit(all.tk0 as Tk.Sym)
        all.accept(TK.XVAR) -> Expr.Var(all.tk0 as Tk.Str)
        all.accept(TK.XNAT) -> {
            val tk0 = all.tk0 as Tk.Nat
            val tp = if (!all.accept(TK.CHAR, ':')) null else {
                xparser_type(all)
            }
            Expr.Nat(tk0, tp)
        }
        all.accept(TK.CHAR, '/') -> {
            val tk0 = all.tk0 as Tk.Chr
            val e = xparser_expr(all)
            all.assert_tk(
                all.tk0,
                e is Expr.Nat || e is Expr.Var || e is Expr.TDisc || e is Expr.Dnref || e is Expr.Upref
            ) {
                "unexpected operand to `/´"
            }
            Expr.Upref(tk0, e)
        }
        all.accept(TK.CHAR, '(') -> {
            val e = xparser_expr(all)
            all.accept_err(TK.CHAR, ')')
            e
        }
        all.accept(TK.CHAR, '[') -> {
            val tk0 = all.tk0 as Tk.Chr
            val e = xparser_expr(all)
            val es = arrayListOf(e)
            while (true) {
                if (!all.accept(TK.CHAR, ',')) {
                    break
                }
                val e2 = xparser_expr(all)
                es.add(e2)
            }
            all.accept_err(TK.CHAR, ']')
            Expr.TCons(tk0, es.toTypedArray())
        }
        all.accept(TK.CHAR, '<') -> {
            all.accept_err(TK.CHAR, '.')
            all.accept_err(TK.XNUM)
            val tk0 = all.tk0 as Tk.Num
            val cons = try {
                xparser_expr(all)
            } catch (e: Throwable) {
                assert(!all.consumed(tk0)) {
                    e.message!!
                }
                Expr.Unit(Tk.Sym(TK.UNIT, all.tk1.lin, all.tk1.col, "()"))
            }
            all.accept_err(TK.CHAR, '>')
            val tp = if (!all.accept(TK.CHAR, ':')) null else xparser_type(all)
            Expr.UCons(tk0, tp, cons)
        }
        all.accept(TK.NEW) -> {
            val tk0 = all.tk0
            val e = xparser_expr(all)
            all.assert_tk(tk0, e is Expr.UCons && e.tk_.num != 0) {
                //"invalid `new` : unexpected <.0>"
                "invalid `new` : expected constructor"
            }
            val scp = if (all.accept(TK.CHAR, ':')) {
                all.accept(TK.XSCPCST) || all.accept_err(TK.XSCPVAR)
                all.tk0 as Tk.Scp1
            } else {
                null
            }
            Expr.New(tk0 as Tk.Key, scp, null, e as Expr.UCons)
        }
        all.accept(TK.CALL) -> {
            val tk_pre = all.tk0 as Tk.Key
            val f = xparser_expr(all)

            val scps = mutableListOf<Tk.Scp1>()
            if (all.accept(TK.CHAR, '{')) {
                while (all.accept(TK.XSCPCST) || all.accept(TK.XSCPVAR)) {
                    val tk = all.tk0 as Tk.Scp1
                    scps.add(tk)
                    if (!all.accept(TK.CHAR, ',')) {
                        break
                    }
                }
                all.accept_err(TK.CHAR, '}')
            }

            val arg = xparser_expr(all)

            val scp = if (!all.accept(TK.CHAR, ':')) null else {
                all.accept(TK.XSCPCST) || all.accept_err(TK.XSCPVAR)
                all.tk0 as Tk.Scp1
            }
            Expr.Call(tk_pre, f, arg, Pair(scps.toTypedArray(),scp), null)
        }
        all.accept(TK.INPUT) -> {
            val tk = all.tk0 as Tk.Key
            all.accept_err(TK.XVAR)
            val lib = (all.tk0 as Tk.Str)
            val tp = if (!all.accept(TK.CHAR, ':')) null else xparser_type(all)
            Expr.Inp(tk, tp, lib)
        }
        all.accept(TK.OUTPUT) -> {
            val tk = all.tk0 as Tk.Key
            all.accept_err(TK.XVAR)
            val lib = (all.tk0 as Tk.Str)
            val arg = xparser_expr(all)
            Expr.Out(tk, lib, arg)
        }
        all.check(TK.FUNC) -> {
            val tk = all.tk1 as Tk.Key
            val tp = xparser_type(all) as Type.Func

            val ups: Array<Tk.Str> = if (!all.accept(TK.CHAR,'[')) emptyArray() else {
                val ret = mutableListOf<Tk.Str>()
                while (all.accept(TK.XVAR)) {
                    ret.add(all.tk0 as Tk.Str)
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                }
                all.accept_err(TK.CHAR,']')
                ret.toTypedArray()
            }

            val block = xparser_block(all)
            Expr.Func(tk, tp, ups, block)
        }
        else -> {
            all.err_expected("expression")
            error("unreachable")
        }
    }

    // one!1\.2?1
    while (all.accept(TK.CHAR,'\\') || all.accept(TK.CHAR, '.') || all.accept(TK.CHAR, '!') || all.accept(TK.CHAR, '?')) {
        val chr = all.tk0 as Tk.Chr
        e = if (chr.chr == '\\') {
            all.assert_tk(all.tk0, e is Expr.Nat || e is Expr.Var || e is Expr.TDisc || e is Expr.UDisc || e is Expr.Dnref || e is Expr.Upref || e is Expr.Call) {
                "unexpected operand to `\\´"
            }
            Expr.Dnref(chr, e)
        } else {
            all.accept_err(TK.XNUM)
            val num = all.tk0 as Tk.Num
            all.assert_tk(all.tk0, e !is Expr.TCons && e !is Expr.UCons) {
                "invalid discriminator : unexpected constructor"
            }
            if (chr.chr=='?' || chr.chr=='!') {
                All_assert_tk(all.tk0, num.num!=0 || e is Expr.Dnref) {
                    "invalid discriminator : union cannot be <.0>"
                }
            }
            when {
                (chr.chr == '?') -> Expr.UPred(num, e)
                (chr.chr == '!') -> Expr.UDisc(num, e)
                (chr.chr == '.') -> Expr.TDisc(num, e)
                else -> error("impossible case")
            }
        }
    }

    return e
}

fun xparser_attr (all: All): Attr {
    var e = when {
        all.accept(TK.XVAR) -> Attr.Var(all.tk0 as Tk.Str)
        all.accept(TK.XNAT) -> {
            all.accept_err(TK.CHAR, ':')
            val tp = xparser_type(all)
            Attr.Nat(all.tk0 as Tk.Nat, tp)
        }
        all.accept(TK.CHAR,'\\') -> {
            val tk0 = all.tk0 as Tk.Chr
            val e = xparser_attr(all)
            all.assert_tk(all.tk0, e is Attr.Nat || e is Attr.Var || e is Attr.TDisc || e is Attr.UDisc || e is Attr.Dnref) {
                "unexpected operand to `\\´"
            }
            Attr.Dnref(tk0,e)
        }
        all.accept(TK.CHAR, '(') -> {
            val e = xparser_attr(all)
            all.accept_err(TK.CHAR, ')')
            e
        }
        else -> {
            all.err_expected("expression")
            error("unreachable")
        }
    }

    // one.1!\.2.1?
    while (all.accept(TK.CHAR, '\\') || all.accept(TK.CHAR, '.') || all.accept(TK.CHAR, '!')) {
        val chr = all.tk0 as Tk.Chr
        e = if (chr.chr == '\\') {
            all.assert_tk(
                all.tk0,
                e is Attr.Nat || e is Attr.Var || e is Attr.TDisc || e is Attr.UDisc || e is Attr.Dnref
            ) {
                "unexpected operand to `\\´"
            }
            Attr.Dnref(chr, e)
        } else {
            all.accept_err(TK.XNUM)
            val num = all.tk0 as Tk.Num
            when {
                (chr.chr == '!') -> Attr.UDisc(num, e)
                (chr.chr == '.') -> Attr.TDisc(num, e)
                else -> error("impossible case")
            }
        }
    }
    return e
}

fun xparser_block (all: All): Stmt.Block {
    all.accept_err(TK.CHAR,'{')
    val tk0 = all.tk0 as Tk.Chr
    val scp = all.accept(TK.XSCPCST).let { if (it) all.tk0 as Tk.Scp1 else null }
    val ret = xparser_stmts(all, Pair(TK.CHAR,'}'))
    all.accept_err(TK.CHAR,'}')
    return Stmt.Block(tk0, scp, ret)
}

fun xparser_stmt (all: All): Stmt {
    return when {
        all.accept(TK.VAR) -> {
            all.accept_err(TK.XVAR)
            val tk_id = all.tk0 as Tk.Str
            val tp = if (!all.accept(TK.CHAR,':')) null else {
                xparser_type(all)
            }
            if (all.accept(TK.CHAR,'=')) {
                val tk = all.tk0 as Tk.Chr
                val src = xparser_expr(all)
                Stmt.Seq(tk_id, Stmt.Var(tk_id,tp), Stmt.Set(tk,Expr.Var(tk_id),src))
            } else {
                if (tp == null) {
                    all.err_expected("type declaration")
                }
                Stmt.Var(tk_id, tp)
            }
        }
        all.accept(TK.SET) -> {
            val dst = xparser_attr(all)
            all.accept_err(TK.CHAR,'=')
            val tk0 = all.tk0 as Tk.Chr
            val src = xparser_expr(all)
            Stmt.Set(tk0, dst.toExpr(), src)
        }
        all.accept(TK.NATIVE) -> {
            all.accept_err(TK.XNAT)
            Stmt.Nat(all.tk0 as Tk.Nat)
        }
        all.check(TK.CALL) || all.check(TK.OUTPUT) || all.check(TK.INPUT) -> {
            val tk0 = all.tk1 as Tk.Key
            val e = xparser_expr(all)
            Stmt.SExpr(tk0, e)
        }
        all.accept(TK.IF) -> {
            val tk0 = all.tk0 as Tk.Key
            val tst = xparser_expr(all)
            val true_ = xparser_block(all)
            val false_ = if (all.accept(TK.ELSE)) {
                xparser_block(all)
            } else {
                Stmt.Block(Tk.Chr(TK.CHAR,all.tk1.lin,all.tk1.col,'{'),null, Stmt.Nop(all.tk0))
            }
            Stmt.If(tk0, tst, true_, false_)
        }
        all.accept(TK.RETURN) -> {
            val tk0 = all.tk0 as Tk.Key
            val e = try {
                xparser_expr(all)
            } catch (e: Throwable) {
                assert(!all.consumed(tk0)) {
                    e.message!!
                }
                Expr.Unit(Tk.Sym(TK.UNIT,all.tk1.lin,all.tk1.col,"()"))
            }
            Stmt.Seq (tk0,
                Stmt.Set (
                    Tk.Chr(TK.CHAR,tk0.lin,tk0.col,'='),
                    Expr.Var(Tk.Str(TK.XVAR,tk0.lin,tk0.col,"ret")),
                    e
                ),
                Stmt.Ret(tk0)
            )
        }
        all.accept(TK.LOOP) -> {
            val tk0 = all.tk0 as Tk.Key
            val block = xparser_block(all)
            Stmt.Loop(tk0, block)
        }
        all.accept(TK.BREAK) -> Stmt.Break(all.tk0 as Tk.Key)
        all.check(TK.CHAR,'{') -> xparser_block(all)
        else -> {
            all.err_expected("statement")
            error("unreachable")
        }
    }
}

fun xparser_stmts (all: All, opt: Pair<TK,Char?>): Stmt {
    fun enseq (s1: Stmt, s2: Stmt): Stmt {
        return when {
            (s1 is Stmt.Nop) -> s2
            (s2 is Stmt.Nop) -> s1
            else -> Stmt.Seq(s1.tk, s1, s2)
        }
    }
    var ret: Stmt = Stmt.Nop(all.tk0)
    while (true) {
        all.accept(TK.CHAR, ';')
        val tk_bef = all.tk0
        try {
            val s = xparser_stmt(all)
            ret = enseq(ret,s)
        } catch (e: Throwable) {
            //throw e
            assert(!all.consumed(tk_bef)) {
                e.message!!
            }
            assert(all.check(opt.first, opt.second)) {
                e.message!!
            }
            break
        }
    }
    return ret
}
