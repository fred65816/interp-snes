package assembler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import assembler.syntax.node.Node;
import assembler.syntax.node.TIdent;

public class VariableScope {
	private VariableScope parent;
	private Map<String, Type> declaredVariables = new HashMap<>();
	
	public VariableScope(VariableScope parentScope) {
		this.parent = parentScope;
	}
	
	public VariableScope getParent() {
		return this.parent;
	}
	
	public void addDecl(TIdent ident, Type type) {
		String name = ident.getText();
		if(alreadyDeclared(name)) {
			throw new SemanticException(ident, "variable " + name + " is already declared");
		}
		this.declaredVariables.put(name, type);
	}

	private boolean alreadyDeclared(String name) {
		if(this.declaredVariables.containsKey(name)) {
			return true;
		}
		if(parent != null) {
			return parent.alreadyDeclared(name);
		}
		return false;
	}

	public Type getType(TIdent ident) {
		if(!alreadyDeclared(ident.getText())) {
			throw new SemanticException(ident, "variable " + ident.getText() + " is not declared");
		}
		if(this.declaredVariables.containsKey(ident.getText())) {
			return this.declaredVariables.get(ident.getText());
		}
		return this.parent.getType(ident);
	}
}
