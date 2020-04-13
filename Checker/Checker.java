/**
 * Checker.java   
 * Mar 25 15:57:55 AEST 2020
 **/

package VC.Checker;

import VC.ASTs.*;
import VC.Scanner.SourcePosition;
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

    Decl binding = idTable.retrieve("main");
    if(binding == null || !binding.isFuncDecl()) { // error: main not found
      reporter.reportError(errMesg[0], "", ast.position);
    } else if (!binding.T.isIntType()) {
      reporter.reportError(errMesg[1], "", ast.position);
    }

    return null;
  }

  // Statements
  
  public Object visitCompoundStmt(CompoundStmt ast, Object o) {
    boolean isParentFuncDecl = ast.parent instanceof FuncDecl;
    if (!isParentFuncDecl) {
      idTable.openScope();
    } 
    // idTable.openScope();

    // Your code goes here
    ast.DL.visit(this, o);
    ast.SL.visit(this, o);

    if (!isParentFuncDecl) {
      idTable.closeScope();
    }
    // idTable.closeScope();
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
    Type exprType = (Type) ast.E.visit(this, o);
    if (!exprType.isBooleanType()){
      reporter.reportError(errMesg[20] + " %", "(found: " + exprType.toString() + ")", ast.E.position);
    }
    ast.S1.visit(this,o);
    ast.S2.visit(this,o);
    return null;
  }

  public Object visitWhileStmt(WhileStmt ast, Object o) {
    Type exprType = (Type) ast.E.visit(this, o);
    if(!exprType.isBooleanType()){
      reporter.reportError(errMesg[22] + " %", "(found: " + exprType.toString() + ")", ast.E.position);
    }
    ast.S.visit(this, o);
    return null;
  }

  public Object visitForStmt(ForStmt ast, Object o) {
    Expr e2Expr = ast.E2;
    Type e2Type = (Type) ast.E2.visit(this, o);

    if(!e2Expr.isEmptyExpr()) {
      if (!e2Type.isBooleanType()) {
        reporter.reportError(errMesg[21] + " %", "(found: " + e2Type.toString() + ")", ast.E2.position);
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
    Type funcRetType = ((FuncDecl) o).T;
    Type retExprType = (Type) ast.E.visit(this,null);

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
    return true; // return true means function has a return stmt
  }

  public Object visitExprStmt(ExprStmt ast, Object o) {
    ast.E.visit(this, o);
    return null;
  }

  public Object visitEmptyStmt(EmptyStmt ast, Object o) {
    return null;
  }

  public Object visitEmptyCompStmt(EmptyCompStmt ast, Object o) {
    return null;
  }

  public Object visitEmptyStmtList(EmptyStmtList ast, Object o) {
    return null;
  }

  // Expressions

  // Returns the Type denoting the type of the expression. Does
  // not use the given object.
  public Object visitUnaryExpr(UnaryExpr ast, Object o) {
    Type exprType = (Type) ast.E.visit(this, o);

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
      case "i2f":
        break;
      default:
        ast.type = StdEnvironment.errorType;
        reporter.reportError(errMesg[10] + ": %", ast.O.spelling, ast.position);
        break;
    }

    return ast.type;
  }

  public Object visitBinaryExpr(BinaryExpr ast, Object o) {
    Type e1Type = (Type) ast.E1.visit(this, o);
    Type e2Type = (Type) ast.E2.visit(this, o);

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
    // InitExpr only used in global-var-decl and local-var-decl (must be array initialiser)
    //TODO: set array size and err[16]
    ArrayType arrayType = null;
    ExprList exprList = (ExprList) ast.IL;
    //Assign arrayType
    if (o instanceof GlobalVarDecl){
      arrayType = (ArrayType) ((GlobalVarDecl)o).T;
    }
    if (o instanceof LocalVarDecl){
      arrayType = (ArrayType) ((LocalVarDecl)o).T;
    }
    // Check if initialiser and array size are the same
    if (arrayType != null && arrayType.E instanceof IntExpr) {
      Integer arraySize = Integer.valueOf(((IntExpr) arrayType.E).IL.spelling);
      // System.out.println(arraySize);
      // System.out.println(exprList.index);
      if (exprList.index > arraySize) {
        reporter.reportError(errMesg[16], "", ast.position);
        return null;
      }
    }
    ast.IL.visit(this, o);
    return null;
  }

  public Object visitExprList(ExprList ast, Object o) {
    // Expr-list only used in global-var-decl and local-var-decl inside the initialiser (must be array initialiser)
    // o is the Global/LocalVarDecl
    //TODO: err[13]
    // Increment index for the initaliser
    if (ast.EL instanceof ExprList) {
      ast.index = ((ExprList)ast.EL.visit(this, o)).index + 1;
    } else if (ast.EL instanceof EmptyExprList) {
      ast.index = 1;
    }

    Type exprType = (Type) ast.E.visit(this, o);
    // System.out.println(exprType);
    //Global variable
    if (o instanceof GlobalVarDecl) {
      ArrayType arrayType = (ArrayType)((GlobalVarDecl)o).T;
      // Type arrayPrimaryType = arrayType.T;
      // System.out.println(arrayType.T);
      if(!arrayType.T.assignable(exprType)) {
        reporter.reportError(errMesg[13] + ": at position", Integer.toString(ast.index), ast.E.position);
        return ast;
      }
      // Type coersion inside the initialiser
      if(arrayType.T.isFloatType() && ast.E.type.isIntType()){
        ast.E = intToFloat(ast.E);
      }
    }
    // Local variable
    if (o instanceof LocalVarDecl) {
      ArrayType arrayType = (ArrayType)((LocalVarDecl)o).T;
      // Type arrayPrimaryType = arrayType.T;
      // System.out.println(arrayType.T);
      if(!arrayType.T.assignable(exprType)) {
        reporter.reportError(errMesg[13] + ": at position", Integer.toString(ast.index), ast.E.position);
        return ast;
      }
      // Type coersion inside the initialiser
      if(arrayType.T.isFloatType() && ast.E.type.isIntType()){
        ast.E = intToFloat(ast.E);
      }
    }
    return ast;
  }

  public Object visitArrayExpr(ArrayExpr ast, Object o) { //FIXME: missing err[12]
    Type varType = (Type) ast.V.visit(this, null); //visit simple var
    Type exprType = (Type) ast.E.visit(this, null);
    
    // avoid spurous error
    if (varType.isErrorType() || exprType.isErrorType()) {
      ast.type = StdEnvironment.errorType;
      return ast.type;
    }

    if(!exprType.isIntType()) { // error: array subscript is not an integer
      reporter.reportError(errMesg[17], "", ast.position);
      ast.type = StdEnvironment.errorType;
      return ast.type;
    }

    ast.type = varType; // arrayType
    return ast.type;
  }

  public Object visitAssignExpr(AssignExpr ast, Object o) {
    Type e1Type = (Type) ast.E1.visit(this, o);
    Type e2Type = (Type) ast.E2.visit(this, o);

    // avoid spurous error
    // if(e1Type.isErrorType() || e2Type.isErrorType()){
    //   ast.type = StdEnvironment.errorType;
    //   return StdEnvironment.errorType;
    // }

    //FIXME: Unknown part: arrayExpr, CallExpr, VarExpr (err[7], err[11])
    if (e1Type.isArrayType()|| e2Type.isArrayType()) {
      //TODO: cannot rely on assignable for array type (special case)
    } 

    //TODO: void cannot be in the assignment expr

    // Known part (std env)
    // boolean isE1StdType = isStdType(e1Type);
    // boolean isE2StdType = isStdType(e2Type);

    // Check if E1 is a var and E2 is not a var
    if(!(ast.E1 instanceof VarExpr)) { //FIXME: || ast.E1 instanceof ArrayExpr) * array name can only be used as an arguement, not in assignment
      reporter.reportError(errMesg[7], "", ast.position);
      return StdEnvironment.errorType;
    }
    // // Check if E2 is not array type (array name can only be used as an arguement, not in assignment) FIXME: Not sure
    // if (e2Type.isArrayType()) {
    //   reporter.reportError(errMesg[11], "", ast.position);
    //   return StdEnvironment.errorType;
    // }
    
    // Std types check assignable
    if(!e1Type.assignable(e2Type)){
      reporter.reportError(errMesg[6], "", ast.position); //err[6]: incompatible type for =
      return StdEnvironment.errorType;
    }

    // Assigment coersions (int to float)
    if (e1Type.isFloatType() && e2Type.isIntType()){
      ast.E2 = intToFloat(ast.E2);
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

  public Object visitCallExpr(CallExpr ast, Object o) { //TODO: err[5](done), err[29]
    Decl binding = (Decl) ast.I.visit(this, null);
    if (binding == null) { // error: function never declared
      reporter.reportError(errMesg[5] + ": %", ast.I.spelling, ast.position);
      ast.type = StdEnvironment.errorType;
      return ast.type;
    }
    if (!binding.isFuncDecl()) { // error: use scalar/array as function
      ast.type = StdEnvironment.errorType;
      reporter.reportError(errMesg[19] + ": %", ast.I.spelling, ast.I.position);
      return ast.type;
    }
    List paraList = ((FuncDecl) binding).PL; // pass para list all the way down so that is can be checked later
    ast.AL.visit(this, paraList);
    return binding.T;
  }

  // Declarations

  // Always returns null. Does not use the given object.

  public Object visitFuncDecl(FuncDecl ast, Object o) {
    declareVariable(ast.I, ast);
    // idTable.insert (ast.I.spelling, ast); 

    // Your code goes here

    // HINT
    // Pass ast as the 2nd argument (as done below) so that the
    // formal parameters of the function an be extracted from ast when the
    // function body is later visited
    // ast.I.visit(this, null);
    idTable.openScope();
    ast.PL.visit(this, ast);
    Object isRetStmtFound = ast.S.visit(this, ast);

    // Check if return stmt is found in the non-void type function
    if(!ast.T.isVoidType()) {
      if (!(isRetStmtFound instanceof Boolean)) {
        reporter.reportError(errMesg[31], "", ast.position);
      } else {
        if (!(Boolean)isRetStmtFound) {
          reporter.reportError(errMesg[31], "", ast.position);
        }
      }
    }
    idTable.closeScope();
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
    // Check type is valid
    if (ast.T.isVoidType()) { // error: declared with void type
      reporter.reportError(errMesg[3] + ": %", ast.I.spelling, ast.I.position);
    } else if (ast.T.isArrayType()) {
      if (((ArrayType) ast.T).E instanceof EmptyExpr) {
        reporter.reportError(errMesg[18] + ": %", ast.I.spelling, ast.I.position);
        return ast.T;
      } 
      
      Type arrayPrimaryType = (Type)((ArrayType)ast.T).T;
      if (arrayPrimaryType.isVoidType()) { // error: declared with void[] type
        reporter.reportError(errMesg[4] + ": %", ast.I.spelling, ast.I.position);
      }
    }

    // Check array type global var decl
    if (ast.T.isArrayType()) {
      // No initialiser
      if (ast.E instanceof EmptyExpr) {
          if (((ArrayExpr)ast.E).E.isEmptyExpr()) { // error: array size missing
            reporter.reportError(errMesg[18] + ": %", ast.I.spelling, ast.I.position);
          }
      }

      // With initialiser
      if (ast.E instanceof InitExpr) {
        ast.E.visit(this, ast);
      } else { // error: scalar initialize for array
        reporter.reportError(errMesg[15] + ": %", ast.I.spelling, ast.position);
      }
    } else {
    // Check non-array type global var decl
      if (ast.E instanceof InitExpr) { // error: array initializer for scalar
        reporter.reportError(errMesg[14], "", ast.E.position);
        return null;
      }
      Type exprType = (Type) ast.E.visit(this, ast);
      if(!ast.T.assignable(exprType)) { // error: incompetible type before and after "="
        reporter.reportError(errMesg[6] + ": %", ast.I.spelling, ast.I.position);
      }
    }

    ast.I.visit(this,null);
    ast.E.visit(this,ast);

    return null;
  }

  public Object visitLocalVarDecl(LocalVarDecl ast, Object o) {
    declareVariable(ast.I, ast);

    // fill the rest
      // Check type is valid
      if (ast.T.isVoidType()) { // error: declared with void type
        reporter.reportError(errMesg[3] + ": %", ast.I.spelling, ast.I.position);
      } else if (ast.T.isArrayType()) {
        if (((ArrayType) ast.T).E instanceof EmptyExpr) {
          reporter.reportError(errMesg[18] + ": %", ast.I.spelling, ast.I.position);
          return ast.T;
        } 
        
        Type arrayPrimaryType = (Type)((ArrayType)ast.T).T;
        if (arrayPrimaryType.isVoidType()) { // error: declared with void[] type
          reporter.reportError(errMesg[4] + ": %", ast.I.spelling, ast.I.position);
        }
      }
  
      // Check array type local var decl
      if (ast.T.isArrayType()) {
        // No initialiser
        if (ast.E instanceof EmptyExpr) {
            if (((ArrayExpr)ast.E).E.isEmptyExpr()) { // error: array size missing
              reporter.reportError(errMesg[18] + ": %", ast.I.spelling, ast.I.position);
            }
        }
  
        // With initialiser
        if (ast.E instanceof InitExpr) {
          ast.E.visit(this, ast);
        } else { // error: scalar initialize for array
          reporter.reportError(errMesg[15] + ": %", ast.I.spelling, ast.position);
        }
      } else {
      // Check non-array type local var decl
        if (ast.E instanceof InitExpr) { // error: array initializer for scalar
          reporter.reportError(errMesg[14], "", ast.E.position);
          return null;
        }
        Type exprType = (Type) ast.E.visit(this, ast);
        if(!ast.T.assignable(exprType)) { // error: incompetible type before and after "="
          reporter.reportError(errMesg[6] + ": %", ast.I.spelling, ast.I.position);
        }
      }
  
      ast.I.visit(this,null);
      ast.E.visit(this,ast);
  
      return null;
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

  public Object visitEmptyExprList(EmptyExprList ast, Object o) {
    return null;
  }

  public Object visitEmptyParaList(EmptyParaList ast, Object o) {
    return null;
  }

  public Object visitEmptyArgList(EmptyArgList ast, Object o) {
    if (!(o instanceof EmptyParaList)) { // para-list is passed all the way to this level to compare with the arg-list
      reporter.reportError(errMesg[26], "", ast.parent.position);
    } 
    return null;
}
  // Arguments

  // Your visitor methods for arguments go here

  public Object visitArgList(ArgList ast, Object o) {
    if (o instanceof EmptyParaList) { // para-list passed all the way to this level to compare with arg-list
      reporter.reportError(errMesg[25], "", ast.position);
    } else {
      ast.A.visit(this, ((ParaList)o).P);
      ast.AL.visit(this, ((ParaList)o).PL);
    }
    return null;
  }

  public Object visitArg(Arg ast, Object o) {
    // para decl is passed all the way here to compare with arg type
    Type paraType = ((ParaDecl)o).T;
    Type exprType = (Type) ast.E.visit(this, null);

    // Avoid spurous errors
    if (paraType.isErrorType() || exprType.isErrorType()) {
      ast.type = StdEnvironment.errorType;
      return ast.type;
    }

    // Array type: array name itself can used as an arguement in the function call //FIXME: para can be an array, but arg cannot, I think
    // Diff between para and arg (para is from func decl and arg is passed to the function)
    if (paraType.isArrayType() && exprType.isArrayType()) {
      if (!((ArrayType)paraType).T.assignable(((ArrayType)exprType).T)) {
        reporter.reportError(errMesg[27] + ": %", ((ParaDecl)o).I.spelling, ast.E.position);
        return StdEnvironment.errorType;
      }
    } else {
      // Non-array type: asssignable and type coersion
      if (!paraType.assignable(exprType)) {
        reporter.reportError(errMesg[27] + ": %", ((ParaDecl)o).I.spelling, ast.E.position);
        ast.type = StdEnvironment.errorType;
        return StdEnvironment.errorType;
      } else {
        if (paraType.isFloatType() && exprType.isIntType()) {
          ast.E = intToFloat(ast.E);
        }
      }
    }

    ast.type = paraType;
    return ast.type;
  }

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

  public Object visitArrayType(ArrayType ast, Object o) { //TODO:
    ast.E.visit(this, o);
    return ast;
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

  // Variables
  public Object visitSimpleVar(SimpleVar ast, Object o) {
    //TODO:
    Decl binding = (Decl) ast.I.visit(this, null);
    if (binding == null) {
      reporter.reportError(errMesg[5] + ": %", ast.I.spelling, ast.position);
      ast.type = StdEnvironment.errorType;
      return ast.type;
    } 
    
    // TODO: err[11]
    if (binding instanceof FuncDecl && !(o instanceof ExprStmt)) { //FIXME: is this necessary? //&& !(o instanceof ExprStmt)
      reporter.reportError(errMesg[11] + ": %", ast.I.spelling, ast.position);
      return StdEnvironment.errorType;
    }
    ast.type = binding.T;
    return ast.type;
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
