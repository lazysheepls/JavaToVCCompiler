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
import sun.tools.java.SyntaxError;
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

    try {
      parseFuncDecl();
      if (currentToken.kind != Token.EOF) {
        syntacticError("\"%\" wrong result type for a function", currentToken.spelling);
      }
    }
    catch (SyntaxError s) {  }
  }

// ========================== DECLARATIONS ========================

  void parseFuncDecl() throws SyntaxError {

    match(Token.VOID);
    parseIdent();
    match(Token.LPAREN);
    match(Token.RPAREN);
    parseCompoundStmt();
  }

// ======================= STATEMENTS ==============================


  void parseCompoundStmt() throws SyntaxError {

    match(Token.LCURLY);
    parseStmtList();
    match(Token.RCURLY);
  }

 // Here, a new nontermial has been introduced to define { stmt } *
  void parseStmtList() throws SyntaxError {

    while (currentToken.kind != Token.RCURLY) 
      parseStmt();
  }

  void parseStmt() throws SyntaxError {

    switch (currentToken.kind) {

    case Token.CONTINUE:
      parseContinueStmt();
      break;

    default:
      parseExprStmt();
      break;

    }
  }

  void parseContinueStmt() throws SyntaxError {

    match(Token.CONTINUE);
    match(Token.SEMICOLON);

  }

  void parseExprStmt() throws SyntaxError {

    if (currentToken.kind == Token.ID
        || currentToken.kind == Token.INTLITERAL
        || currentToken.kind == Token.MINUS
        || currentToken.kind == Token.LPAREN) {
        parseExpr();
        match(Token.SEMICOLON);
    } else {
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

// ======================= OPERATORS ======================

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

    parseAdditiveExpr();

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
            accept();
            parseExpr();
            match(Token.RBRACKET);
            break;
        }
        break;

      case Token.LPAREN:
        accept();
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
        syntacticError("illegal parimary expression", currentToken.spelling);
       
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

  // ========================== PARAMETERS ========================
  void parseArgList() throws SyntaxError {
    if (currentToken.kind == Token.LPAREN){
      accept();
      parseProperArgList();
      match(Token.RPAREN);
    } else {
      syntacticError("arg-list expected here", "");
    }
  }

  void parseProperArgList() throws SyntaxError {
    parseArg();
    while(currentToken.kind == Token.COMMA){
      accept();
      parseArg();
    }
  }

  void parseArg() throws SyntaxError {
    parseExpr();
  }
}
