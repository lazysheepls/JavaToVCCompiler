/***
 * *
 * * Recogniser.java            
 * *
 ***/

/* At this stage, this parser accepts a subset of VC defined	by
 * the following grammar. 
 *
 * You need to modify the supplied parsing methods (if necessary) and 
 * add the missing ones to obtain a parser for the VC language.
 *
 * (27---Feb---2019)

program       -> func-decl

// declaration

func-decl     -> void identifier "(" ")" compound-stmt

identifier    -> ID

// statements 
compound-stmt -> "{" stmt* "}" 
stmt          -> continue-stmt
    	      |  expr-stmt
continue-stmt -> continue ";"
expr-stmt     -> expr? ";"

// expressions 
expr                -> assignment-expr
assignment-expr     -> additive-expr
additive-expr       -> multiplicative-expr
                    |  additive-expr "+" multiplicative-expr
multiplicative-expr -> unary-expr
	            |  multiplicative-expr "*" unary-expr
unary-expr          -> "-" unary-expr
		    |  primary-expr

primary-expr        -> identifier
 		    |  INTLITERAL
		    | "(" expr ")"
*/

package VC.Recogniser;

import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import VC.ErrorReporter;

public class Recogniser {

  private Scanner scanner;
  private ErrorReporter errorReporter;
  private Token currentToken;

  public Recogniser (Scanner lexer, ErrorReporter reporter) {
    scanner = lexer;
    errorReporter = reporter;

    currentToken = scanner.getToken();
  }

// match checks to see f the current token matches tokenExpected.
// If so, fetches the next token.
// If not, reports a syntactic error.

  void match(int tokenExpected) throws SyntaxError {
    if (currentToken.kind == tokenExpected) {
      currentToken = scanner.getToken();
    } else {
      syntacticError("\"%\" expected here", Token.spell(tokenExpected));
    }
  }

 // accepts the current token and fetches the next
  void accept() {
    currentToken = scanner.getToken();
  }

  void syntacticError(String messageTemplate, String tokenQuoted) throws SyntaxError {
    SourcePosition pos = currentToken.position;
    errorReporter.reportError(messageTemplate, tokenQuoted, pos);
    throw(new SyntaxError());
  }


// ========================== PROGRAMS ========================
  public void parseProgram() {
    try{
      while(currentToken.kind != Token.EOF){
        parseType();
        parseIdent();
        if (currentToken.kind == Token.LPAREN){
          parseFuncDecl();
        }
        else {
          parseVarDecl(true);
        }
      }
    } 
    catch (SyntaxError s) { }
  }

// ========================== DECLARATIONS ========================

  void parseFuncDecl() throws SyntaxError {
    // Skip the following two parses due to left-factoring in parseProgram()
    // parseType();
    // parseIdent();
    parseParaList();
    parseCompoundStmt();
  }
  
  void parseVarDecl(boolean skipParseIdent) throws SyntaxError {
    // Skip the following parse dut to left-factoring in parseProgram()
    if(!skipParseIdent)
      parseType();
    parseInitDeclaratorList(skipParseIdent);
    match(Token.SEMICOLON);
  }

  void parseInitDeclaratorList(boolean skipParseIdent) throws SyntaxError {
    // set to true to skip parse identifier due to left-factoring in parseProgram()
    parseInitDeclarator(skipParseIdent);
    while(currentToken.kind == Token.COMMA){
      match(Token.COMMA);
      parseInitDeclarator(false);
    }
  }

  void parseInitDeclarator(boolean skipParseIdent) throws SyntaxError {
    parseDeclarator(skipParseIdent);
    if(currentToken.kind == Token.EQ){
      acceptOperator();
      parseInitialiser();
    }
  }

  void parseDeclarator(boolean skipParseIdent) throws SyntaxError {
    if(!skipParseIdent)
      parseIdent();
    if(currentToken.kind == Token.LBRACKET){
      match(Token.LBRACKET);
      if(currentToken.kind == Token.INTLITERAL){
        parseIntLiteral();
      }
      match(Token.RBRACKET);
    }
  }

  void parseInitialiser() throws SyntaxError {
    if (currentToken.kind != Token.LCURLY){
      parseExpr();
    } else {
      match(Token.LCURLY);
      parseExpr();
      while(currentToken.kind == Token.COMMA){
        match(Token.COMMA);
        parseExpr();
      }
      match(Token.RCURLY);
    }
  }
// ======================= PRIMITIVE TYPES =========================

void parseType() throws SyntaxError {
  switch(currentToken.kind){
    case Token.VOID:
    case Token.BOOLEAN:
    case Token.INT:
    case Token.FLOAT:
      accept();
      break;
    default:
      syntacticError("\"%\" wrong result type for a function", currentToken.spelling);
      break;
  }
}

// ======================= STATEMENTS ==============================
  void parseCompoundStmt() throws SyntaxError {
    match(Token.LCURLY);
    parseVarDeclList(); // var-decl*
    parseStmtList(); // stmt*
    match(Token.RCURLY);
  }

  void parseVarDeclList() throws SyntaxError { // var-decl*
    while(currentToken.kind == Token.VOID ||
    currentToken.kind == Token.BOOLEAN ||
    currentToken.kind == Token.INT ||
    currentToken.kind == Token.FLOAT) // use first of var-decl
      parseVarDecl(false);
  }

 // Here, a new nontermial has been introduced to define { stmt } *
  void parseStmtList() throws SyntaxError {
    while (currentToken.kind != Token.RCURLY) // use follow of stmt
      parseStmt();
  }

  void parseStmt() throws SyntaxError {

    switch (currentToken.kind) {
      case Token.LCURLY:
        parseCompoundStmt();
        break;
      case Token.IF:
        parseIfStmt();
        break;
      case Token.FOR:
        parseForStmt();
        break;
      case Token.WHILE:
        parseWhileStmt();
        break;
      case Token.BREAK:
        parseBreakStmt();
        break;
      case Token.CONTINUE:
        parseContinueStmt();
        break;
      case Token.RETURN:
        parseReturnStmt();
        break;
      default:
        parseExprStmt();
        break;
    }
  }

  void parseIfStmt() throws SyntaxError {
    match(Token.IF);
    match(Token.LPAREN);
    parseExpr();
    match(Token.RPAREN);
    parseStmt();
    if(currentToken.kind == Token.ELSE){
      match(Token.ELSE);
      parseStmt();
    }
  }

  void parseForStmt() throws SyntaxError {
    match(Token.FOR);
    match(Token.LPAREN);
    parseWhateverInsideForStmt();
    match(Token.RPAREN);
    parseStmt();
  }

  //CustomFunc: Used for parseForStmt()
  void parseWhateverInsideForStmt() throws SyntaxError {
    if(currentToken.kind != Token.SEMICOLON)
      parseExpr();

    match(Token.SEMICOLON);

    if(currentToken.kind != Token.SEMICOLON)
      parseExpr();

    match(Token.SEMICOLON);

    if(currentToken.kind != Token.RPAREN)
      parseExpr();
  }

  void parseWhileStmt() throws SyntaxError {
    match(Token.WHILE);
    match(Token.LPAREN);
    parseExpr();
    match(Token.RPAREN);
    parseStmt();
  }

  void parseBreakStmt() throws SyntaxError {
    match(Token.BREAK);
    match(Token.SEMICOLON);
  }

  void parseContinueStmt() throws SyntaxError {
    match(Token.CONTINUE);
    match(Token.SEMICOLON);
  }

  void parseReturnStmt() throws SyntaxError {
    match(Token.RETURN);
    if (currentToken.kind == Token.SEMICOLON){
      match(Token.SEMICOLON);
    } else {
      parseExpr();
      match(Token.SEMICOLON);
    }
  }

  void parseExprStmt() throws SyntaxError {
    if (currentToken.kind == Token.SEMICOLON){
      match(Token.SEMICOLON);
    } else {
      parseExpr();
      match(Token.SEMICOLON);
    }
  }


// ======================= IDENTIFIERS ======================

 // Call parseIdent rather than match(Token.ID). 
 // In Assignment 3, an Identifier node will be constructed in here.


  void parseIdent() throws SyntaxError {

    if (currentToken.kind == Token.ID) {
      currentToken = scanner.getToken();
    } else 
      syntacticError("identifier expected here", "");
  }

// ======================== OPERATORS =======================

 // Call acceptOperator rather than accept(). 
 // In Assignment 3, an Operator Node will be constructed in here.

  void acceptOperator() throws SyntaxError {

    currentToken = scanner.getToken();
  }


// ======================= EXPRESSIONS ======================

  void parseExpr() throws SyntaxError {
    parseAssignExpr();
  }


  void parseAssignExpr() throws SyntaxError {
    parseCondOrExpr();
    while(currentToken.kind == Token.EQ){
      acceptOperator();
      parseCondOrExpr();
    }
  }

  void parseCondOrExpr() throws SyntaxError {
    parseCondAndExpr();
    while(currentToken.kind == Token.OROR){
      acceptOperator();
      parseCondAndExpr();
    }
  }

  void parseCondAndExpr() throws SyntaxError {
    parseEqualityExpr();
    while(currentToken.kind == Token.ANDAND){
      acceptOperator();
      parseEqualityExpr();
    }
  }

  void parseEqualityExpr() throws SyntaxError {
    parseRelExpr();
    while (currentToken.kind == Token.EQEQ || currentToken.kind == Token.NOTEQ){
      acceptOperator();
      parseRelExpr();
    }
  }

  void parseRelExpr() throws SyntaxError {
    parseAdditiveExpr();
    while(currentToken.kind == Token.LT || currentToken.kind == Token.LTEQ 
    || currentToken.kind == Token.GT || currentToken.kind == Token.GTEQ){
      acceptOperator();
      parseAdditiveExpr();
    }
  }

  void parseAdditiveExpr() throws SyntaxError {
    parseMultiplicativeExpr();
    while (currentToken.kind == Token.PLUS || currentToken.kind == Token.MINUS ) {
      acceptOperator();
      parseMultiplicativeExpr();
    }
  }

  void parseMultiplicativeExpr() throws SyntaxError {
    parseUnaryExpr();
    while (currentToken.kind == Token.MULT || currentToken.kind == Token.DIV ) {
      acceptOperator();
      parseUnaryExpr();
    }
  }

  void parseUnaryExpr() throws SyntaxError {

    switch (currentToken.kind) {
      case Token.PLUS:
      case Token.MINUS:
      case Token.NOT:
        acceptOperator();
        parseUnaryExpr();
        break;

      default:
        parsePrimaryExpr();
        break;
    }
  }

  void parsePrimaryExpr() throws SyntaxError {

    switch (currentToken.kind) {

      case Token.ID:
        parseIdent();
        switch(currentToken.kind) {
          case Token.LPAREN:
            parseArgList();
            break;
          case Token.LBRACKET:
            match(Token.LBRACKET);
            parseExpr();
            match(Token.RBRACKET);
            break;
          default: // arg-list is optional
            break;
        }
        break;

      case Token.LPAREN:
        match(Token.LPAREN);
        parseExpr();
        match(Token.RPAREN);
        break;

      case Token.INTLITERAL:
        parseIntLiteral();
        break;

      case Token.FLOATLITERAL:
        parseFloatLiteral();
        break;

      case Token.BOOLEANLITERAL:
        parseBooleanLiteral();
        break;

      case Token.STRINGLITERAL:
        parseStringLiteral();
        break;
        
      default:
        syntacticError("\"%\" is an illegal primary expression", currentToken.spelling);
        break;
    }
  }

// ========================== LITERALS ========================

  // Call these methods rather than accept().  In Assignment 3, 
  // literal AST nodes will be constructed inside these methods. 

  void parseIntLiteral() throws SyntaxError {

    if (currentToken.kind == Token.INTLITERAL) {
      currentToken = scanner.getToken();
    } else 
      syntacticError("integer literal expected here", "");
  }

  void parseFloatLiteral() throws SyntaxError {

    if (currentToken.kind == Token.FLOATLITERAL) {
      currentToken = scanner.getToken();
    } else 
      syntacticError("float literal expected here", "");
  }

  void parseBooleanLiteral() throws SyntaxError {

    if (currentToken.kind == Token.BOOLEANLITERAL) {
      currentToken = scanner.getToken();
    } else 
      syntacticError("boolean literal expected here", "");
  }

  void parseStringLiteral() throws SyntaxError {
    if (currentToken.kind == Token.STRINGLITERAL) {
      currentToken = scanner.getToken();
    } else
      syntacticError("string literal expected here", "");
  }

// ========================= PARAMETERS =======================
  void parseParaList() throws SyntaxError {
    match(Token.LPAREN);
    if(currentToken.kind == Token.RPAREN){
      match(Token.RPAREN);
    } else {
      parseProperParaList();
      match(Token.RPAREN);
    }
  }
  void parseProperParaList() throws SyntaxError {
    parseParaDecl();
    while(currentToken.kind == Token.COMMA){
      match(Token.COMMA);
      parseParaDecl();
    }
  }

  void parseParaDecl() throws SyntaxError {
    parseType();
    if(currentToken.kind == Token.ID){
      parseDeclarator(false); // do not skip parse identifier
    } else {
      syntacticError("Declarator expected here", "");
    }
  }

  void parseArgList() throws SyntaxError {
    match(Token.LPAREN);
    if (currentToken.kind == Token.RPAREN){
      match(Token.RPAREN);
    } else {
      parseProperArgList();
      match(Token.RPAREN);
    }
  }

  void parseProperArgList() throws SyntaxError {
    parseArg();
    while(currentToken.kind == Token.COMMA){
      match(Token.COMMA);
      parseArg();
    }
  }

  void parseArg() throws SyntaxError {
    parseExpr();
  }
}
