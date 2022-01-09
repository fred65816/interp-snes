package assembler;

import java.util.ArrayList;
import java.util.List;

import assembler.syntax.node.TIdent;

public class NameSpace {
	private NameSpace parent;
	private List<String> declaredLabels = new ArrayList<>();
	
	public NameSpace(NameSpace parent) {
		this.parent = parent;
	}
	
	public NameSpace getParent() {
		return this.parent;
	}
	
	public void addLabel(TIdent ident) {
		// on ne regarde pas le(s) parent(s)
		if(this.declaredLabels.contains(ident.getText())) {
			throw new SemanticException(ident, "label " + ident.getText() + " is already declared");
		}
		this.declaredLabels.add(ident.getText());
	}

	public boolean labelIsDeclared(String name) {
		if(this.declaredLabels.contains(name)) {
			return true;
		}
		if(parent != null) {
			return parent.labelIsDeclared(name);
		}
		return false;
	}
}
