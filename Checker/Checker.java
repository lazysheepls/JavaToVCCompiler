/**
 * Checker.java   
 * Mar 25 15:57:55 AEST 2020
 **/

package VC.Checker;

import VC.ASTs.*;
import VC.Scanner.SourcePosition;

import javax.sound.sampled.EnumControl.Type;

import VC.ErrorReporter;
import VC.StdEnvironment;

public final class Checker implements Visitor {

  private String errMesg[] = {
    "*0: main function is missing",                            
    "*1: return type of main is not int",                    

    // defined occurrences of identifiers
    // for global, local and parameters
    "*2: identifier redeclared",                             
    "*3: identifier declared void",                         
    "*4: identifier declared void[]",                      

    // applied occurrences of identifiers
    "*5: identifier undeclared",                          

    // assignments
    "*6: incompatible type for =",                       
    "*7: invalid lvalue in assignment",                 

     // types for expressions 
    "*8: incompatible type for return",                
    "*9: incompatible type for this binary operator", 
    "*10: incompatible type for this unary operator",

     // scalars
     "*11: attempt to use an array/function as a scalar", 

     // arrays
     "*12: attempt to use a scalar/function as an array",
     "*13: wrong type for element in array initialiser",
     "*14: invalid initialiser: array initialiser for scalar",   
     "*15: invalid initialiser: scalar initialiser for array",  
     "*16: excess elements in array initialiser",              
     "*17: array subscript is not an integer",                
     "*18: array size missing",                              

     // functions
     "*19: attempt to reference a scalar/array as a function",

     // conditional expressions in if, for and while
    "*20: if conditional is not boolean",                    
    "*21: for conditional is not boolean",                  
    "*22: while conditional is not boolean",               

    // break and continue
    "*23: break must be in a while/for",                  
    "*24: continue must be in a while/for",              

    // parameters 
    "*25: too many actual parameters",                  
    "*26: too few actual parameters",                  
    "*27: wrong type for actual parameter",           

    // reserved for errors that I may have missed (J. Xue)
    "*28: misc 1",
    "*29: misc 2",

    // the following two checks are optional 
    "*30: statement(s) not reached",     
    "*31: missing return statement",    
  };


  private SymbolTable idTable;
  private static SourcePosition dummyPos = new SourcePosition();
  private ErrorReporter reporter;

  // Checks whether the source program, represented by its AST, 
  // satisfies the language's scope rules and type rules.
  // Also decorates the AST as follows:
  //  (1) Each applied occurrence of an identifier is linked to
  //      the corresponding declaration of that identifier.
  //  (2) Each expression and variable is decorated by its type.

  public Checker (ErrorReporter reporter) {
    this.reporter = reporter;
    this.idTable = new SymbolTable ();
    establishStdEnvironment();
  }

  public void check(AST ast) {
    ast.visit(this, null);
  }


  // auxiliary methods

  private void declareVariable(Ident ident, Decl decl) {
    IdEntry entry = idTable.retrieveOneLevel(ident.spelling);

    if (entry == null) {
      ; // no problem
    } else
      reporter.reportError(errMesg[2] + ": %", ident.spelling, ident.position);
    idTable.insert(ident.spelling, decl);
  }


  // Programs

  public Object visitProgram(Program ast, Object o) {
    ast.FL.visit(this, null);

    return null;
  }

  // Statements

  public Object visitCompoundStmt(CompoundStmt ast, Object o) {
    idTable.openScope();

    // Your code goes here

    idTable.closeScope();
    return null;
  }

  public Object visitStmtList(StmtList ast, Object o) {
    ast.S.visit(this, o);
    if (ast.S instanceof ReturnStmt && ast.SL instanceof StmtList)
      reporter.reportError(errMesg[30], "", ast.SL.position);
    ast.SL.visit(this, o);
    return null;
  }

  public Object visitIfStmt(IfStmt ast, Object o) {
    Type exprType = ast.E.visit(this, o);
    if (!exprType.isBooleanType()){
      reporter.reportError(errMesg[20] + " %", "(found: " + exprType.toString() + ")", ast.E.position);
    }
    ast.S1.visit(this,o);
    ast.S2.visit(this,o);
    return null;
  }

  public Object visitWhileStmt(WhileStmt ast, Object o) {
    Type exprType = ast.E.visit(this, o);
    if(!exprType.isBooleanType()){
      reporter.reportError(errMesg[22] + " %", "(found: " + exprType.toString() + ")", ast.E.position);
    }
    ast.S.visit(this.o);
    return null;
  }

  public Object visitForStmt(ForStmt ast, Object o) {
    Expr e2Expr = ast.E2;
    Type e2Type = ast.E2.visit(this, o);

    if(!e2Expr.isEmptyExpr()) {
      if (!e2Type.isBooleanType()) {
        reporter.reportError(errMesg[21] + " %", "(found: " + exprType.toString() + ")", ast.E2.position);
      }
    }
    ast.E1.visit(this, o);
    ast.E3.visit(this, o);
    ast.S.visit(this,o);
    return null;
  }

  public Object visitBreakStmt(BreakStmt ast, Object o) {
    AST parent = ast.parent;
    // Find parent node of the break statement
    while(!(parent instanceof CompoundStmt || 
            parent instanceof ForStmt ||
            parent instanceof WhileStmt)) {
            parent = parent.parent;
          }
    if(!(parent instanceof ForStmt || parent instanceof WhileStmt)) {
      reporter.reportError(errMesg[23], "", ast.position);
    }
    return null;
  }

  public Object visitContinueStmt(ContinueStmt ast, Object o) {
    AST parent = ast.parent;
    // Find parent node of the break statement
    while(!(parent instanceof CompoundStmt || 
            parent instanceof ForStmt ||
            parent instanceof WhileStmt)) {
            parent = parent.parent;
          }
    if(!(parent instanceof ForStmt || parent instanceof WhileStmt)) {
      reporter.reportError(errMesg[24], "", ast.position);
    }
    return null;
  }

  public Object visitReturnStmt(ReturnStmt ast, Object o) {
    Type funcRetType = (FuncDecl) o.T;
    Type retExprType = ast.E.visit(this,null);

    if (ast.E instanceof EmptyExpr) {
      // return type with no expr: function return type should be only be void
      if (!funcRetType.isVoidType()) {
        reporter.reportError(errMesg[8], "", ast.position);
      }
    } else if (retExprType.isArrayType()) {
      // return type value cannot be array type
      reporter.reportError(errMesg[8], "", ast.position);
    } else if (!funcRetType.assignable(retExprType)) {
      // function return type must be assignable with return type inside the function
      reporter.reportError(errMesg[8], "", ast.position);
    }

    // Type coersion
    if (retExprType.isIntType() && funcRetType.isFloatType()) {
      // type coersion
      ast.E = intToFloat(ast.E);
    }
    return null;
  }

  public Object visitExprStmt(ExprStmt ast, Object o) {
    ast.E.visit(this, o);
    return null;
  }

  public Object visitEmptyStmt(EmptyStmt ast, Object o) {
    return null;
  }

  public Object visitEmptyStmtList(EmptyStmtList ast, Object o) {
    return null;
  }

  // Expressions

  // Returns the Type denoting the type of the expression. Does
  // not use the given object.
  public Object visitUnaryExpr(UnaryExpr ast, Object o) {
    Type exprType = ast.E.visit(this, o);

    // Avoid spurious errors
    if(exprType == StdEnvironment.errorType){
      ast.type = StdEnvironment.errorType;
      return ast.type;
    }

    // Check Expr type based on operator
    switch(ast.O.spelling){
      case "+":
      case "-":
        if (exprType.isIntType()) {
          ast.type = StdEnvironment.intType;
        } else if (exprType.isFloatType()) {
          ast.type = StdEnvironment.floatType;
        } else {
          ast.type = StdEnvironment.errorType;
          reporter.reportError(errMesg[10] + ": %", ast.O.spelling, ast.position);
        }
        break;
      case "!":
        if (exprType.isBooleanType()) {
          // Modify <op> to i<op> for boolean operators (JVM)
          ast.O.spelling = "i" + ast.O.spelling;
          ast.type = StdEnvironment.booleanType;
        } else {
          ast.type = StdEnvironment.errorType;
          reporter.reportError(errMesg[10] + ": %", ast.O.spelling, ast.position);
        }
        break;
      default:
        ast.type = StdEnvironment.errorType();
        reporter.reportError(errMesg[10] + ": %", ast.O.spelling, ast.position);
        break;
    }

    return ast.type;
  }

  public Object visitBinaryExpr(BinaryExpr ast, Object o) {
    Type e1Type = ast.E1.visit(this, o);
    Type e2Type = ast.E2.visit(this, o);

    if (e1Type == StdEnvironment.errorType || e2Type == StdEnvironment.errorType) {
      ast.type = StdEnvironment.errorType;
      return ast.type;
    }
    //TODO: there may be other conditions that could cause scalar error
    if (e1Type.isArrayType() || e2Type.isArrayType()){ // err[11]: attempt to use an array/fuction as a scalar
      ast.type = StdEnvironment.errorType;
      reporter.reportError(errMesg[11], "", ast.position);
      return ast.type;
    }

    switch(ast.O.spelling){
      case "&&":
      case "||": // logical <op>, oprand: boolean, result: boolean
        if (e1Type.isBooleanType() && e2Type.isBooleanType()){
          ast.type = StdEnvironment.booleanType;
          // Modify <op> to i<op> for boolean operators (JVM)
          ast.O.spelling = "i" + ast.O.spelling;
        } else {
          ast.type = StdEnvironment.errorType;
          reporter.reportError(errMesg[9] + ": %", ast.O.spelling, ast.position);
        }
        break;
      case "+":
      case "-":
      case "*":
      case "/": // arthematic <op>, oprand: int or float, result: int or float
        if (e1Type.isIntType() && e2Type.isIntType()) {
          ast.type = StdEnvironment.intType;
          // Modify the overloading <op> to i<op>
          ast.O.spelling = "i" + ast.O.spelling;
        } else if (e1Type.isFloatType() && e2Type.isFloatType()) {
          ast.type = StdEnvironment.floatType;
          // Modify the overloading <op> to f<op>
          ast.O.spelling = "f" + ast.O.spelling;
        } else if (e1Type.isIntType() && e2Type.isFloatType()) {
          // Type coersion for E1
          ast.E1 = intToFloat(ast.E1);
          ast.type = StdEnvironment.floatType;
          // Modify the overloading <op> to f<op>
          ast.O.spelling = "f" + ast.O.spelling;
        } else if (e1Type.isFloatType() && e2Type.isIntType()) {
          // Type coersion for E2
          ast.E2 = intToFloat(ast.E2);
          ast.type = StdEnvironment.floatType;
          // Modify the overloading <op> to f<op>
          ast.O.spelling = "f" + ast.O.spelling;
        } else {
          ast.type = StdEnvironment.errorType;
          reporter.reportError(errMesg[9] + ": %", ast.O.spelling, ast.position);
        }
        break;
      case "<":
      case "<=":
      case ">":
      case ">=": // relational <op>, oprand: int or float, result: boolean
      if (e1Type.isIntType() && e2Type.isIntType()) {
        ast.type = StdEnvironment.booleanType;
        // Modify the overloading <op> to i<op>
        ast.O.spelling = "i" + ast.O.spelling;
      } else if (e1Type.isFloatType() && e2Type.isFloatType()) {
        ast.type = StdEnvironment.booleanType;
        // Modify the overloading <op> to f<op>
        ast.O.spelling = "f" + ast.O.spelling;
      } else if (e1Type.isIntType() && e2Type.isFloatType()) {
        // Type coersion for E1
        ast.E1 = intToFloat(ast.E1);
        ast.type = StdEnvironment.booleanType;
        // Modify the overloading <op> to f<op>
        ast.O.spelling = "f" + ast.O.spelling;
      } else if (e1Type.isFloatType() && e2Type.isIntType()) {
        // Type coersion for E2
        ast.E2 = intToFloat(ast.E2);
        ast.type = StdEnvironment.booleanType;
        // Modify the overloading <op> to f<op>
        ast.O.spelling = "f" + ast.O.spelling;
      } else {
        ast.type = StdEnvironment.errorType;
        reporter.reportError(errMesg[9] + ": %", ast.O.spelling, ast.position);
      }
        break;
      case "==":
      case "!==": // equality <op>, oprand: boolean or int or float, result: boolean
        if (e1Type.isBooleanType() && e2Type.isBooleanType()) {
          ast.type = StdEnvironment.booleanType;
          // Modify <op> to i<op> for boolean operators (JVM)
          ast.O.spelling = "i" + ast.O.spelling;
        } else if (e1Type.isIntType() && e2Type.isIntType()) {
          ast.type = StdEnvironment.booleanType;
          // Modify the overloading <op> to i<op>
          ast.O.spelling = "i" + ast.O.spelling;
        } else if (e1Type.isIntType() && e2Type.isFloatType()) {
          // Type coersion for E1
          ast.E1 = intToFloat(ast.E1);
          ast.type = StdEnvironment.booleanType;
          // Modify the overloading <op> to f<op>
          ast.O.spelling = "f" + ast.O.spelling;
        } else if (e1Type.isFloatType() && e2Type.isIntType()) {
          // Type coersion for E2
          ast.E2 = intToFloat(ast.E2);
          ast.type = StdEnvironment.booleanType;
          // Modify the overloading <op> to f<op>
          ast.O.spelling = "f" + ast.O.spelling;
        } else {
          ast.type = StdEnvironment.errorType;
          reporter.reportError(errMesg[9] + ": %", ast.O.spelling, ast.position);
        }
        break;
      default:
        ast.type = StdEnvironment.errorType;
        reporter.reportError(errMesg[9] + ": %", ast.O.spelling, ast.position);
        break;
    }

    return ast.type;
  }

  public Object visitInitExpr(InitExpr ast, Object o) {
    //TODO:
  }

  public Object visitAssignExpr(AssignExpr ast, Object o) {
    Type e1Type = (Type) ast.E1.visit(this, o);
    Type e2Type = (Type) ast.E2.visit(this, o);

    // Avoid spurous error
    if(e1Type.isErrorType() || e2Type.isErrorType()){
      ast.type = StdEnvironment.errorType;
      return StdEnvironment.errorType;
    }

    //FIXME: Unknown part: arrayExpr, CallExpr, VarExpr (err[7], err[11])
    if (e1Type.isArrayType()|| e2Type.isArrayType()) {
      //TODO: cannot rely on assignable for array type (special case)
    } 

    //TODO: void cannot be in the assignment expr

    // Known part (std env)
    boolean isE1StdType = isStdType(e1Type);
    boolean isE2StdType = isStdType(e2Type);
    
    // Std types check assignable
    if(!e1Type.assignable(e2Type)){
      reporter.reportError(errMesg[6], "", ast.position); //err[6]: incompatible type for =
      return StdEnvironment.errorType;
    }

    // Assigment coersions (int to float)
    if (e1Type.isFloatType() && e2Type.isIntType()){
      //TODO: insert i2f node
    }

    // Assign type to current ast
    ast.type = e1Type; 
    return ast.type;
  }

  public Object visitEmptyExpr(EmptyExpr ast, Object o) {
    ast.type = StdEnvironment.errorType;
    return ast.type;
  }

  public Object visitBooleanExpr(BooleanExpr ast, Object o) {
    ast.type = StdEnvironment.booleanType;
    return ast.type;
  }

  public Object visitIntExpr(IntExpr ast, Object o) {
    ast.type = StdEnvironment.intType;
    return ast.type;
  }

  public Object visitFloatExpr(FloatExpr ast, Object o) {
    ast.type = StdEnvironment.floatType;
    return ast.type;
  }

  public Object visitStringExpr(StringExpr ast, Object o) {
    ast.type = StdEnvironment.stringType;
    return ast.type;
  }

  public Object visitVarExpr(VarExpr ast, Object o) {
    ast.type = (Type) ast.V.visit(this, null);
    return ast.type;
  }

  // Declarations

  // Always returns null. Does not use the given object.

  public Object visitFuncDecl(FuncDecl ast, Object o) {
    idTable.insert (ast.I.spelling, ast); 

    // Your code goes here

    // HINT
    // Pass ast as the 2nd argument (as done below) so that the
    // formal parameters of the function an be extracted from ast when the
    // function body is later visited

    ast.S.visit(this, ast);
    return null;
  }

  public Object visitDeclList(DeclList ast, Object o) {
    ast.D.visit(this, null);
    ast.DL.visit(this, null);
    return null;
  }

  public Object visitEmptyDeclList(EmptyDeclList ast, Object o) {
    return null;
  }

  public Object visitGlobalVarDecl(GlobalVarDecl ast, Object o) {
    declareVariable(ast.I, ast);

    // fill the rest
  }

  public Object visitLocalVarDecl(LocalVarDecl ast, Object o) {
    declareVariable(ast.I, ast);

    // fill the rest
  }

  // Parameters

 // Always returns null. Does not use the given object.

  public Object visitParaList(ParaList ast, Object o) {
    ast.P.visit(this, null);
    ast.PL.visit(this, null);
    return null;
  }

  public Object visitParaDecl(ParaDecl ast, Object o) {
     declareVariable(ast.I, ast);

    if (ast.T.isVoidType()) {
      reporter.reportError(errMesg[3] + ": %", ast.I.spelling, ast.I.position);
    } else if (ast.T.isArrayType()) {
     if (((ArrayType) ast.T).T.isVoidType())
        reporter.reportError(errMesg[4] + ": %", ast.I.spelling, ast.I.position);
    }
    return null;
  }

  public Object visitEmptyParaList(EmptyParaList ast, Object o) {
    return null;
  }

  // Arguments

  // Your visitor methods for arguments go here

  // Types 

  // Returns the type predefined in the standard environment. 

  public Object visitErrorType(ErrorType ast, Object o) {
    return StdEnvironment.errorType;
  }

  public Object visitBooleanType(BooleanType ast, Object o) {
    return StdEnvironment.booleanType;
  }

  public Object visitIntType(IntType ast, Object o) {
    return StdEnvironment.intType;
  }

  public Object visitFloatType(FloatType ast, Object o) {
    return StdEnvironment.floatType;
  }

  public Object visitStringType(StringType ast, Object o) {
    return StdEnvironment.stringType;
  }

  public Object visitVoidType(VoidType ast, Object o) {
    return StdEnvironment.voidType;
  }

  // Literals, Identifiers and Operators

  public Object visitIdent(Ident I, Object o) {
    Decl binding = idTable.retrieve(I.spelling);
    if (binding != null)
      I.decl = binding;
    return binding;
  }

  public Object visitBooleanLiteral(BooleanLiteral SL, Object o) {
    return StdEnvironment.booleanType;
  }

  public Object visitIntLiteral(IntLiteral IL, Object o) {
    return StdEnvironment.intType;
  }

  public Object visitFloatLiteral(FloatLiteral IL, Object o) {
    return StdEnvironment.floatType;
  }

  public Object visitStringLiteral(StringLiteral IL, Object o) {
    return StdEnvironment.stringType;
  }

  public Object visitOperator(Operator O, Object o) {
    return null;
  }

  // Creates a small AST to represent the "declaration" of each built-in
  // function, and enters it in the symbol table.

  private FuncDecl declareStdFunc (Type resultType, String id, List pl) {

    FuncDecl binding;

    binding = new FuncDecl(resultType, new Ident(id, dummyPos), pl, 
           new EmptyStmt(dummyPos), dummyPos);
    idTable.insert (id, binding);
    return binding;
  }

  // Creates small ASTs to represent "declarations" of all 
  // build-in functions.
  // Inserts these "declarations" into the symbol table.

  private final static Ident dummyI = new Ident("x", dummyPos);

  private void establishStdEnvironment () {

    // Define four primitive types
    // errorType is assigned to ill-typed expressions

    StdEnvironment.booleanType = new BooleanType(dummyPos);
    StdEnvironment.intType = new IntType(dummyPos);
    StdEnvironment.floatType = new FloatType(dummyPos);
    StdEnvironment.stringType = new StringType(dummyPos);
    StdEnvironment.voidType = new VoidType(dummyPos);
    StdEnvironment.errorType = new ErrorType(dummyPos);

    // enter into the declarations for built-in functions into the table

    StdEnvironment.getIntDecl = declareStdFunc( StdEnvironment.intType,
	"getInt", new EmptyParaList(dummyPos)); 
    StdEnvironment.putIntDecl = declareStdFunc( StdEnvironment.voidType,
	"putInt", new ParaList(
	new ParaDecl(StdEnvironment.intType, dummyI, dummyPos),
	new EmptyParaList(dummyPos), dummyPos)); 
    StdEnvironment.putIntLnDecl = declareStdFunc( StdEnvironment.voidType,
	"putIntLn", new ParaList(
	new ParaDecl(StdEnvironment.intType, dummyI, dummyPos),
	new EmptyParaList(dummyPos), dummyPos)); 
    StdEnvironment.getFloatDecl = declareStdFunc( StdEnvironment.floatType,
	"getFloat", new EmptyParaList(dummyPos)); 
    StdEnvironment.putFloatDecl = declareStdFunc( StdEnvironment.voidType,
	"putFloat", new ParaList(
	new ParaDecl(StdEnvironment.floatType, dummyI, dummyPos),
	new EmptyParaList(dummyPos), dummyPos)); 
    StdEnvironment.putFloatLnDecl = declareStdFunc( StdEnvironment.voidType,
	"putFloatLn", new ParaList(
	new ParaDecl(StdEnvironment.floatType, dummyI, dummyPos),
	new EmptyParaList(dummyPos), dummyPos)); 
    StdEnvironment.putBoolDecl = declareStdFunc( StdEnvironment.voidType,
	"putBool", new ParaList(
	new ParaDecl(StdEnvironment.booleanType, dummyI, dummyPos),
	new EmptyParaList(dummyPos), dummyPos)); 
    StdEnvironment.putBoolLnDecl = declareStdFunc( StdEnvironment.voidType,
	"putBoolLn", new ParaList(
	new ParaDecl(StdEnvironment.booleanType, dummyI, dummyPos),
	new EmptyParaList(dummyPos), dummyPos)); 

    StdEnvironment.putStringLnDecl = declareStdFunc( StdEnvironment.voidType,
	"putStringLn", new ParaList(
	new ParaDecl(StdEnvironment.stringType, dummyI, dummyPos),
	new EmptyParaList(dummyPos), dummyPos)); 

    StdEnvironment.putStringDecl = declareStdFunc( StdEnvironment.voidType,
	"putString", new ParaList(
	new ParaDecl(StdEnvironment.stringType, dummyI, dummyPos),
	new EmptyParaList(dummyPos), dummyPos)); 

    StdEnvironment.putLnDecl = declareStdFunc( StdEnvironment.voidType,
	"putLn", new EmptyParaList(dummyPos));

  }

  // Insert unary expr i2f to ast
  // valid for the following visitor functions: visitAssignExpr, visitBinaryExpr, visitArg and visitReturnStmt
  private Expr intToFloat(Expr expr){
    Operator op = new Operator("i2f", dummyPos);
    UnaryExpr eAST = new UnaryExpr(op, expr, dummyPos);
    eAST.type = StdEnvironment.floatType;
    return eAST;
  }

}
