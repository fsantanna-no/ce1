import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class TParser {

    // TYPE

    /*
    @Test
    fun a01_parser_type () {
        val all = All_new(PushbackReader(StringReader("xxx"), 2))
        lexer(all)
        try {
            parser_type(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 1): expected type : have \"xxx\"")
        }
    }

    // EXPR

    @Test
    fun b01_parser_expr_unit () {
        val all = All_new(PushbackReader(StringReader("()"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Unit)
    }
     */

    // STMT

    @Test
    fun c01_parser_var () {
        val all = All_new(PushbackReader(StringReader("var x: () = ()"), 2))
        lexer(all)
        val s = xparser_stmt(all)
        println(s)
        assert(s is Stmt.Seq && s.s1 is Stmt.Var && s.s2 is Stmt.Set)
        assert(s.tostr() == "var x: ()\nset x = ()\n") { s.tostr() }
    }
    @Test
    fun c02_parser_var () {
        val all = All_new(PushbackReader(StringReader("var x: ()"), 2))
        lexer(all)
        val s = xparser_stmt(all)
        assert(s is Stmt.Var)
        assert(s.tostr() == "var x: ()\n") { s.tostr() }
    }
    @Test
    fun c03_parser_var () {
        val all = All_new(PushbackReader(StringReader("var x = ()"), 2))
        lexer(all)
        val s = xparser_stmt(all)
        assert(s is Stmt.Seq && s.s1 is Stmt.Var && s.s2 is Stmt.Set)
    }
    @Test
    fun c04_parser_var () {
        val all = All_new(PushbackReader(StringReader("var x"), 2))
        lexer(all)
        try {
            xparser_stmt(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 6): expected type declaration : have end of file")
        }
    }
}