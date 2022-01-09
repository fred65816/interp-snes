package assembler;

import java.util.Iterator;
import java.util.List;

import assembler.syntax.node.*;

public class MacroInfo {
	
	private TIdent name;
	private PBody macroBody;
	private List<ParamInfo> paramList;
	
	public MacroInfo(AMacroDecl declaration, List<ParamInfo> paramList) {
		this.name = declaration.getIdent();
		this.macroBody = declaration.getBody();
		this.paramList = paramList;
	}
	
	public PBody getMacroBody() {
		return this.macroBody;
	}
	
	public String getName() {
		return this.name.getText();
	}
	
	public List<ParamInfo> getParams() {
		return this.paramList;
	}
	
	public void addParamsToScope(GlobalScope scope) {
		for(ParamInfo param: this.paramList) {
			scope.addDecl(param.getName(), param.getType());
		}
	}
	
	public void checkArgTypes(List<Type> argTypes, Token location) {
		Iterator<Type> argTypesIterator = argTypes.iterator();
		
		for(ParamInfo param: this.paramList) {
			String name = param.getName().getText();
			if(argTypesIterator.hasNext()) {
				Type argType = argTypesIterator.next();
				if(argType != param.getType()) {
					throw new SemanticException(location, 
							"invalid argument for the " + name + " parameter");
				}
			}
			else {
				throw new SemanticException(location,
                        "missing argument for the " + name + " parameter");
			}
		}
		
		if(argTypesIterator.hasNext()) {
			throw new SemanticException(location, "too many arguments");
		}
	}
	
	public void assignArgs(List<Value> args, Frame frame, Token location) {
		
		Iterator<Value> argsIterator = args.iterator();
		
		for(ParamInfo param: this.paramList) {
			if(argsIterator.hasNext()) {
				Value value = argsIterator.next();
				frame.putVariable(param.getName(), value, frame);
			}
			else {
				String name = param.getName().getText();
				throw new InterpreterException(location,
						"missing value for the " + name + " parameter");
			}
		}
		
		if(argsIterator.hasNext()) {
			throw new InterpreterException(location, "too many arguments");
		}
	}
}
