/*
 * Parser.java            
 *
 * This parser for a subset of the VC language is intended to 
 *  demonstrate how to create the AST nodes, including (among others): 
 *  [1] a list (of statements)
 *  [2] a function
 *  [3] a statement (which is an expression statement), 
 *  [4] a unary expression
 *  [5] a binary expression
 *  [6] terminals (identifiers, integer literals and operators)
 *
 * In addition, it also demonstrates how to use the two methods start 
 * and finish to determine the position information for the start and 
 * end of a construct (known as a phrase) corresponding an AST node.
 *
 * NOTE THAT THE POSITION INFORMATION WILL NOT BE MARKED. HOWEVER, IT CAN BE
 * USEFUL TO DEBUG YOUR IMPLEMENTATION.
 *
 * --- 5-March-2020 --- 


program       -> func-decl
func-decl     -> type identifier "(" ")" compound-stmt
type          -> void
identifier    -> ID
// statements
compound-stmt -> "{" stmt* "}" 
stmt          -> expr-stmt
expr-stmt     -> expr? ";"
// expressions 
expr                -> additive-expr
additive-expr       -> multiplicative-expr
                    |  additive-expr "+" multiplicative-expr
                    |  additive-expr "-" multiplicative-expr
multiplicative-expr -> unary-expr
	            |  multiplicative-expr "*" unary-expr
	            |  multiplicative-expr "/" unary-expr
unary-expr          -> "-" unary-expr
		    |  primary-expr

primary-expr        -> identifier
 		    |  INTLITERAL
		    | "(" expr ")"
 */

package VC.Parser;

import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import VC.ErrorReporter;
import VC.ASTs.*;

public class Parser {

  private Scanner scanner;
  private ErrorReporter errorReporter;
  private Token currentToken;
  private SourcePosition previousTokenPosition;
  private SourcePosition dummyPos = new SourcePosition();

  public Parser (Scanner lexer, ErrorReporter reporter) {
    scanner = lexer;
    errorReporter = reporter;

    previousTokenPosition = new SourcePosition();

    currentToken = scanner.getToken();
  }

// match checks to see f the current token matches tokenExpected.
// If so, fetches the next token.
// If not, reports a syntactic error.

  void match(int tokenExpected) throws SyntaxError {
    if (currentToken.kind == tokenExpected) {
      previousTokenPosition = currentToken.position;
      currentToken = scanner.getToken();
    } else {
      syntacticError("\"%\" expected here", Token.spell(tokenExpected));
    }
  }

  void accept() {
    previousTokenPosition = currentToken.position;
    currentToken = scanner.getToken();
  }

  void syntacticError(String messageTemplate, String tokenQuoted) throws SyntaxError {
    SourcePosition pos = currentToken.position;
    errorReporter.reportError(messageTemplate, tokenQuoted, pos);
    throw(new SyntaxError());
  }

// start records the position of the start of a phrase.
// This is defined to be the position of the first
// character of the first token of the phrase.

  void start(SourcePosition position) {
    position.lineStart = currentToken.position.lineStart;
    position.charStart = currentToken.position.charStart;
  }

// finish records the position of the end of a phrase.
// This is defined to be the position of the last
// character of the last token of the phrase.

  void finish(SourcePosition position) {
    position.lineFinish = previousTokenPosition.lineFinish;
    position.charFinish = previousTokenPosition.charFinish;
  }

  void copyStart(SourcePosition from, SourcePosition to) {
    to.lineStart = from.lineStart;
    to.charStart = from.charStart;
  }

// ========================== PROGRAMS ========================
  public Program parseProgram() {
    Program programAST = null;
    SourcePosition programPos = new SourcePosition();
    start(programPos);

    Type typeAST = null;
    Ident idAST = null;
    List declList = new EmptyDeclList(dummyPos);
    Decl funcDeclAST = null;
    Decl globalVarDeclAST = null;

    try{
      if(currentToken.kind != Token.EOF){
        typeAST = parseType();
        idAST = parseIdent();
        declList = parseDeclList(typeAST, idAST);
      }
      finish(programPos);
      programAST = new Program(declList, programPos);
      return programAST;
    } 
    catch (SyntaxError s) { return null; }
  }

// ========================== DECLARATIONS ========================
  List parseDeclList(Type tAST, Ident idAST) throws SyntaxError {
    Decl dAST = null;
    List dlList = null;

    SourcePosition declPos = new SourcePosition();
    start(declPos);

    boolean hasMoreThanOneVar = false;
    // Parse declaration
    if(currentToken.kind == Token.LPAREN) {
      dAST = parseFuncDecl(tAST, idAST);
    } 
    else 
    {
      dAST = parseGlobalVarDecl(tAST, idAST);
      if(currentToken.kind == Token.SEMICOLON){
        match(Token.SEMICOLON);
      } else {
        match (Token.COMMA);
        hasMoreThanOneVar = true;
      }
    }

    // Parse expr list
    if (currentToken.kind == Token.VOID ||
        currentToken.kind == Token.BOOLEAN ||
        currentToken.kind == Token.INT ||
        currentToken.kind == Token.FLOAT){
          tAST = parseType();
          idAST = parseIdent();
          dlList = parseDeclList(tAST, idAST);
          finish(declPos);
          dlList = new DeclList(dAST, dlList, declPos);
      }
      else if(currentToken.kind == Token.ID && hasMoreThanOneVar) //continue with the decl-list (one level down)
      {
        idAST = parseIdent();
        if(currentToken.kind == Token.LPAREN)
          match(Token.LPAREN);
        dlList = parseDeclList(tAST, idAST);
        finish(declPos);
        dlList = new DeclList(dAST, dlList, declPos);
      }
      else if (dAST != null) {
        finish(declPos);
        dlList = new DeclList(dAST, new EmptyDeclList(dummyPos), declPos);
      }
      if (dAST == null) {
        dlList = new EmptyDeclList(dummyPos);
      }
    
      return dlList;
  }

  Decl parseFuncDecl(Type tAST, Ident idAST) throws SyntaxError {

    Decl fAST = null; 
    
    SourcePosition funcPos = new SourcePosition();
    start(funcPos);
    
    // Skip the following two parses due to left-factoring in parseProgram()
    if (tAST == null)
      tAST = parseType();
    if (idAST == null)
      idAST = parseIdent();

    List fplAST = parseParaList();
    Stmt cAST = parseCompoundStmt();
    finish(funcPos);
    fAST = new FuncDecl(tAST, idAST, fplAST, cAST, funcPos);
    return fAST;
  }

  Decl parseGlobalVarDecl(Type tAST, Ident idAST) throws SyntaxError {
    // By default - global var is skipping type and ident parsing due to left-factoring in parseProgram()
    Decl globalVarAST = null;
    SourcePosition globalVarPos = new SourcePosition();
    start(globalVarPos);

    Expr eAST = null;
    Type arrAST = tAST;
    
    if (tAST == null)
      tAST = parseType();
    if (idAST == null)
      idAST = parseIdent();
    
    // Parse array type
    if(currentToken.kind == Token.LBRACKET){
      arrAST = parseArrayType(tAST, globalVarPos);
    }

    // Parse initialiser
    if(currentToken.kind == Token.EQ){
      match(Token.EQ);
      if(currentToken.kind == Token.LCURLY){ // expr { initExpr }
        eAST = parseInitExpr();
        finish(globalVarPos);
      } else { // just expr
        eAST = parseExpr();
        finish(globalVarPos);
      }
    } else {
      finish(globalVarPos);
      eAST = new EmptyExpr(dummyPos);
    }
    globalVarAST = new GlobalVarDecl(arrAST, idAST, eAST, globalVarPos);
    return globalVarAST;
  }

//  ======================== TYPES ==========================
  Type parseArrayType(Type tAST, SourcePosition arrPos) throws SyntaxError {
    Type arrAST = null;

    match(Token.LBRACKET);

    Expr dAST = null;
    IntLiteral intLiteralAST = null;
    SourcePosition dPos = new SourcePosition();
    start(dPos);

    if(currentToken.kind == Token.RBRACKET){ // []
      dAST = new EmptyExpr(dummyPos);
    } else { //[intliteral]
      intLiteralAST = parseIntLiteral();
      dAST = new IntExpr(intLiteralAST, dPos);
    }
    match(Token.RBRACKET);
    finish(dPos);
    arrAST = new ArrayType(tAST, dAST, arrPos);
    return arrAST;
  }

  Type parseType() throws SyntaxError {
    Type typeAST = null;

    SourcePosition typePos = new SourcePosition();
    start(typePos);

    switch(currentToken.kind){
      case Token.VOID:
        accept();
        finish(typePos);
        typeAST = new VoidType(typePos);
        break;
      case Token.BOOLEAN:
        accept();
        finish(typePos);
        typeAST = new BooleanType(typePos);
        break;
      case Token.INT:
        accept();
        finish(typePos);
        typeAST = new IntType(typePos);
        break;
      case Token.FLOAT:
        accept();
        finish(typePos);
        typeAST = new FloatType(typePos);
        break;
      default:
        syntacticError("\"%\" wrong result type for a function", currentToken.spelling);
        break;
    }

    return typeAST;
  }

// ======================= STATEMENTS ==============================
  Stmt parseCompoundStmt() throws SyntaxError {
    Stmt cAST = null; 

    SourcePosition stmtPos = new SourcePosition();
    start(stmtPos);

    List dlAST = new EmptyDeclList(dummyPos);
    List slAST = new EmptyStmtList(dummyPos);

    match(Token.LCURLY);
    
    // Insert code here to build a DeclList node for variable declarations
    if(currentToken.kind == Token.VOID ||
       currentToken.kind == Token.BOOLEAN ||
       currentToken.kind == Token.INT ||
       currentToken.kind == Token.FLOAT){
         Type tAST = parseType();
         dlAST = parseLocalVarDeclList(tAST);
       }

    slAST = parseStmtList();
    match(Token.RCURLY);
    finish(stmtPos);

    /* In the subset of the VC grammar, no variable declarations are
    * allowed. Therefore, a block is empty iff it has no statements.
    */
    if (dlAST instanceof EmptyDeclList && slAST instanceof EmptyStmtList) 
      cAST = new EmptyCompStmt(dummyPos);
    else
      cAST = new CompoundStmt(dlAST, slAST, stmtPos);
    return cAST;
  }

  List parseLocalVarDeclList(Type tAST) throws SyntaxError {
    List localVarList = null;
    SourcePosition localVarListPos = new SourcePosition();
    start(localVarListPos);

    boolean hasMoreThanOneVar = false;

    Ident idAST = null;
    Decl vAST = parseLocalVarDecl(tAST, idAST);

    if(currentToken.kind == Token.SEMICOLON){
      match(Token.SEMICOLON);
    } else if (currentToken.kind == Token.COMMA){
      match(Token.COMMA);
      hasMoreThanOneVar = true;
    }

    if(currentToken.kind == Token.VOID ||
       currentToken.kind == Token.BOOLEAN ||
       currentToken.kind == Token.INT ||
       currentToken.kind == Token.FLOAT) {
        tAST = parseType();
        localVarList = parseLocalVarDeclList(tAST);
        finish(localVarListPos);
        localVarList = new DeclList(vAST, localVarList, localVarListPos);
      }
      else if (currentToken.kind == Token.ID && hasMoreThanOneVar){
        localVarList = parseLocalVarDeclList(tAST);
        finish(localVarListPos);
        localVarList = new DeclList(vAST, localVarList, localVarListPos);
      }
      else if(vAST != null) {
        finish(localVarListPos);
        localVarList = new DeclList(vAST, new EmptyDeclList(dummyPos), localVarListPos);
      }
      if (vAST == null) {
        localVarList = new EmptyDeclList(dummyPos);
      }
    
      return localVarList;
  }

  Decl parseLocalVarDecl(Type tAST, Ident idAST) throws SyntaxError {
    // By default - local var is NOT skipping type and ident parsing (only used by compundStmt)
    Decl localVarAST = null;
    SourcePosition localVarPos = new SourcePosition();
    start(localVarPos);

    Expr eAST = null;
    Type arrAST = tAST;
    
    if (tAST == null)
      tAST = parseType();
    if (idAST == null)
      idAST = parseIdent();
    
    // Parse array type
    if(currentToken.kind == Token.LBRACKET){
      arrAST = parseArrayType(tAST, localVarPos);
    }

    // Parse initialiser
    if(currentToken.kind == Token.EQ){
      match(Token.EQ);
      if(currentToken.kind == Token.LCURLY){ // expr { initExpr }
        eAST = parseInitExpr();
        finish(localVarPos);
      } else { // just expr
        eAST = parseExpr();
        finish(localVarPos);
      }
    } else {
      finish(localVarPos);
      eAST = new EmptyExpr(dummyPos);
    }
    localVarAST = new LocalVarDecl(arrAST, idAST, eAST, localVarPos);
    return localVarAST;
  }

  List parseStmtList() throws SyntaxError {
    List slAST = null; 

    SourcePosition stmtPos = new SourcePosition();
    start(stmtPos);

    if (currentToken.kind != Token.RCURLY) {
      Stmt sAST = parseStmt();
      if (currentToken.kind != Token.RCURLY) {
        slAST = parseStmtList();
        finish(stmtPos);
        slAST = new StmtList(sAST, slAST, stmtPos);
      } else {
        finish(stmtPos);
        slAST = new StmtList(sAST, new EmptyStmtList(dummyPos), stmtPos);
      }
    }
    else
      slAST = new EmptyStmtList(dummyPos);
    
    return slAST;
  }

  Stmt parseStmt() throws SyntaxError {
    Stmt sAST = null;

    switch (currentToken.kind) {
      case Token.LCURLY:
        sAST = parseCompoundStmt();
        break;
      case Token.IF:
        sAST = parseIfStmt();
        break;
      case Token.FOR:
        sAST = parseForStmt();
        break;
      case Token.WHILE:
        sAST = parseWhileStmt();
        break;
      case Token.BREAK:
        sAST = parseBreakStmt();
        break;
      case Token.CONTINUE:
        sAST = parseContinueStmt();
        break;
      case Token.RETURN:
        sAST = parseReturnStmt();
        break;
      default:
        sAST = parseExprStmt();
        break;
    }
    
    return sAST;
  }

  Stmt parseIfStmt() throws SyntaxError {
    Stmt ifAST = null;
    Expr eAST = null;
    Stmt s1AST = null;
    Stmt s2AST = null;
    SourcePosition ifPos = new SourcePosition();
    start(ifPos);

    match(Token.IF);
    match(Token.LPAREN);
    eAST = parseExpr();
    match(Token.RPAREN);
    s1AST = parseStmt();
    if(currentToken.kind == Token.ELSE){
      match(Token.ELSE);
      s2AST = parseStmt();
      finish(ifPos);
      ifAST = new IfStmt(eAST, s1AST, s2AST, ifPos);
    }
    else {
      finish(ifPos);
      ifAST = new IfStmt(eAST, s1AST, ifPos);
    }
    return ifAST;
  }

  Stmt parseForStmt() throws SyntaxError {
    Stmt forAST = null;
    Stmt sAST = null;
    SourcePosition forPos = new SourcePosition();
    start(forPos);

    Expr e1AST = new EmptyExpr(dummyPos);
    Expr e2AST = new EmptyExpr(dummyPos);
    Expr e3AST = new EmptyExpr(dummyPos);

    match(Token.FOR);
    match(Token.LPAREN);

    if(currentToken.kind != Token.SEMICOLON)
      e1AST = parseExpr();

    match(Token.SEMICOLON);

    if(currentToken.kind != Token.SEMICOLON)
      e2AST = parseExpr();

    match(Token.SEMICOLON);

    if(currentToken.kind != Token.RPAREN)
      e3AST = parseExpr();

    match(Token.RPAREN);
    sAST = parseStmt();

    finish(forPos);
    forAST = new ForStmt(e1AST, e2AST, e3AST, sAST, forPos);
    return forAST;
  }

  Stmt parseWhileStmt() throws SyntaxError {
    Expr eAST = null;
    Stmt sAST = null;
    SourcePosition whilePos = new SourcePosition();
    start(whilePos);

    match(Token.WHILE);
    match(Token.LPAREN);
    eAST = parseExpr();
    match(Token.RPAREN);
    sAST = parseStmt();
    finish(whilePos);

    Stmt whileAST = new WhileStmt(eAST,sAST, whilePos);
    return whileAST;
  }

  Stmt parseBreakStmt() throws SyntaxError {
    Stmt breakAST = null;
    SourcePosition breakPos = new SourcePosition();
    start(breakPos);

    match(Token.BREAK);
    match(Token.SEMICOLON);
    finish(breakPos);

    breakAST = new BreakStmt(breakPos);
    return breakAST;
  }

  Stmt parseContinueStmt() throws SyntaxError {
    Stmt continueAST = null;
    SourcePosition continuePos = new SourcePosition();
    start(continuePos);

    match(Token.CONTINUE);
    match(Token.SEMICOLON);
    finish(continuePos);

    continueAST = new ContinueStmt(continuePos);
    return continueAST;
  }

  Stmt parseReturnStmt() throws SyntaxError {
    Stmt returnAST = null;
    Expr eAST = null;
    SourcePosition returnPos = new SourcePosition();
    start(returnPos);

    match(Token.RETURN);
    if (currentToken.kind == Token.SEMICOLON){
      eAST = new EmptyExpr(dummyPos);
      match(Token.SEMICOLON);
      finish(returnPos);
    } else {
      eAST = parseExpr();
      match(Token.SEMICOLON);
      finish(returnPos);
    }
    returnAST = new ReturnStmt(eAST, returnPos);
    return returnAST;
  }

  Stmt parseExprStmt() throws SyntaxError {
    Stmt sAST = null;

    SourcePosition stmtPos = new SourcePosition();
    start(stmtPos);

    if (currentToken.kind == Token.ID ||
        currentToken.kind == Token.PLUS ||
        currentToken.kind == Token.MINUS ||
        currentToken.kind == Token.NOT ||
        currentToken.kind == Token.LPAREN ||
        currentToken.kind == Token.INTLITERAL ||
        currentToken.kind == Token.FLOATLITERAL ||
        currentToken.kind == Token.BOOLEANLITERAL ||
        currentToken.kind == Token.STRINGLITERAL) {
      Expr eAST = parseExpr();
      match(Token.SEMICOLON);
      finish(stmtPos);
      sAST = new ExprStmt(eAST, stmtPos);
    } else {
      match(Token.SEMICOLON);
      finish(stmtPos);
      sAST = new ExprStmt(new EmptyExpr(dummyPos), stmtPos);
    }
    return sAST;
  }

// ======================= EXPRESSIONS ======================
  Expr parseInitExpr() throws SyntaxError {
    Expr initAST = null;
    SourcePosition initPos = new SourcePosition();
    start(initPos);

    List exprList = null;

    match(Token.LCURLY);
    exprList = parseExprList();
    finish(initPos);
    initAST = new InitExpr(exprList, initPos);
    match(Token.RCURLY);
    return initAST;
  }

  List parseExprList() throws SyntaxError {
    List exprList = null;
    SourcePosition exprListPos = new SourcePosition();
    start(exprListPos);

    Expr eAST = parseExpr();
    if(currentToken.kind == Token.COMMA){
      match(Token.COMMA);
      finish(exprListPos);
      exprList = new ExprList(eAST, parseExprList(), exprListPos);
    } else {
      finish(exprListPos);
      exprList = new ExprList(eAST, new EmptyExprList(dummyPos), exprListPos);
    }

    return exprList;
  }

  Expr parseExpr() throws SyntaxError {
    Expr exprAST = null;
    exprAST = parseAssignExpr();
    return exprAST;
  }

  Expr parseAssignExpr() throws SyntaxError {
    Expr assignAST = null;
    SourcePosition assignPos = new SourcePosition();
    start(assignPos);

    Expr e2AST = null;

    assignAST = parseCondOrExpr();
    if(currentToken.kind == Token.EQ){
      acceptOperator();
      finish(assignPos);
      assignAST = new AssignExpr(assignAST, parseAssignExpr(), assignPos);
    }
    return assignAST;
  }

  Expr parseCondOrExpr() throws SyntaxError {
    Expr condOrAST = null;
    SourcePosition condOrPos = new SourcePosition();
    start(condOrPos);
    
    Operator oAST = null;
    Expr e2AST = null;

    condOrAST = parseCondAndExpr();
    while(currentToken.kind == Token.OROR){
      oAST = acceptOperator();
      e2AST = parseCondAndExpr();

      SourcePosition condAndPos = new SourcePosition();
      copyStart(condOrPos, condAndPos);
      finish(condAndPos);
      condOrAST = new BinaryExpr(condOrAST, oAST, e2AST, condAndPos);
    }
    return condOrAST;
  }

  Expr parseCondAndExpr() throws SyntaxError {
    Expr condAndAST = null;
    SourcePosition condAndPos = new SourcePosition();
    start(condAndPos);

    Operator oAST = null;
    Expr e2AST = null;

    condAndAST = parseEqualityExpr();
    while(currentToken.kind == Token.ANDAND){
      oAST = acceptOperator();
      e2AST = parseEqualityExpr();

      SourcePosition eqPos = new SourcePosition();
      copyStart(condAndPos, eqPos);
      finish(eqPos);
      condAndAST = new BinaryExpr(condAndAST, oAST, e2AST, eqPos);
    }
    return condAndAST;
  }

  Expr parseEqualityExpr() throws SyntaxError {
    Expr eqAST = null;
    SourcePosition eqPos = new SourcePosition();
    start(eqPos);

    Operator oAST = null;
    Expr e2AST = null;

    eqAST = parseRelExpr();
    while (currentToken.kind == Token.EQEQ || currentToken.kind == Token.NOTEQ){
      oAST = acceptOperator();
      e2AST = parseRelExpr();

      SourcePosition relPos = new SourcePosition();
      copyStart(eqPos, relPos);
      finish(relPos);
      eqAST = new BinaryExpr(eqAST, oAST, e2AST, relPos);
    }
    return eqAST;
  }

  Expr parseRelExpr() throws SyntaxError {
    Expr relAST = null;
    SourcePosition relPos = new SourcePosition();
    start(relPos);

    Operator oAST = null;
    Expr e2AST = null;

    relAST = parseAdditiveExpr();
    while(currentToken.kind == Token.LT || currentToken.kind == Token.LTEQ 
    || currentToken.kind == Token.GT || currentToken.kind == Token.GTEQ){
      oAST = acceptOperator();
      e2AST = parseAdditiveExpr();

      SourcePosition additivePos = new SourcePosition();
      copyStart(relPos, additivePos);
      finish(additivePos);
      relAST = new BinaryExpr(relAST, oAST, e2AST, additivePos);
    }
    return relAST;
  }

  Expr parseAdditiveExpr() throws SyntaxError {
    Expr exprAST = null;

    SourcePosition addStartPos = new SourcePosition();
    start(addStartPos);

    exprAST = parseMultiplicativeExpr();
    while (currentToken.kind == Token.PLUS
           || currentToken.kind == Token.MINUS) {
      Operator opAST = acceptOperator();
      Expr e2AST = parseMultiplicativeExpr();

      SourcePosition addPos = new SourcePosition();
      copyStart(addStartPos, addPos);
      finish(addPos);
      exprAST = new BinaryExpr(exprAST, opAST, e2AST, addPos);
    }
    return exprAST;
  }

  Expr parseMultiplicativeExpr() throws SyntaxError {

    Expr exprAST = null;

    SourcePosition multStartPos = new SourcePosition();
    start(multStartPos);

    exprAST = parseUnaryExpr();
    while (currentToken.kind == Token.MULT
           || currentToken.kind == Token.DIV) {
      Operator opAST = acceptOperator();
      Expr e2AST = parseUnaryExpr();
      SourcePosition multPos = new SourcePosition();
      copyStart(multStartPos, multPos);
      finish(multPos);
      exprAST = new BinaryExpr(exprAST, opAST, e2AST, multPos);
    }
    return exprAST;
  }

  Expr parseUnaryExpr() throws SyntaxError {

    Expr exprAST = null;

    SourcePosition unaryPos = new SourcePosition();
    start(unaryPos);

    switch (currentToken.kind) {
      case Token.PLUS:
      case Token.MINUS:
      case Token.NOT:
        Operator opAST = acceptOperator();
        Expr e2AST = parseUnaryExpr();
        finish(unaryPos);
        exprAST = new UnaryExpr(opAST, e2AST, unaryPos);
        break;

      default:
        exprAST = parsePrimaryExpr();
        break;
    }
    return exprAST;
  }

  Expr parsePrimaryExpr() throws SyntaxError {

    Expr exprAST = null;

    SourcePosition primPos = new SourcePosition();
    start(primPos);

    switch (currentToken.kind) {
      case Token.ID:
        Ident idAST = parseIdent();
        Var simpVarAST = new SimpleVar(idAST, primPos);
        switch(currentToken.kind) {
          case Token.LPAREN:
            List aplAST = parseArgList();
            finish(primPos);
            exprAST = new CallExpr(idAST, aplAST, primPos);
            break;
          case Token.LBRACKET:
            match(Token.LBRACKET);
            Expr indexAST = parseExpr();
            match(Token.RBRACKET);
            finish(primPos);
            exprAST = new ArrayExpr(simpVarAST, indexAST, primPos);
            break;
          default: // arg-list is optional
            exprAST = new VarExpr(simpVarAST, primPos);
            break;
        }
        break;

      case Token.LPAREN:
        accept();
        exprAST = parseExpr();
        match(Token.RPAREN);
        break;

      case Token.INTLITERAL:
        IntLiteral ilAST = parseIntLiteral();
        finish(primPos);
        exprAST = new IntExpr(ilAST, primPos);
        break;
      
      case Token.FLOATLITERAL:
        FloatLiteral flAST = parseFloatLiteral();
        finish(primPos);
        exprAST = new FloatExpr(flAST, primPos);
        break;

      case Token.BOOLEANLITERAL:
        BooleanLiteral blAST = parseBooleanLiteral();
        finish(primPos);
        exprAST = new BooleanExpr(blAST, primPos);
        break;

      case Token.STRINGLITERAL:
        StringLiteral slAST = parseStringLiteral();
        finish(primPos);
        exprAST = new StringExpr(slAST, primPos);
        break;

      default:
        syntacticError("illegal primary expression", currentToken.spelling);
        break;
    }
    return exprAST;
  }

// ========================== ID, OPERATOR and LITERALS ========================

  Ident parseIdent() throws SyntaxError {

    Ident I = null; 

    if (currentToken.kind == Token.ID) {
      previousTokenPosition = currentToken.position;
      String spelling = currentToken.spelling;
      I = new Ident(spelling, previousTokenPosition);
      currentToken = scanner.getToken();
    } else 
      syntacticError("identifier expected here", "");
    return I;
  }

// acceptOperator parses an operator, and constructs a leaf AST for it

  Operator acceptOperator() throws SyntaxError {
    Operator O = null;

    previousTokenPosition = currentToken.position;
    String spelling = currentToken.spelling;
    O = new Operator(spelling, previousTokenPosition);
    currentToken = scanner.getToken();
    return O;
  }


  IntLiteral parseIntLiteral() throws SyntaxError {
    IntLiteral IL = null;

    if (currentToken.kind == Token.INTLITERAL) {
      String spelling = currentToken.spelling;
      accept();
      IL = new IntLiteral(spelling, previousTokenPosition);
    } else 
      syntacticError("integer literal expected here", "");
    return IL;
  }

  FloatLiteral parseFloatLiteral() throws SyntaxError {
    FloatLiteral FL = null;

    if (currentToken.kind == Token.FLOATLITERAL) {
      String spelling = currentToken.spelling;
      accept();
      FL = new FloatLiteral(spelling, previousTokenPosition);
    } else 
      syntacticError("float literal expected here", "");
    return FL;
  }

  BooleanLiteral parseBooleanLiteral() throws SyntaxError {
    BooleanLiteral BL = null;

    if (currentToken.kind == Token.BOOLEANLITERAL) {
      String spelling = currentToken.spelling;
      accept();
      BL = new BooleanLiteral(spelling, previousTokenPosition);
    } else 
      syntacticError("boolean literal expected here", "");
    return BL;
  }

  StringLiteral parseStringLiteral() throws SyntaxError {
    StringLiteral SL = null;

    if (currentToken.kind == Token.STRINGLITERAL) {
      String spelling = currentToken.spelling;
      accept();
      SL = new StringLiteral(spelling, previousTokenPosition);
    } else
      syntacticError("string literal expected here", "");
    return SL;
  }

  
// ========================= PARAMETERS =======================
  List parseParaList() throws SyntaxError {
    List plList = null;
    SourcePosition plPos = new SourcePosition();
    start(plPos);

    match(Token.LPAREN);
    if(currentToken.kind == Token.RPAREN){
      match(Token.RPAREN);
      finish(plPos);
      plList = new EmptyParaList(dummyPos);
    } else {
      finish(plPos);
      plList = parseProperParaList();
      match(Token.RPAREN);
    }
    return plList;
  }

  List parseProperParaList() throws SyntaxError {
    List plList = null;
    SourcePosition plPos = new SourcePosition();
    start(plPos);

    ParaDecl pAST = null;

    if(currentToken.kind != Token.RPAREN){
      pAST = parseParaDecl();
      if(currentToken.kind == Token.COMMA){ // More than one para
        match(Token.COMMA);
        finish(plPos);
        plList = new ParaList(pAST, parseProperParaList(), plPos);
      } else {
        finish(plPos);
        plList = new ParaList(pAST, new EmptyParaList(dummyPos), plPos);
      }

    } else {// Empty para list
      finish(plPos);
      plList = new EmptyParaList(dummyPos);
    }
    return plList;
  }

  ParaDecl parseParaDecl() throws SyntaxError {
    ParaDecl pAST = null;
    SourcePosition pPos = new SourcePosition();
    start(pPos);

    Type tAST = null;
    Ident idAST = null;

    tAST = parseType();
    idAST = parseIdent();
    if(currentToken.kind == Token.LBRACKET){
      tAST = parseArrayType(tAST, pPos);
    }

    finish(pPos);
    pAST = new ParaDecl(tAST, idAST, pPos);
    return pAST;
  }

  List parseArgList() throws SyntaxError {
    List alList = null;
    SourcePosition alPos = new SourcePosition();
    start(alPos);

    match(Token.LPAREN);
    if (currentToken.kind == Token.RPAREN){
      match(Token.RPAREN);
      finish(alPos);
      alList = new EmptyArgList(dummyPos);
    } else {
      finish(alPos);
      alList = parseProperArgList();
      match(Token.RPAREN);
    }
    return alList;
  }

  List parseProperArgList() throws SyntaxError {
    List alList = null;
    SourcePosition alPos = new SourcePosition();
    start(alPos);

    Arg aAST = null;
    aAST = parseArg();

    if(currentToken.kind == Token.COMMA){
      match(Token.COMMA);
      finish(alPos);
      alList = new ArgList(aAST, parseProperArgList(), alPos);
    } else {
      finish(alPos);
      alList = new ArgList(aAST, new EmptyArgList(dummyPos), alPos);
    }
    return alList;
  }

  Arg parseArg() throws SyntaxError {
    Arg argAST = null;
    SourcePosition argPos = new SourcePosition();
    start(argPos);

    Expr eAST = parseExpr();
    argAST = new Arg(eAST, argPos);

    return argAST;
  }

}

