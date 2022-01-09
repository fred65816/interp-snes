package assembler;

import assembler.syntax.node.Token;

public class CompilationException extends RuntimeException {
	public CompilationException(String message) {
		super(message);
	}
}
