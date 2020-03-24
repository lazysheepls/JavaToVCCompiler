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

    try {
      List dlAST = parseFuncDeclList();
      finish(programPos);
      programAST = new Program(dlAST, programPos); 
      if (currentToken.kind != Token.EOF) {
        syntacticError("\"%\" unknown type", currentToken.spelling);
      }
    }
    catch (SyntaxError s) { return null; }
    return programAST;
  }

// ========================== DECLARATIONS ========================

  List parseFuncDeclList() throws SyntaxError {
    List dlAST = null;
    Decl dAST = null;

    SourcePosition funcPos = new SourcePosition();
    start(funcPos);

    dAST = parseFuncDecl();
    
    if (currentToken.kind == Token.VOID) {
      dlAST = parseFuncDeclList();
      finish(funcPos);
      dlAST = new DeclList(dAST, dlAST, funcPos);
    } else if (dAST != null) {
      finish(funcPos);
      dlAST = new DeclList(dAST, new EmptyDeclList(dummyPos), funcPos);
    }
    if (dlAST == null) 
      dlAST = new EmptyDeclList(dummyPos);

    return dlAST;
  }

  Decl parseFuncDecl() throws SyntaxError {

    Decl fAST = null; 
    
    SourcePosition funcPos = new SourcePosition();
    start(funcPos);

    Type tAST = parseType();
    Ident iAST = parseIdent();
    List fplAST = parseParaList();
    Stmt cAST = parseCompoundStmt();
    finish(funcPos);
    fAST = new FuncDecl(tAST, iAST, fplAST, cAST, funcPos);
    return fAST;
  }

  //TODO: Not finished
  Decl parseGlobalVarDecl(boolean skipParseIdent) throws SyntaxError {
    // Skip the following parse dut to left-factoring in parseProgram()
    Decl globalVarAST = null;
    SourcePosition globalVarPos = new SourcePosition();
    start(globalVarPos);

    Type tAST = null;
    Ident idAST = null;
    Expr eAST = null;

    if(!skipParseIdent)
      parseType();
    // parseInitDeclaratorList(skipParseIdent);
    match(Token.SEMICOLON);
    return globalVarAST;
  }

  //TODO: Have not start yet (return type could be wrong)
  Ident parseDeclarator(boolean skipParseIdent) throws SyntaxError {
    Ident dAST = null;
    if(!skipParseIdent)
      parseIdent();
    if(currentToken.kind == Token.LBRACKET){
      match(Token.LBRACKET);
      if(currentToken.kind == Token.INTLITERAL){
        parseIntLiteral();
      }
      match(Token.RBRACKET);
    }
    return dAST;
  }

//  ======================== TYPES ==========================
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

    match(Token.LCURLY);

    // Insert code here to build a DeclList node for variable declarations
    List dlAST = parseVarDeclList();
    List slAST = parseStmtList();
    match(Token.RCURLY);
    finish(stmtPos);

    /* In the subset of the VC grammar, no variable declarations are
     * allowed. Therefore, a block is empty iff it has no statements.
     */
    if (dlAST instanceof EmptyDeclList && slAST instanceof EmptyStmtList) 
      cAST = new EmptyCompStmt(stmtPos);
    else
      cAST = new CompoundStmt(dlAST, slAST, stmtPos);
    return cAST;
  }

  //TODO: Not finished
  List parseVarDeclList() throws SyntaxError { // var-decl*
    List dlAST = null;

    // while(currentToken.kind == Token.VOID ||
    // currentToken.kind == Token.BOOLEAN ||
    // currentToken.kind == Token.INT ||
    // currentToken.kind == Token.FLOAT) // use first of var-decl
    //   // parseVarDecl(false);
    
    return dlAST;
  }


  List parseStmtList() throws SyntaxError {
    List slAST = null; 

    SourcePosition stmtPos = new SourcePosition();
    start(stmtPos);

    if (currentToken.kind != Token.RCURLY) {
      Stmt sAST = parseStmt();
      {
        if (currentToken.kind != Token.RCURLY) {
          slAST = parseStmtList();
          finish(stmtPos);
          slAST = new StmtList(sAST, slAST, stmtPos);
        } else {
          finish(stmtPos);
          slAST = new StmtList(sAST, new EmptyStmtList(dummyPos), stmtPos);
        }
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

    Expr e1AST = new EmptyExpr(forPos);
    Expr e2AST = new EmptyExpr(forPos);
    Expr e3AST = new EmptyExpr(forPos);

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
      eAST = new EmptyExpr(returnPos);
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

    if (currentToken.kind == Token.ID
        || currentToken.kind == Token.INTLITERAL
        || currentToken.kind == Token.LPAREN) {
        Expr eAST = parseExpr();
        match(Token.SEMICOLON);
        finish(stmtPos);
        sAST = new ExprStmt(eAST, stmtPos);
    } else {
      Expr eAST = new EmptyExpr(stmtPos);
      match(Token.SEMICOLON);
      finish(stmtPos);
      sAST = new ExprStmt(new EmptyExpr(dummyPos), stmtPos);
    }
    return sAST;
  }

// ======================= EXPRESSIONS ======================
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
    while(currentToken.kind == Token.EQ){
      acceptOperator();
      e2AST = parseCondOrExpr();

      SourcePosition condOrPos = new SourcePosition();
      copyStart(assignPos, condOrPos);
      finish(condOrPos);
      assignAST = new AssignExpr(assignAST, e2AST, condOrPos);
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
      plList = new EmptyParaList(plPos);
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

    plList = new ParaList(parseParaDecl(), new EmptyParaList(plPos), plPos);
    while(currentToken.kind == Token.COMMA){
      match(Token.COMMA);
      pAST = parseParaDecl();

      SourcePosition pPos = new SourcePosition();
      copyStart(plPos, pPos);
      finish(pPos);
      plList = new ParaList(pAST, plList, pPos);
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
    if(currentToken.kind == Token.ID){
      idAST = parseDeclarator(false); // do not skip parse identifier //TODO: This means local decl?
    } else {
      syntacticError("Declarator expected here", "");
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
      alList = new EmptyArgList(alPos);
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

    alList = new ArgList(parseArg(), new EmptyArgList(alPos), alPos);
    while(currentToken.kind == Token.COMMA){
      match(Token.COMMA);
      aAST = parseArg();

      SourcePosition aPos = new SourcePosition();
      copyStart(alPos, aPos);
      finish(aPos);
      alList = new ArgList(aAST, alList, aPos);
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

