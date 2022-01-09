package assembler;

import java.util.LinkedList;
import java.util.List;

import assembler.syntax.analysis.DepthFirstAdapter;
import assembler.syntax.node.*;

public class SemanticAnalysisPhase1 extends DepthFirstAdapter {
	
	// création des global scope, variable scope et namespaces
	
	// ajout des macros aux macros table
	
	// ajout des labels au global scope courant ou namespace courant
	
	private SemanticInfo semantics;
	private GlobalScope currentGlobalScope;
	private NameSpace currentNameSpace; 
	private List<ParamInfo> currentParamList;
    private Type currentType;
	
	public SemanticAnalysisPhase1(SemanticInfo semantics) {
		this.semantics = semantics;
	}
	
	private void visit(Node node) {
		if(node != null) {
			node.apply(this);
		}
	}
	
	@Override
	public void caseAProg(AProg node) {
		visit(node.getMacros());
		
		VariableScope variableScope = new VariableScope(null);		
		this.currentGlobalScope = new GlobalScope(node, variableScope);
		this.semantics.addGlobalScope(node, this.currentGlobalScope);
		
		visit(node.getInsts());
		
		this.currentGlobalScope = null;
	}
	
	@Override
	public void caseANamespaceInst(ANamespaceInst node) {
		String name = node.getIdent().getText();
		NameSpace nameSpace = new NameSpace(this.currentNameSpace);
		this.currentGlobalScope.addNameSpace(name, nameSpace, node.getIdent());
		this.currentNameSpace = nameSpace;
		
		visit(node.getBody());
		
		this.currentNameSpace = this.currentNameSpace.getParent();
	}
	
	@Override
	public void caseAMacroDecl(AMacroDecl node) {
		// construit la liste des paramètres
        this.currentParamList = new LinkedList<>();
        visit(node.getParams());
        List<ParamInfo> paramList = this.currentParamList;
        this.currentParamList = null;
        
        VariableScope variableScope = new VariableScope(null);
		this.currentGlobalScope = new GlobalScope(node, variableScope);
		this.semantics.addGlobalScope(node, this.currentGlobalScope);
		
		visit(node.getBody());
		
		this.currentGlobalScope = null;
		
		this.semantics.addMacroDecl(node, paramList);
	}
	
	@Override
	public void caseALabelInst(ALabelInst node) {
		if(this.currentNameSpace == null) {
			this.currentGlobalScope.addLabel(node.getIdent());
		}
		else {
			this.currentNameSpace.addLabel(node.getIdent());
		}
	}
	
	@Override
	public void caseAParam(AParam node) {
        this.currentType = null;
        visit(node.getType());
        Type type = this.currentType;
        this.currentType = null;

        this.currentParamList.add(new ParamInfo(node, type));
	}
	
	@Override
    public void caseAIntType(AIntType node) {
        this.currentType = Type.INT;
    }

    @Override
    public void caseABoolType(ABoolType node) {
        this.currentType = Type.BOOL;
    }

    @Override
    public void caseAStringType(AStringType node) {
        this.currentType = Type.STRING;
    }
}
