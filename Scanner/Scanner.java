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
    sourcePos = new SourcePosition();
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
      // operators
      case '+'://11
        accept();
        return Token.PLUS;
      case '-'://12
        accept();
        return Token.MINUS;
      case '*'://13
        accept();
        return Token.MULT;
      case '/'://14
        accept();
        return Token.DIV;
      case '!'://15 //16
        accept();
        if(currentChar == '='){
          accept();
          return Token.NOTEQ;
        } else {
          return Token.EQ;
        }
      case '='://17 //18
        accept();
        if(currentChar == '='){
          accept();
          return Token.EQEQ;
        } else { 
          return Token.EQ;
        }
      case '<'://19 //20
        accept();
        if(currentChar == '='){
          accept();
          return Token.LTEQ;
        } else {
          return Token.LT;
        }
      case '>'://21 //22
        accept();
        if(currentChar == '='){
          accept();
          return Token.GTEQ;
        } else {
          return Token.GT;
        }
      case '&'://23 //Error:illegal character
        accept();
        if(currentChar == '&'){
          accept();
          return Token.ANDAND;
        } else {
          return Token.ERROR;
        }
      case '|'://24 //Error:illegal character (Example from original code)
        accept();
        if (currentChar == '|') {
          accept();
          return Token.OROR;
        } else {
          return Token.ERROR;
        }
      // separators 
      case '{'://25
        accept();
        return Token.LCURLY;
      case '}'://26
        accept();
        return Token.RCURLY;
      case '('://27
        accept();
        return Token.LPAREN;
      case ')'://28
        accept();
        return Token.RPAREN;
      case '['://29
        accept();
        return Token.LBRACKET;
      case ']'://30
        accept();
        return Token.RBRACKET;
      case ';'://31
        accept();
        return Token.SEMICOLON;
      case ','://32
        accept();
        return Token.COMMA;
      //Int or Float
      case '0':
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case '6':
      case '7':
      case '8':
      case '9':
        do{
          //DEBUG
          System.out.println("digit " + currentChar + " found");
          //END
          accept();
        }while(Character.isDigit(currentChar));
        if (currentChar == '.' || currentChar == 'e' || currentChar == 'E'){
          //DEBUG
          System.out.println("After digit " + currentChar + " is found.");
          //END
          // getFraction();
          return Token.FLOATLITERAL;
        } else {
          return Token.INTLITERAL;
        }
      //Float
      case '.':
        // getFraction();
        break;
        //  attempting to recognise a float
      // String literal

    // ....
    case SourceFile.eof:	
	    currentSpelling.append(Token.spell(Token.EOF));
	    return Token.EOF;
    default:
      //Q:if reach here, maybe it is not llegal? Report error?
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
      accept();
    } 

    // skip comment
    if (currentChar == '/'){
      char peekNextChar = inspectChar(1);
      boolean isEndOfLineComment = false;
      boolean isTraditionalComment = false;

      // detemine comment type
      if (peekNextChar == '/'){
        //DEBUG
        System.out.println("// found");
        //END
        isEndOfLineComment = true;
        accept();
      }
      else if (peekNextChar == '*'){
        //DEBUG
        System.out.println("/* found");
        //END
        isTraditionalComment = true;
        accept();
      }

      // skip end-of-line comment "//"
      if (isEndOfLineComment){
        do{
          currentChar = sourceFile.getNextChar();
          // //DEBUG
          // System.out.println("after //: " + currentChar);
          // //END
        }while(currentChar != '\n' && currentChar != sourceFile.eof);
      }

      // skip traditional comment "/*...*/"
      if(isTraditionalComment){
        boolean isTerminatorsFound = false;
        do{
          accept();
          if (currentChar == '*' && inspectChar(1) == '/'){
            //DEBUG
            System.out.println("*/ found");
            //END
            isTerminatorsFound = true;
            break;
          }
        }while(currentChar != sourceFile.eof);
        // Error: Unterminated comment
        if (currentChar == sourceFile.eof && isTerminatorsFound == false){
          System.out.println("Error: Unterminated comment");
        }
        else if (isTerminatorsFound){
          accept();
        }
      }
    }

    // //DEBUG
    // System.out.println("---End of this skip----");
    // //END
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

   private void getFraction(){

   }

}
