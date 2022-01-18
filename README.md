# Ce1 - Extensions

*Ce* is a simple language with algebraic data types, pointers, first-class
functions, and region-based memory management.
The main goal of *Ce* is to support safe memory management for dynamically
allocated data structures.

An allocated data is always attached to a specific block and cannot move.
When a block terminates, all attached allocations are automatically released.
**This prevents memory leaks.**
A pointer is also attached to a specific block and cannot point to data
allocated in nested blocks.
**This prevents dangling pointer dereferencing.**
These ideas have been successfully adopted in Cyclone:
https://cyclone.thelanguage.org/

*Ce1* is an extension to the core version of *Ce* with type inference and some
syntax sugar.

See also *Ce0*: https://github.com/fsantanna/ce0

# INSTALL & RUN

```
$ sudo make install
$ vi x.ce   # output std ()
$ ce1 x.ce
()
$
```

# MANUAL

This manual only contains the differences to *Ce0*.

# 1. STATEMENTS

## Variable Declaration

A variable declaration may include an initialization expression:

```
var x: () = ()
```

In *Ce0*, it is equivalent to a declaration followed by an assignment:

```
var x: ()
set x = ()
```

The declaration may ommit its type, which the language infers from the
initialization expression:

```
var x = ()  -- `x: ()` is inferred from expression `()`
```

## Conditional

The `else` branch is optional:

```
if x {
    call f ()
}   -- do nothing otherwise
```

In *Ce0*, it is equivalent to an empty `else` branch:

```
if x {
    call f ()
} else {
    -- do nothing otherwise
}
```

## Function

The `return` statement may include a result expression:

```
set f = func () -> () {
    return arg      -- assigns to the result and returns
}
```

In *Ce0*, it is equivalent to an assignment to `ret` and an empty `return`:

```
set f = func () -> () {
    set ret = arg
    return
}
```

# 2. TYPES

## Pointer

A pointer may ommit its block, which defaults to `@LOCAL`:

```
/_int
```

## Function

`TODO: implicit blocks`


# 3. EXPRESSIONS

## Native

A native expression may ommit its type, which the language infers from the
context:

```
var x: _int = _10   -- `_10:_int` is inferred from the assignment
```

## Union: Constructor, Allocation, Discriminator & Predicate

### Constructor

For unions with unit `()` subcases, the constructor argument unit `()` is
optional:

```
<.1>: <(),()>   -- `<.1 ()>` is not required
```

A constructor may ommit its explicit complete union type, which the language
infers from the context:

```
var x: <(),()> = <.1>   -- `<.1 ()>:<(),()>` is inferred from the assignment
```

### Null Pointer Constructor

A null constructor may also ommit its explicit complete union type:

```
var x: /</^@A>@A = <.0> -- `<.0>:/</^@A>@A`
```

### Allocation

An allocation may ommit its explicit block, which the language infers from the
context:

```
var x: /</^@A>@A = new <.1 <.0>>    -- `: @A` is inferred from the assignment
```

## Call

`TODO: return block`

## Input

An input may ommit its explicit return type, which the language infers from the
context:

```
var x: _int = input std ()   -- `: _int` is inferred from the assignment
```

# 4. LEXICAL RULES

No changes.

# 5. SYNTAX

```
Stmt ::= <...>
      |  `var´ VAR [`:´ Type] [`=´ Expr]                -- optional type and assignment
      |  `if´ Expr `{´ Stmt `}´ [`else´ `{´ Stmt `}´]   -- optional `else`
      |  `return´ [Expr]                                -- optional result expression

Expr ::= <...>
      |  NAT [`:´ Type]                                 -- optional type
      |  `<´ `.´ NUM Expr `>´ [`:´ Type]                -- optional type
      |  `<´ `.´ 0 `>´ [`:´ Type]                       -- optional type
      |  `new´ Expr.Union [`:´ BLOCK]                   -- optional block
      |  `input´ VAR Expr [`:´ Type]                    -- optional type
      |  `call´ Expr Blocks Expr [`:´ BLOCK]            -- optional block

Type ::= <...>
      |  `/´ Type [BLOCK]                               -- optional block
      |  `func´ `TODO`
```


