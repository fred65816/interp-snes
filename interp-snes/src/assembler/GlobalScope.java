package assembler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import assembler.syntax.node.Node;
import assembler.syntax.node.TIdent;

public class GlobalScope {
	private List<String> declaredLabels = new ArrayList<>();
	private Map<String, NameSpace> nameSpaces = new HashMap<>();
	private Map<Node, VariableScope> variableScopes = new HashMap<>();
	private Node sourceNode;
	
	public GlobalScope(Node node, VariableScope variableScope) {
		addVariableScope(node, variableScope);
		this.sourceNode = node;
	}
	
	public void addNameSpace(String name, NameSpace nameSpace, TIdent ident) {
		if(this.nameSpaces.containsKey(name)) {
			throw new SemanticException(ident, "namespace " + name + " is already declared");
		}
		this.nameSpaces.put(name, nameSpace);	
	}

	public NameSpace getNameSpace(String name) {
		return this.nameSpaces.get(name);
	}
	
	public void addVariableScope(Node node, VariableScope variableScope) {
		this.variableScopes.put(node, variableScope);	
	}
	
	public VariableScope getVariableScope(Node node) {
		return this.variableScopes.get(node);	
	}
	
	public void addDecl(TIdent ident, Type type) {
		VariableScope variableScope = this.variableScopes.get(this.sourceNode);
		variableScope.addDecl(ident, type);
	}
	
	public void addLabel(TIdent ident) {
		String name = ident.getText();
		if(labelIsDeclared(name)) {
			throw new SemanticException(ident, "label " + name + " is already declared");
		}
		this.declaredLabels.add(name);
	}

	public boolean labelIsDeclared(String name) {
		if(this.declaredLabels.contains(name)) {
			return true;
		}
		return false;
	}
}
