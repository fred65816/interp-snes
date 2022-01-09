package assembler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import assembler.syntax.node.*;


public class SemanticInfo {

	private MacroTable macroTable = new MacroTable();
	private Map<Node, GlobalScope> scopes = new HashMap<>();
	private Map<PNumber, Integer> numbers = new HashMap<>();
	private Map<PArgNum, OpcodeArg> opcodeArgs = new HashMap<>();
	
	public void addGlobalScope(Node node, GlobalScope scope) {
		this.scopes.put(node, scope);	
	}

	public GlobalScope getGlobalScope(Node node) {
		return this.scopes.get(node);
	}
	
	public void addOpcodeArg(PArgNum node, OpcodeArg opcodeArg) {
		this.opcodeArgs.put(node, opcodeArg);
	}
	
	public OpcodeArg getOpcodeArg(PArgNum node) {
		return this.opcodeArgs.get(node);
	}

	public void addNumber(PNumber node, int value) {
		this.numbers.put(node, value);
		
	}
	
	public int getNumber(Node node) {
		return this.numbers.get(node);
	}

	public void addMacroDecl(AMacroDecl declaration, List<ParamInfo> paramList) {
		this.macroTable.addMacroDecl(declaration, paramList);		
	}
	
	public MacroInfo getMacroInfo(String macroName) {
		return this.macroTable.getMacroInfo(macroName);
	}
}
