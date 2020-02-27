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

  private int line = 1;
  private int column = 1;

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
    //DEBUG
    System.out.println("DEBUG: currentSpelling is going to append " + currentChar);
    //END

    // you may save the lexeme of the current token incrementally here
    currentSpelling.append(currentChar);
    
    // you may also increment your line and column counters here
    // IMPORTANT Yang - For accept, we probabaly should not handle charEnd here, only after the token was fully taken 
    //DEBUG
    System.out.println("DEBUG: update pos during accept: line-" + line + " column-" + column);
    //END
    
    // get next char
    currentChar = sourceFile.getNextChar();
    column = column + 1;
  }

  // skip and get next character from the source program
  private void skip(){
    // increase line
    if (currentChar == '\n'){
      line = line + 1;
      column = 1;
    }
    // increase column
    else if (currentChar == '\t'){
      column = column + 8;
    }
    else{
      column = column + 1;
    }
    // //DEBUG
    // System.out.println("DEBUG: update pos during skip: line-" + line + " column-" + column + " charEnd-" + charEnd);
    // //END

    // get next char
    currentChar = sourceFile.getNextChar();
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
          System.out.println("DEBUG: digit " + currentChar + " found");
          //END
          accept();
        }while(Character.isDigit(currentChar));
        if (currentChar == '.' || currentChar == 'e' || currentChar == 'E'){
          //DEBUG
          System.out.println("DEBUG: After digit " + currentChar + " is found.");
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
      column = column + 1; // EOF still take 1 position
	    return Token.EOF;
    default:
      //QUESTION: if reach here, maybe it is not llegal? Report error?
	    break;
    }

    accept(); 
    return Token.ERROR;
  }

  void skipSpaceAndComments() {

    boolean isEndOfLineComment = false;
    boolean isTraditionalComment = false;

    // DEBUG
    System.out.println("DEBUG: Start of Skip");
    System.out.println("DEBUG: Next skip inspect is " + currentChar);
    // END

    // skip space, and return
    if (currentChar == ' ' || currentChar == '\t' || currentChar == '\n') {
      //DEBUG
      System.out.println("DEBUG: " + currentChar +" found");
      //END
      skip();
      // Recurse - skip until a valid content is found
      skipSpaceAndComments();
    } 

    // determine comment type, return if it not a comment
    if (currentChar == '/' && inspectChar(1) == '/'){
      isEndOfLineComment = true;
      skip(); // point to the 2nd '/'
      skip(); // point to the actual comment
      //DEBUG
      System.out.println("DEBUG: // found");
      //END
    }
    else if (currentChar == '/' && inspectChar(1) == '*'){
      isTraditionalComment = true;
      skip(); // point to the '*'
      skip(); // point to the actual comment
      //DEBUG
      System.out.println("DEBUG: /* found");
      //END
    }
    else { // Not a comment, return 
      return;
    }

    // skip the end-of-line comment
    if (isEndOfLineComment){
      while(currentChar != '\n' && currentChar != sourceFile.eof){
        skip();
      }
      // Recurse - skip until a valid content is found
      if (currentChar != sourceFile.eof){
        skip();
        skipSpaceAndComments();
      }
    }

    // skip the traditional comment
    if(isTraditionalComment){
      boolean isTerminatorPairFound = false;
      while(currentChar != sourceFile.eof){
        // if terminator pair found, exit loop
        if(currentChar == '*' && inspectChar(1) == '/'){
          //DEBUG
          System.out.println("DEBUG: */ found");
          //END
          isTerminatorPairFound = true;
          skip(); // point to the 2nd terminator '/'
          skip(); // point to the next char after the comment
          break;
        }
        //DEBUG
        System.out.println("DEBUG: in /* */, we skipped " + currentChar);
        //END
        // have not reach the end of comment, skip more
        skip();
      }
      // Error: Unterminated comment, return
      if (isTerminatorPairFound == false){
        System.out.println("Error: unterminated comment");
        return;
      }
      // Recurse - skip until a valid content is found
      if (currentChar != sourceFile.eof && isTerminatorPairFound){
        skip();
        skipSpaceAndComments();
      }
    }
  }
  
  public Token getToken() {
    Token tok;
    int kind;

    currentSpelling = new StringBuffer("");
    sourcePos = new SourcePosition();

    // skip white space and comments
    skipSpaceAndComments();

    //DEBUG
    System.out.println("DEBUG: End of Skip");
    //END

    // You must record the position of the current token somehow
    //...

    kind = nextToken();

    // Update source location
    /* math: how to calculate charStart (normal case)
     * e.g 1 2 3 4 5
     *     / /   n
     *             ^
     *          column when finish nextToken()
     * Hence,
     * charEnd = column - 1
     * charStart = column - token.length + 1 - 1
     */
    int charStart = column - currentSpelling.length();
    int charEnd = column - 1;
    sourcePos = new SourcePosition(line,charStart,charEnd);

    tok = new Token(kind, currentSpelling.toString(), sourcePos);

    // * do not remove these three lines
    if (debug)
      System.out.println(tok);
    return tok;
   }

   // Custom functions
  private void getFraction(){

  }

  private void getExponent(){
    
  }
}
