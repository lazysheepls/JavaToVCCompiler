/*
 *** Emitter.java 
 *** Sun  4 Apr 23:21:06 AEST 2020
 *** Jingling Xue, School of Computer Science, UNSW, Australia
 */

// A new frame object is created for every function just before the
// function is being translated in visitFuncDecl.
//
// All the information about the translation of a function should be
// placed in this Frame object and passed across the AST nodes as the
// 2nd argument of every visitor method in Emitter.java.

package VC.CodeGen;

import java.util.LinkedList;
import java.util.Enumeration;
import java.util.ListIterator;

import VC.ASTs.*;
import VC.ErrorReporter;
import VC.StdEnvironment;

public final class Emitter implements Visitor {

  private ErrorReporter errorReporter;
  private String inputFilename;
  private String classname;
  private String outputFilename;

  public Emitter(String inputFilename, ErrorReporter reporter) {
    this.inputFilename = inputFilename;
    errorReporter = reporter;
    
    int i = inputFilename.lastIndexOf('.');
    if (i > 0)
      classname = inputFilename.substring(0, i);
    else
      classname = inputFilename;
    
  }

  // PRE: ast must be a Program node

  public final void gen(AST ast) {
    ast.visit(this, null); 
    JVM.dump(classname + ".j");
  }
    
  // Programs
  public Object visitProgram(Program ast, Object o) {
     /** This method works for scalar variables only. You need to modify
         it to handle all array-related declarations and initialisations.
      **/ 

    // Generates the default constructor initialiser 
    emit(JVM.CLASS, "public", classname);
    emit(JVM.SUPER, "java/lang/Object");

    emit("");

    // Three subpasses:

    // (1) Generate .field definition statements since
    //     these are required to appear before method definitions
    List list = ast.FL;
    while (!list.isEmpty()) {
      DeclList dlAST = (DeclList) list;
      if (dlAST.D instanceof GlobalVarDecl) {
        GlobalVarDecl vAST = (GlobalVarDecl) dlAST.D;
        emit(JVM.STATIC_FIELD, vAST.I.spelling, VCtoJavaType(vAST.T));
        }
      list = dlAST.DL;
    }

    emit("");

    // (2) Generate <clinit> for global variables (assumed to be static)
 
    emit("; standard class static initializer ");
    emit(JVM.METHOD_START, "static <clinit>()V");
    emit("");

    // create a Frame for <clinit>

    Frame frame = new Frame(false);

    list = ast.FL;
    while (!list.isEmpty()) {
      DeclList dlAST = (DeclList) list;
      if (dlAST.D instanceof GlobalVarDecl) {
        GlobalVarDecl vAST = (GlobalVarDecl) dlAST.D;
        if (vAST.T.isArrayType()) { // Global var decl
          ArrayType arrType = (ArrayType) vAST.T;
          Type arrayPrimaryType = arrType.T;
          // put array size onto stack
          arrType.E.visit(this, frame);
          // decalre new array
          emit(JVM.NEWARRAY, VCtoJavaType(arrayPrimaryType));
          //FIXME: not sure about this (maybe for the visitor to continue)

          if (!vAST.E.isEmptyExpr()) {
            vAST.E.visit(this, frame);
          }

          // store array-ref to static
          emitPUTSTATIC(arrType.toString(), vAST.I.spelling);
          frame.pop();

        } else {
          if (!vAST.E.isEmptyExpr()) {
            vAST.E.visit(this, frame);
          } else {
            if (vAST.T.equals(StdEnvironment.floatType))
              emit(JVM.FCONST_0);
            else
              emit(JVM.ICONST_0);
            frame.push();
          }
          emitPUTSTATIC(VCtoJavaType(vAST.T), vAST.I.spelling); 
          frame.pop();
        }
      }
      list = dlAST.DL;
    }
   
    emit("");
    emit("; set limits used by this method");
    emit(JVM.LIMIT, "locals", frame.getNewIndex());

    emit(JVM.LIMIT, "stack", frame.getMaximumStackSize());
    emit(JVM.RETURN);
    emit(JVM.METHOD_END, "method");

    emit("");

    // (3) Generate Java bytecode for the VC program

    emit("; standard constructor initializer ");
    emit(JVM.METHOD_START, "public <init>()V");
    emit(JVM.LIMIT, "stack 1");
    emit(JVM.LIMIT, "locals 1");
    emit(JVM.ALOAD_0);
    emit(JVM.INVOKESPECIAL, "java/lang/Object/<init>()V");
    emit(JVM.RETURN);
    emit(JVM.METHOD_END, "method");

    return ast.FL.visit(this, o);
  }

  // Statements

  public Object visitStmtList(StmtList ast, Object o) {
    ast.S.visit(this, o);
    ast.SL.visit(this, o);
    return null;
  }

  public Object visitIfStmt(IfStmt ast, Object o) {
    Frame frame = (Frame) o;

    String elseLabel = frame.getNewLabel();
    String nextLabel = frame.getNewLabel();

    ast.E.visit(this, o);
    frame.pop();

    emit(JVM.IFEQ, elseLabel); // if E == 0, goto else

    // content under if
    ast.S1.visit(this, o);
    emit(JVM.GOTO, nextLabel);

    // label:
    emit(elseLabel + ":");
    ast.S2.visit(this, o);

    // label:
    emit(nextLabel + ":");
    return null;
  }

  public Object visitWhileStmt(WhileStmt ast, Object o){
    Frame frame = (Frame) o;
    String contLabel = frame.getNewLabel();
    String breakLabel = frame.getNewLabel();

    // push label onto different stack
    frame.conStack.push(contLabel);
    frame.brkStack.push(breakLabel);

    emit(contLabel + ":");
    ast.E.visit(this, o);
    frame.pop();

    // label:
    emit(JVM.IFEQ, breakLabel);
    ast.S.visit(this, o);
    emit(JVM.GOTO, contLabel);

    // label:
    emit(breakLabel + ":");
    frame.conStack.pop();
    frame.brkStack.pop();
    
    return null;
  }

  public Object visitForStmt(ForStmt ast, Object o) {
    Frame frame = (Frame) o;
    String startLabel = frame.getNewLabel();
    String contLabel = frame.getNewLabel();
    String breakLabel = frame.getNewLabel();

    // push label onto different stack
    frame.conStack.push(contLabel);
    frame.brkStack.push(breakLabel);

    ast.E1.visit(this, o); // init condition
    emit(startLabel + ":");

    if (ast.E2.isEmptyExpr()) {  // When E2 is empty
      emit(JVM.ICONST_1);
      frame.push();
    } else { // When E2 is not empty
      ast.E2.visit(this, o);
    }

    emit(JVM.IFEQ, breakLabel);
    frame.pop();

    ast.S.visit(this, o);

    emit(contLabel + ":");
    ast.E3.visit(this, o);
    emit(JVM.GOTO, startLabel);

    emit(breakLabel + ":");
    frame.conStack.pop();
    frame.brkStack.pop();

    return null;
  }

  public Object visitBreakStmt(BreakStmt ast, Object o) {
    Frame frame = (Frame) o;
    String breakLabel = frame.brkStack.peek();
    emit(JVM.GOTO, breakLabel);
    return null;
  }

  public Object visitContinueStmt(ContinueStmt ast, Object o) {
    Frame frame = (Frame) o;
    String contLabel = frame.conStack.peek();
    emit(JVM.GOTO, contLabel);
    return null;
  }

  public Object visitCompoundStmt(CompoundStmt ast, Object o) {
    Frame frame = (Frame) o; 

    String scopeStart = frame.getNewLabel();
    String scopeEnd = frame.getNewLabel();
    frame.scopeStart.push(scopeStart);
    frame.scopeEnd.push(scopeEnd);
   
    emit(scopeStart + ":");
    if (ast.parent instanceof FuncDecl) {
      if (((FuncDecl) ast.parent).I.spelling.equals("main")) {
        emit(JVM.VAR, "0 is argv [Ljava/lang/String; from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());
        emit(JVM.VAR, "1 is vc$ L" + classname + "; from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());
        // Generate code for the initialiser vc$ = new classname();
        emit(JVM.NEW, classname);
        emit(JVM.DUP);
        frame.push(2);
        emit("invokenonvirtual", classname + "/<init>()V");
        frame.pop();
        emit(JVM.ASTORE_1);
        frame.pop();
      } else {
        emit(JVM.VAR, "0 is this L" + classname + "; from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());
        ((FuncDecl) ast.parent).PL.visit(this, o);
      }
    }
    ast.DL.visit(this, o);
    ast.SL.visit(this, o);
    emit(scopeEnd + ":");

    frame.scopeStart.pop();
    frame.scopeEnd.pop();
    return null;
  }

public Object visitReturnStmt(ReturnStmt ast, Object o) {
    Frame frame = (Frame)o;

/*
  int main() { return 0; } must be interpretted as 
  public static void main(String[] args) { return ; }
  Therefore, "return expr", if present in the main of a VC program
  must be translated into a RETURN rather than IRETURN instruction.
*/

     if (frame.isMain())  {
        emit(JVM.RETURN);
        return null;
     }

// Your other code goes here
     ast.E.visit(this, o);
     if(ast.E.type.isIntType() || ast.E.type.isBooleanType()){
       emit(JVM.IRETURN);
       frame.pop();
     } else if (ast.E.type.isFloatType()) {
       emit(JVM.FRETURN);
       frame.pop();
     }
     return null;
  }

  public Object visitExprStmt(ExprStmt ast, Object o) {
    ast.E.visit(this, o);
    return null;
  }

  public Object visitEmptyStmtList(EmptyStmtList ast, Object o) {
    return null;
  }

  public Object visitEmptyExprList(EmptyExprList ast, Object o) {
    return null;
  }

  public Object visitEmptyCompStmt(EmptyCompStmt ast, Object o) {
    return null;
  }

  public Object visitEmptyStmt(EmptyStmt ast, Object o) {
    return null;
  }

  // Expressions

  public Object visitCallExpr(CallExpr ast, Object o) {
    Frame frame = (Frame) o;
    String fname = ast.I.spelling;

    if (fname.equals("getInt")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System.getInt()I");
      frame.push();
    } else if (fname.equals("putInt")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System.putInt(I)V");
      frame.pop();
    } else if (fname.equals("putIntLn")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System/putIntLn(I)V");
      frame.pop();
    } else if (fname.equals("getFloat")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System/getFloat()F");
      frame.push();
    } else if (fname.equals("putFloat")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System/putFloat(F)V");
      frame.pop();
    } else if (fname.equals("putFloatLn")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System/putFloatLn(F)V");
      frame.pop();
    } else if (fname.equals("putBool")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System/putBool(Z)V");
      frame.pop();
    } else if (fname.equals("putBoolLn")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System/putBoolLn(Z)V");
      frame.pop();
    } else if (fname.equals("putString")) {
      ast.AL.visit(this, o);
      emit(JVM.INVOKESTATIC, "VC/lang/System/putString(Ljava/lang/String;)V");
      frame.pop();
    } else if (fname.equals("putStringLn")) {
      ast.AL.visit(this, o);
      emit(JVM.INVOKESTATIC, "VC/lang/System/putStringLn(Ljava/lang/String;)V");
      frame.pop();
    } else if (fname.equals("putLn")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System/putLn()V");
    } else { // programmer-defined functions

      FuncDecl fAST = (FuncDecl) ast.I.decl;

      // all functions except main are assumed to be instance methods
      if (frame.isMain()) 
        emit("aload_1"); // vc.funcname(...)
      else
        emit("aload_0"); // this.funcname(...)
      frame.push();

      ast.AL.visit(this, o);
    
      String retType = VCtoJavaType(fAST.T);
      
      // The types of the parameters of the called function are not
      // directly available in the FuncDecl node but can be gathered
      // by traversing its field PL.

      StringBuffer argsTypes = new StringBuffer("");
      List fpl = fAST.PL;
      while (! fpl.isEmpty()) {
        if (((ParaList) fpl).P.T.equals(StdEnvironment.booleanType))
          argsTypes.append("Z");         
        else if (((ParaList) fpl).P.T.equals(StdEnvironment.intType))
          argsTypes.append("I");         
        else
          argsTypes.append("F");         
        fpl = ((ParaList) fpl).PL;
      }
      
      emit("invokevirtual", classname + "/" + fname + "(" + argsTypes + ")" + retType);
      frame.pop(argsTypes.length() + 1);

      if (! retType.equals("V"))
        frame.push();
    }
    return null;
  }

  public Object visitEmptyExpr(EmptyExpr ast, Object o) {
    return null;
  }

  public Object visitIntExpr(IntExpr ast, Object o) {
    ast.IL.visit(this, o);
    return null;
  }

  public Object visitFloatExpr(FloatExpr ast, Object o) {
    ast.FL.visit(this, o);
    return null;
  }

  public Object visitBooleanExpr(BooleanExpr ast, Object o) {
    ast.BL.visit(this, o);
    return null;
  }

  public Object visitStringExpr(StringExpr ast, Object o) {
    ast.SL.visit(this, o);
    return null;
  }

  public Object visitUnaryExpr(UnaryExpr ast, Object o) {
    Frame frame = (Frame) o;

    // Push expr on to operand stack
    ast.E.visit(this, o);

    switch(ast.O.spelling){
      case "i-":
        frame.pop();
        emit(JVM.INEG);
        frame.push();
        break;
      case "f-":
        frame.pop();
        emit(JVM.FNEG);
        frame.push();
        break;
      case "i2f":
        frame.pop();
        emit(JVM.I2F);
        frame.push();
        break;
      case "i!":
        String falseLabel = frame.getNewLabel();
        String nextLabel = frame.getNewLabel();

        frame.pop();
        // if E is FALSE(0), True -> go to label success
        emit(JVM.IFEQ, falseLabel); //ifeq succeeds ifff value = 0
        // invert TRUE(1) to FALSE(0)
        emit(JVM.ICONST_0);
        emit(JVM.GOTO, nextLabel);

        //Label:
        emit(falseLabel + ":");
        emit(JVM.ICONST_1); // if 0, invert to 1

        //Label:
        emit(nextLabel + ":");
        frame.push();
        break;
      default:
        break;
    }
    return null;
  }

  public Object visitBinaryExpr(BinaryExpr ast, Object o){
    Frame frame = (Frame) o;

    switch(ast.O.spelling){
      case "i||":
        String trueLabel = frame.getNewLabel();
        String nextLabelOR = frame.getNewLabel();

        //if E1 is TRUE
        ast.E1.visit(this, o);
        frame.pop();
        emit(JVM.IFNE, trueLabel);
        
        //else if E2 is TRUE
        ast.E2.visit(this, o);
        frame.pop();
        emit(JVM.IFNE, trueLabel);

        //E1 and E2 are both FALSE
        emit(JVM.ICONST_0);
        emit(JVM.GOTO, nextLabelOR);

        //Label:
        emit(trueLabel + ":");
        emit(JVM.ICONST_1);

        //Label:
        emit(nextLabelOR + ":");
        frame.push();
        break;
      case "i&&":
        String falseLabel = frame.getNewLabel();
        String nextLabelAND = frame.getNewLabel();

        //if E1 is false
        ast.E1.visit(this, o);
        frame.pop();
        emit(JVM.IFEQ, falseLabel);

        //else if E2 is false
        ast.E2.visit(this, o);
        frame.pop();
        emit(JVM.IFEQ, falseLabel);

        //E1 and E2 are both TRUE
        emit(JVM.ICONST_1);
        emit(JVM.GOTO, nextLabelAND);

        //Label:
        emit(falseLabel + ":");
        emit(JVM.ICONST_0);

        //Label:
        emit(nextLabelAND + ":");
        frame.push();
        break;
      default:
        ast.E1.visit(this, o);
        ast.E2.visit(this, o);
        switch(ast.O.spelling){
          case "i+":
            frame.pop(2);
            emit(JVM.IADD);
            frame.push();
            break;
          case "i-":
            frame.pop(2);
            emit(JVM.ISUB);
            frame.push();
            break;
          case "i*":
            frame.pop(2);
            emit(JVM.IMUL);
            frame.push();
            break;
          case "i/":
            frame.pop(2);
            emit(JVM.IDIV);
            frame.push();
            break;
          case "f+":
            frame.pop(2);
            emit(JVM.FADD);
            frame.push();
            break;
          case "f-":
            frame.pop(2);
            emit(JVM.FSUB);
            frame.push();
            break;
          case "f*":
            frame.pop(2);
            emit(JVM.FMUL);
            frame.push();
            break;
          case "f/":
            frame.pop(2);
            emit(JVM.FDIV);
            frame.push();
            break;
          case "i!=":
          case "i==":
          case "i<":
          case "i<=":
          case "i>":
          case "i>=":
            emitIF_ICMPCOND(ast.O.spelling, frame);
            break;
          case "f!=":
          case "f==":
          case "f<":
          case "f<=":
          case "f>":
          case "f>=":
            emitFCMP(ast.O.spelling, frame);
            break;
          default:
            break;
        }
        break;
    }
    return null;
  }

  public Object visitInitExpr(InitExpr ast, Object o) {
    Frame frame = (Frame) o;
    List list = ast.IL;
    Integer index = 0;
    Type arrayPrimaryType = ((ArrayType) ((Decl) ast.parent).T).T;

    //store each expr in the initialiser into the array
    while(!list.isEmptyExprList()){
      // put array ref onto stack
      emit(JVM.DUP);
      frame.push();

      // put index onto stack
      emitICONST(index);
      frame.push();

      // put value onto stack
      ((ExprList) list).E.visit(this, o);

      // store value to array (int, float, bool)
      emitXASTORE(arrayPrimaryType, frame);

      // go to next value
      list = ((ExprList) list).EL;
      index++;
    }
    return null;
  }

  public Object visitArrayExpr(ArrayExpr ast, Object o) {
    Frame frame = (Frame) o;
    Boolean isRHS = true;

    ast.V.visit(this, frame); // load array-ref  onto stack
    ast.E.visit(this, frame); // load index onto stack

    // Determine if the arrayExpr is on the right hand side (RHS)
    // Only load value onto stack if it is RHS
    if (ast.parent instanceof AssignExpr)
      isRHS = false;

    if(isRHS)
      emitXALOAD(ast.type, frame);

    return null;
  }

  public Object visitAssignExpr(AssignExpr ast, Object o) {
    Frame frame = (Frame) o;
    
    if (ast.E1 instanceof ArrayExpr) {
      ast.E1.visit(this, o); // get array-ref and index
      ast.E2.visit(this, o); // get value
      emitXASTORE(ast.E2.type, frame); // store value to array
    } else {
      ast.E2.visit(this, o); // put value on stack

      //Check if E1 is a gobal var
      Ident id = ((SimpleVar) ((VarExpr) ast.E1).V).I;
      Decl decl = (Decl) id.decl;
      if(decl instanceof GlobalVarDecl){
        emitPUTSTATIC(VCtoJavaType(decl.T), id.spelling); 
      } else { //local var
        if (ast.E2.type.isIntType() || ast.E2.type.isBooleanType())
          emitISTORE(id);
        else if (ast.E2.type.isFloatType())
          emitFSTORE(id);
      }
      frame.pop();
    }
    return null;
  }

  public Object visitExprList(ExprList ast, Object o) {
    return null;
  }

  public Object visitVarExpr(VarExpr ast, Object o) {
    ast.V.visit(this, o);
    return null;
  }

  // Declarations

  public Object visitDeclList(DeclList ast, Object o) {
    ast.D.visit(this, o);
    ast.DL.visit(this, o);
    return null;
  }

  public Object visitEmptyDeclList(EmptyDeclList ast, Object o) {
    return null;
  }

  public Object visitFuncDecl(FuncDecl ast, Object o) {

    Frame frame; 

    if (ast.I.spelling.equals("main")) {

       frame = new Frame(true);

      // Assume that main has one String parameter and reserve 0 for it
      frame.getNewIndex(); 

      emit(JVM.METHOD_START, "public static main([Ljava/lang/String;)V"); 
      // Assume implicitly that
      //      classname vc$; 
      // appears before all local variable declarations.
      // (1) Reserve 1 for this object reference.

      frame.getNewIndex(); 

    } else {

       frame = new Frame(false);

      // all other programmer-defined functions are treated as if
      // they were instance methods
      frame.getNewIndex(); // reserve 0 for "this"

      String retType = VCtoJavaType(ast.T);

      // The types of the parameters of the called function are not
      // directly available in the FuncDecl node but can be gathered
      // by traversing its field PL.

      StringBuffer argsTypes = new StringBuffer("");
      List fpl = ast.PL;
      while (! fpl.isEmpty()) {
        if (((ParaList) fpl).P.T.equals(StdEnvironment.booleanType))
          argsTypes.append("Z");         
        else if (((ParaList) fpl).P.T.equals(StdEnvironment.intType))
          argsTypes.append("I");         
        else
          argsTypes.append("F");         
        fpl = ((ParaList) fpl).PL;
      }

      emit(JVM.METHOD_START, ast.I.spelling + "(" + argsTypes + ")" + retType);
    }

    ast.S.visit(this, frame);

    // JVM requires an explicit return in every method. 
    // In VC, a function returning void may not contain a return, and
    // a function returning int or float is not guaranteed to contain
    // a return. Therefore, we add one at the end just to be sure.

    if (ast.T.equals(StdEnvironment.voidType)) {
      emit("");
      emit("; return may not be present in a VC function returning void"); 
      emit("; The following return inserted by the VC compiler");
      emit(JVM.RETURN); 
    } else if (ast.I.spelling.equals("main")) {
      // In case VC's main does not have a return itself
      emit(JVM.RETURN);
    } else
      emit(JVM.NOP); 

    emit("");
    emit("; set limits used by this method");
    emit(JVM.LIMIT, "locals", frame.getNewIndex());

    emit(JVM.LIMIT, "stack", frame.getMaximumStackSize());
    emit(".end method");

    return null;
  }

  public Object visitGlobalVarDecl(GlobalVarDecl ast, Object o) {
    // nothing to be done
    return null;
  }

  public Object visitLocalVarDecl(LocalVarDecl ast, Object o) {
    Frame frame = (Frame) o;
    ast.index = frame.getNewIndex();

    // .var derectives
    String T = VCtoJavaType(ast.T);

    if (ast.T.isArrayType()){
      T = ast.T.toString();
    }

    emit(JVM.VAR + " " + ast.index + " is " + ast.I.spelling + " " + T + " from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());

    // array decl
    if (ast.T.isArrayType()) {
      ArrayType arrType = (ArrayType) ast.T;
      Type arrPrimaryType = arrType.T;
      // put array size onto stack
      arrType.E.visit(this, o);
      // Create array with type
      emit(JVM.NEWARRAY, VCtoJavaType(arrPrimaryType));
      // store array ref to local variable array
      emitASTORE(ast.index);
      frame.pop();
      
    } else { // scalar decl
      if (!ast.E.isEmptyExpr()) {
        ast.E.visit(this, o);
    
        if (ast.T.equals(StdEnvironment.floatType)) {
          // cannot call emitFSTORE(ast.I) since this I is not an
          // applied occurrence 
          if (ast.index >= 0 && ast.index <= 3) 
            emit(JVM.FSTORE + "_" + ast.index); 
          else
            emit(JVM.FSTORE, ast.index); 
          frame.pop();
        } else {
          // cannot call emitISTORE(ast.I) since this I is not an
          // applied occurrence 
          if (ast.index >= 0 && ast.index <= 3) 
            emit(JVM.ISTORE + "_" + ast.index); 
          else
            emit(JVM.ISTORE, ast.index); 
          frame.pop();
        }
      }
    }

    return null;
  }

  // Parameters

  public Object visitParaList(ParaList ast, Object o) {
    ast.P.visit(this, o);
    ast.PL.visit(this, o);
    return null;
  }

  public Object visitParaDecl(ParaDecl ast, Object o) {
    Frame frame = (Frame) o;
    ast.index = frame.getNewIndex();
    String T = VCtoJavaType(ast.T);

    if (ast.T.isArrayType()) {
      T = ast.T.toString();
    } 

    emit(JVM.VAR + " " + ast.index + " is " + ast.I.spelling + " " + T + " from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());
    return null;
  }

  public Object visitEmptyParaList(EmptyParaList ast, Object o) {
    return null;
  }

  // Arguments

  public Object visitArgList(ArgList ast, Object o) {
    ast.A.visit(this, o);
    ast.AL.visit(this, o);
    return null;
  }

  public Object visitArg(Arg ast, Object o) {
    ast.E.visit(this, o);
    return null;
  }

  public Object visitEmptyArgList(EmptyArgList ast, Object o) {
    return null;
  }

  // Types

  public Object visitIntType(IntType ast, Object o) {
    return null;
  }

  public Object visitFloatType(FloatType ast, Object o) {
    return null;
  }

  public Object visitBooleanType(BooleanType ast, Object o) {
    return null;
  }

  public Object visitVoidType(VoidType ast, Object o) {
    return null;
  }

  public Object visitStringType(StringType ast, Object o) {
    return null;
  }

  public Object visitArrayType(ArrayType ast, Object o) {
    return null;
  }

  public Object visitErrorType(ErrorType ast, Object o) {
    return null;
  }

  // Literals, Identifiers and Operators 

  public Object visitIdent(Ident ast, Object o) {
    return null;
  }

  public Object visitIntLiteral(IntLiteral ast, Object o) {
    Frame frame = (Frame) o;
    emitICONST(Integer.parseInt(ast.spelling));
    frame.push();
    return null;
  }

  public Object visitFloatLiteral(FloatLiteral ast, Object o) {
    Frame frame = (Frame) o;
    emitFCONST(Float.parseFloat(ast.spelling));
    frame.push();
    return null;
  }

  public Object visitBooleanLiteral(BooleanLiteral ast, Object o) {
    Frame frame = (Frame) o;
    emitBCONST(ast.spelling.equals("true"));
    frame.push();
    return null;
  }

  public Object visitStringLiteral(StringLiteral ast, Object o) {
    Frame frame = (Frame) o;
    emit(JVM.LDC, "\"" + ast.spelling + "\"");
    frame.push();
    return null;
  }

  public Object visitOperator(Operator ast, Object o) {
    return null;
  }

  // Variables 

  public Object visitSimpleVar(SimpleVar ast, Object o) {
    Frame frame = (Frame) o;
    Ident id = ast.I;
    Decl decl = (Decl) ast.I.decl;

    if (decl instanceof GlobalVarDecl) {
      //FIXME: not sure about T and I
      if (ast.type.isArrayType()) {
        emitGETSTATIC(ast.type.toString(), id.spelling);
      } else {
        emitGETSTATIC(VCtoJavaType(decl.T), id.spelling);
      }
    } else { //LocalVar or Parameter
      if (ast.type.isIntType() || ast.type.isBooleanType()) {
        emitILOAD(decl.index);
      } else if (ast.type.isFloatType()) {
        emitFLOAD(decl.index);
      } else if (ast.type.isArrayType()) {
        emitALOAD(decl.index);
      }
    }

    frame.push();
    return null;
  }

  // Auxiliary methods for byte code generation

  // The following method appends an instruction directly into the JVM 
  // Code Store. It is called by all other overloaded emit methods.

  private void emit(String s) {
    JVM.append(new Instruction(s)); 
    //FIXME: Remove afte debugging
    System.out.println(s);
  }

  private void emit(String s1, String s2) {
    emit(s1 + " " + s2);
  }

  private void emit(String s1, int i) {
    emit(s1 + " " + i);
  }

  private void emit(String s1, float f) {
    emit(s1 + " " + f);
  }

  private void emit(String s1, String s2, int i) {
    emit(s1 + " " + s2 + " " + i);
  }

  private void emit(String s1, String s2, String s3) {
    emit(s1 + " " + s2 + " " + s3);
  }

  private void emitIF_ICMPCOND(String op, Frame frame) {
    String opcode;

    if (op.equals("i!="))
      opcode = JVM.IF_ICMPNE;
    else if (op.equals("i=="))
      opcode = JVM.IF_ICMPEQ;
    else if (op.equals("i<"))
      opcode = JVM.IF_ICMPLT;
    else if (op.equals("i<="))
      opcode = JVM.IF_ICMPLE;
    else if (op.equals("i>"))
      opcode = JVM.IF_ICMPGT;
    else // if (op.equals("i>="))
      opcode = JVM.IF_ICMPGE;

    String falseLabel = frame.getNewLabel();
    String nextLabel = frame.getNewLabel();

    emit(opcode, falseLabel);
    frame.pop(2); 
    emit("iconst_0");
    emit("goto", nextLabel);
    emit(falseLabel + ":");
    emit(JVM.ICONST_1);
    frame.push(); 
    emit(nextLabel + ":");
  }

  private void emitFCMP(String op, Frame frame) {
    String opcode;

    if (op.equals("f!="))
      opcode = JVM.IFNE;
    else if (op.equals("f=="))
      opcode = JVM.IFEQ;
    else if (op.equals("f<"))
      opcode = JVM.IFLT;
    else if (op.equals("f<="))
      opcode = JVM.IFLE;
    else if (op.equals("f>"))
      opcode = JVM.IFGT;
    else // if (op.equals("f>="))
      opcode = JVM.IFGE;

    String falseLabel = frame.getNewLabel();
    String nextLabel = frame.getNewLabel();

    emit(JVM.FCMPG);
    frame.pop(2);
    emit(opcode, falseLabel);
    emit(JVM.ICONST_0);
    emit("goto", nextLabel);
    emit(falseLabel + ":");
    emit(JVM.ICONST_1);
    frame.push();
    emit(nextLabel + ":");

  }

  private void emitILOAD(int index) {
    if (index >= 0 && index <= 3) 
      emit(JVM.ILOAD + "_" + index); 
    else
      emit(JVM.ILOAD, index); 
  }

  private void emitFLOAD(int index) {
    if (index >= 0 && index <= 3) 
      emit(JVM.FLOAD + "_"  + index); 
    else
      emit(JVM.FLOAD, index); 
  }

  private void emitALOAD(int index) {
    if (index >= 0 && index <= 3) 
      emit(JVM.ALOAD + "_"  + index); 
    else
      emit(JVM.ALOAD, index); 
  }

  private void emitASTORE(int index) {
    if (index >= 0 && index <= 3) 
      emit(JVM.ASTORE + "_"  + index); 
    else
      emit(JVM.ASTORE, index); 
  }

  private void emitGETSTATIC(String T, String I) {
    emit(JVM.GETSTATIC, classname + "/" + I, T); 
  }

  private void emitISTORE(Ident ast) {
    int index;
    if (ast.decl instanceof ParaDecl)
      index = ((ParaDecl) ast.decl).index; 
    else
      index = ((LocalVarDecl) ast.decl).index; 
    
    if (index >= 0 && index <= 3) 
      emit(JVM.ISTORE + "_" + index); 
    else
      emit(JVM.ISTORE, index); 
  }

  private void emitFSTORE(Ident ast) {
    int index;
    if (ast.decl instanceof ParaDecl)
      index = ((ParaDecl) ast.decl).index; 
    else
      index = ((LocalVarDecl) ast.decl).index; 
    if (index >= 0 && index <= 3) 
      emit(JVM.FSTORE + "_" + index); 
    else
      emit(JVM.FSTORE, index); 
  }

  private void emitPUTSTATIC(String T, String I) {
    emit(JVM.PUTSTATIC, classname + "/" + I, T); 
  }

  private void emitICONST(int value) {
    if (value == -1)
      emit(JVM.ICONST_M1); 
    else if (value >= 0 && value <= 5) 
      emit(JVM.ICONST + "_" + value); 
    else if (value >= -128 && value <= 127) 
      emit(JVM.BIPUSH, value); 
    else if (value >= -32768 && value <= 32767)
      emit(JVM.SIPUSH, value); 
    else 
      emit(JVM.LDC, value); 
  }

  private void emitFCONST(float value) {
    if(value == 0.0)
      emit(JVM.FCONST_0); 
    else if(value == 1.0)
      emit(JVM.FCONST_1); 
    else if(value == 2.0)
      emit(JVM.FCONST_2); 
    else 
      emit(JVM.LDC, value); 
  }

  private void emitBCONST(boolean value) {
    if (value)
      emit(JVM.ICONST_1);
    else
      emit(JVM.ICONST_0);
  }

  private void emitXASTORE(Type ast, Frame frame) {
    if (ast.isIntType())
      emit(JVM.IASTORE);
    else if (ast.isFloatType())
      emit(JVM.FASTORE);
    else if (ast.isBooleanType())
      emit(JVM.BASTORE);

    frame.pop(3); //arrayref, index, value ->
  }

  private void emitXALOAD(Type ast, Frame frame) {
    if (ast.isIntType())
      emit(JVM.IALOAD);
    else if (ast.isFloatType())
      emit(JVM.FALOAD);
    else if (ast.isBooleanType())
      emit(JVM.BALOAD);
    frame.pop(2); //arrayref, index ->
    frame.push(); //value
  }

  private String VCtoJavaType(Type t) {
    if (t.equals(StdEnvironment.booleanType))
      return "Z";
    else if (t.equals(StdEnvironment.intType))
      return "I";
    else if (t.equals(StdEnvironment.floatType))
      return "F";
    else // if (t.equals(StdEnvironment.voidType))
      return "V";
  }

}
