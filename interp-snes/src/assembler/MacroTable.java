package assembler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import assembler.syntax.node.*;

public class MacroTable {

	private Map<String, MacroInfo> macros = new HashMap<>();
	
	public void addMacroDecl(AMacroDecl declaration, List<ParamInfo> paramList) {
		String name = declaration.getIdent().getText();
		
		if(this.macros.get(name) != null) {
			throw new SemanticException(declaration.getIdent(),
                    "macro " + name + " has already been declared");
		}
		
		MacroInfo macroInfo = new MacroInfo(declaration, paramList);
		this.macros.put(name, macroInfo);
	}
	
	public MacroInfo getMacroInfo(String macroName) {
		return this.macros.get(macroName);
	}
}
