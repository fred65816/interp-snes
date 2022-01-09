package assembler;

import assembler.syntax.node.Token;

public class InterpreterException extends RuntimeException {

	public InterpreterException(Token token, String message) {
		super("[" + token.getLine() + ":" + token.getPos() + "] " + message);
	}
}
