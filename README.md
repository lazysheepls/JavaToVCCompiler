# JavaToVCCompiler
A functional Java to VC compiler

# Implementation
scanner: read characters and produces tokens
recogniser: read tokens and parse the program only for syntactic correctness
parser: extend the parser to build an abstract syntax tree (AST)
static semantics: check semantics at compile-time
code generator -- generate Java bytecode (Java assembly to be more correct)

# Waht is VC?
VC is a subset of C
- Function and variable must declared before use
- Only support primitive types and struct (boolean type does not exist)

# Abstract syntax tree
While java program is translated into a tree structure, attributes are appended to each node. It could be either derived by reading its child nodes (look-down) or parsed to the node by its parent (look-up). Of course, to optimise the performance, attribute updates for all nodes in the tree is done in a single scan. A simple example would be c = a + b, where a is an int, but b is a float, based on VC's definition, c must be a float. Therefore, while processing a + b, we first need cast a into a float, so that float(a) could add to b. This is something called type interpretation.

# Design pattern
Visitor design pattern, used to inject function in few phases of the compilation process

# Reference material
VC language definition.pdf
Assignment spec 1 - 5.pdf
