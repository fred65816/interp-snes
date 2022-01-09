
package assembler;

import java.util.*;

import assembler.syntax.node.*;

public class Frame {

    

    private Map<String, Value> variables = new HashMap<>();
    private List<Label> labels = new LinkedList<>();
    private Token location;
    private MacroInfo macroInfo;
    private Frame parentFrame;
    
    public Frame() {

    }

    public Frame(Frame parentFrame, MacroInfo macroInfo, Token location) {
        this.parentFrame = parentFrame;
        this.macroInfo = macroInfo;
        this.location = location;
    }
    
	public void addLabel(String name, String prefix, int macroId, int offset) {
		this.labels.add(new Label(name, prefix, macroId, offset));	
	}
	
	public Integer getLabelOffset(String name, String prefix, int macroId) {
		List<Label> sameName = new LinkedList<>();
		for(Label label: this.labels) {
			// on ajoute à un liste les labels ayant le même nom et étant dans le même macro
			if(label.getName().equals(name) && label.getMacroId() == macroId) {
				sameName.add(label);		
			}
		}
		do {
			for(Label label: sameName) {
				// on regarde le préfix courant, si on ne trouve pas
				// on enlève le dernier nom du préfix et on regarde dans le parent
				if(label.getPrefix().equals(prefix)) {
					return label.getOffset();
				}
			}
			// reconstruction du préfix parent
			String[] parts = prefix.split("-");
			prefix = "";
			
			for(int i = 0; i < parts.length - 1; i++) {
				prefix += parts[i];
				if(i < parts.length - 2) {
					prefix += "-";
				}
			}
		} while(!prefix.equals(""));
		
		return null;
	}
	
	public Integer getLabelOffset(String name) {
		for(Label label: this.labels) {
			// si le label a le même nom et qu'il n'est pas dans un namespace
			// (donc il est dans le main ou le "main" d'un macro)
			if(label.getName().equals(name) && label.getPrefix().equals("")) {
				return label.getOffset();
			}
		}
		return null;
	}
	
	public Integer getAnonLabelOffset(int branchOffset, int macroId, String sign, Token token) {
		int diff = Consts.MAX_INT;
		Label labelAnon = null;
		
		// si label anonyme positif
		if(sign.equals("+")) {
			for(Label label: this.labels) {
				// on va chercher la déclaration positive
				// la plus proche en descendant
				if(label.getName().contains("+") &&
				   label.getMacroId() == macroId && 
				   label.getOffset() - branchOffset < diff &&
				   label.getOffset() - branchOffset > 0) {
					diff = label.getOffset() - branchOffset;
					labelAnon = label;
				}
			}
			
			// edge case comme seulement "++ beq ++" dans un fichier
			if(labelAnon == null) {
				throw new InterpreterException(token,
						"misuse of positive anomymous label declaration or branching");
			}
		}
		else {
			for(Label label: this.labels) {
				// on va chercher la déclaration négative
				// la plus proche en remontant
				if(label.getName().contains("-") &&
				   label.getMacroId() == macroId && 
				   branchOffset - label.getOffset() < diff &&
				   branchOffset - label.getOffset() > 0) {
					
					diff = branchOffset - label.getOffset();
					labelAnon = label;
				}
			}
			
			// edge case comme seulement "-- beq --" dans un fichier
			if(labelAnon == null) {
				throw new InterpreterException(token,
						"misuse of negative anomymous label declaration or branching");
			}
		}
		return labelAnon.getOffset();
	}

    public void putVariable(TIdent ident, Value value, Frame frame) {
        String varName = ident.getText();
        this.variables.put(varName, value);
    }

    public Value getVariable(TIdent ident, Frame frame) {
        String varName = ident.getText();
        return this.variables.get(varName);
    }

    public Frame getParentFrame() {

        return this.parentFrame;
    }

    public void setLocation(
            Token location) {

        this.location = location;
    }
}
