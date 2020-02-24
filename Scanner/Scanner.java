/*+*
 ***
 *+*	Scanner.java                        
 ***
 *+*/

package VC.Scanner;

import VC.ErrorReporter;

public final class Scanner { 

  private SourceFile sourceFile;
  private boolean debug;

  private ErrorReporter errorReporter;
  private StringBuffer currentSpelling;
  private char currentChar;
  private SourcePosition sourcePos;

// =========================================================

  public Scanner(SourceFile source, ErrorReporter reporter) {
    sourceFile = source;
    errorReporter = reporter;
    currentChar = sourceFile.getNextChar();
    debug = false;

    // you may initialise your counters for line and column numbers here
  }

  public void enableDebugging() {
    debug = true;
  }

  // accept gets the next character from the source program.

  private void accept() {

    currentChar = sourceFile.getNextChar();

  // you may save the lexeme of the current token incrementally here
  // you may also increment your line and column counters here
  }

  // inspectChar returns the n-th character after currentChar
  // in the input stream. 
  //
  // If there are fewer than nthChar characters between currentChar 
  // and the end of file marker, SourceFile.eof is returned.
  // 
  // Both currentChar and the current position in the input stream
  // are *not* changed. Therefore, a subsequent call to accept()
  // will always return the next char after currentChar.

  private char inspectChar(int nthChar) {
    return sourceFile.inspectChar(nthChar);
  }

  private int nextToken() {
  // Tokens: separators, operators, literals, identifiers and keyworods
       
    switch (currentChar) {
       // separators 
    case '(':
	accept();
	return Token.LPAREN;
    case '.':
        //  attempting to recognise a float

    case '|':	
       	accept();
      	if (currentChar == '|') {
           accept();
	   return Token.OROR;
      	} else {
	   return Token.ERROR;
        }

    // ....
    case SourceFile.eof:	
	currentSpelling.append(Token.spell(Token.EOF));
	return Token.EOF;
    default:
	break;
    }

    accept(); 
    return Token.ERROR;
  }

  void skipSpaceAndComments() {
    // Yang - Last Update - Mon 2030

    // skip space ' '
    if (currentChar == ' ') {
      //DEBUG
      System.out.println("space found");
      //END
      currentChar = sourceFile.getNextChar();
    } 

    // skip comment
    if (currentChar == '/'){
      int cnt = 1;
      char peekNextChar = inspectChar(1);
      boolean isEndOfLineComment = false;
      boolean isTraditionalComment = false;

      // detemine comment type
      if (peekNextChar == '/'){
        //DEBUG
        System.out.println("// found");
        //END
        isEndOfLineComment = true;
        currentChar = sourceFile.getNextChar();
      }
      else if (peekNextChar == '*'){
        //DEBUG
        System.out.println("/* found");
        //END
        isTraditionalComment = true;
        currentChar = sourceFile.getNextChar();
      }

      // skip end-of-line comment "//"
      if (isEndOfLineComment){
        do{
          currentChar = sourceFile.getNextChar();
        }while(currentChar != '\n' && currentChar != Token.EOF);
      }

      // skip traditional comment "/*...*/"
      if(isTraditionalComment){
        boolean isTerminatorsFound = false;
        do{
          currentChar = sourceFile.getNextChar();
          if (currentChar == '*' && inspectChar(1) == '/'){
            //DEBUG
            System.out.println("*/ found");
            //END
            isTerminatorsFound = true;
            currentChar = sourceFile.getNextChar();
          }
        }while(currentChar != Token.EOF && isTerminatorsFound == false);
        // Error: Unterminated comment
        if (currentChar == Token.EOF && isTerminatorsFound == false){
          System.out.println("Error: Unterminated comment");
        }
      }
    }
  }

  public Token getToken() {
    Token tok;
    int kind;

    // skip white space and comments

   skipSpaceAndComments();

   currentSpelling = new StringBuffer("");

   sourcePos = new SourcePosition();

   // You must record the position of the current token somehow

   kind = nextToken();

   tok = new Token(kind, currentSpelling.toString(), sourcePos);

   // * do not remove these three lines
   if (debug)
     System.out.println(tok);
   return tok;
   }

}
