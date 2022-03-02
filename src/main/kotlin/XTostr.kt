fun Type.xtostr(): String {
    return XTostr().tostr(this)
}

fun Expr.xtostr(): String {
    return XTostr().tostr(this)
}

fun Stmt.xtostr(): String {
    return XTostr().tostr(this)
}

class XTostr: Tostr()
{
    override fun tostr (tp: Type): String {
        return when (tp) {
            is Type.Pointer -> if (tp.xscp  != null) super.tostr(tp) else ("/" + this.tostr(tp.pln))
            is Type.Alias   -> if (tp.xscps != null) super.tostr(tp) else tp.tk_.id
            is Type.Func    -> if (tp.xscps.second != null) super.tostr(tp) else {
                tp.tk_.key + " " + this.tostr(tp.inp) + " -> " + tp.pub.let { if (it == null) "" else this.tostr(it) + " -> " } + this.tostr(tp.out)
            }
            else -> super.tostr(tp)
        }
    }

    override fun tostr (e: Expr): String {
        return when (e) {
            is Expr.Nat   -> if (e.xtype != null) super.tostr(e) else e.tk_.toce()
            is Expr.UCons -> {
                if (e.xtype != null) super.tostr(e) else {
                    "<." + e.tk_.num + " " + this.tostr(e.arg) + ">"
                }
            }
            is Expr.UNull -> if (e.xtype != null) super.tostr(e) else "<.0>"
            is Expr.Field   -> "(" + this.tostr(e.tsk) + ".${e.tk_.id})"
            is Expr.New   -> if (e.xscp  != null) super.tostr(e) else "(new " + this.tostr(e.arg) + ")"
            is Expr.Call  -> if (e.xscps.first != null) super.tostr(e) else {
                val out = e.xscps.second.let { if (it == null) "" else ": @" + it.scp1.anon2local() }
                "(" + this.tostr(e.f) + " " + this.tostr(e.arg) + out + ")"
            }
            else -> super.tostr(e)
        }
    }

    override fun tostr (s: Stmt): String {
        return when (s) {
            is Stmt.Seq -> s.s1.let {
                if (it is Stmt.Var && it.xtype==null) {
                    "var " + it.tk_.id + " = " + when (s.s2) {
                        is Stmt.Set    -> this.tostr(s.s2.src)
                        is Stmt.SSpawn -> "spawn " + this.tostr(s.s2.call)
                        is Stmt.Seq    -> "await " + this.tostr(((((s.s2.s1 as Stmt.Seq).s1 as Stmt.Seq).s1 as Stmt.Seq).s2 as Stmt.SSpawn).call)
                        else -> TODO(s.s2.toString())
                    } + "\n"
                } else {
                    super.tostr(s)
                }
            }
            is Stmt.Input -> if (s.xtype!=null) super.tostr(s) else {
                if (s.dst == null) "" else "set " + this.tostr(s.dst) + " = " + "input " + s.lib.id + " " + this.tostr(s.arg)
            }
            is Stmt.Typedef -> if (s.xscp1s.first!=null) super.tostr(s) else {
                "type " + s.tk_.id + " = " + this.tostr(s.type) + "\n"
            }
            else -> super.tostr(s)
        }
    }
}