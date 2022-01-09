package assembler;

import assembler.syntax.node.Token;

public class SemanticException extends RuntimeException {

	public SemanticException(Token token, String message) {

		super("[" + token.getLine() + ":" + token.getPos() + "] " + message);
	}
	
	public SemanticException(String message) {

		super(message);
	}
}
