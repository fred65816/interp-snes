package assembler;

import java.util.LinkedList;
import java.util.List;

import assembler.syntax.analysis.DepthFirstAdapter;
import assembler.syntax.node.*;


public class SemanticAnalysisPhase2 extends DepthFirstAdapter {
	

	private SemanticInfo semantics;
	
	// scope global courant (un seul par macro ou programme main)
	private GlobalScope currentGlobalScope;
	
	// namespace courant (possiblement plusieurs par macros ou programme main)
	private NameSpace currentNameSpace;
	
	// scope de variables courant (possiblement plusieurs par macros ou programme main)
	private VariableScope currentVariableScope;
	
	// scope global du main (utilisé pour vérifier labels du main)
	private GlobalScope mainGlobalScope;
	
	private List<Type> currentArgTypes;
	private Type resultType;
	
	// booléens servant à savoir si les labels
	// anonymes sont utilisés de la bonne façon
	private boolean anonLabelPlus;
	private boolean anonLabelMinus;
	
	// compteurs servant à savoir si le dernier
	// label anomyme est bien déclaré/utilisé
	private int numAnonLabelPlus;
	private int numAnonLabelMinus;
	
	private void visit(Node node) {
        if (node != null) {
            node.apply(this);
        }
    }

    private Type evalType(Node node) {
        visit(node);
        return this.resultType;
    }
    
    private Type getType(PType type) {
    	if(type instanceof AStringType) {
    		return Type.STRING;
    	}
    	else if(type instanceof AIntType) {
    		return Type.INT;
    	}
    	else {
    		return Type.BOOL;
    	}
    }
	
	public SemanticAnalysisPhase2(SemanticInfo semantics) {
		this.semantics = semantics;
	}
	
	@Override
	public void caseAProg(AProg node) {
		this.currentGlobalScope = semantics.getGlobalScope(node);
		
		// on a besoin du main scope pour une utilisation
		// d'un label dans un macro qui fait référence 
		// à un label dans le programme principal
		this.mainGlobalScope = this.currentGlobalScope;
		
		visit(node.getMacros());
		
		this.currentGlobalScope = this.semantics.getGlobalScope(node);
		this.currentVariableScope = this.currentGlobalScope.getVariableScope(node);
		this.mainGlobalScope = this.currentGlobalScope;
		
		this.anonLabelPlus = true;
		this.anonLabelMinus = false;
		this.numAnonLabelPlus = 0;
		this.numAnonLabelMinus = 0;
		
		visit(node.getInsts());
		
		// si il y a un "++" ou "--" trainant on lance un exception
		if(this.numAnonLabelPlus % 2 != 0) {
        	throw new SemanticException("main program is missing a positive anomymous label declaration");
        }
        if(this.numAnonLabelMinus % 2 != 0) {
        	throw new SemanticException("main program is missing a negative anomymous branching");
        }
		
		this.currentGlobalScope = null;
	}
	
	@Override
	public void caseAMacroDecl(AMacroDecl node) {
		this.currentGlobalScope = this.semantics.getGlobalScope(node);
		this.currentVariableScope = this.currentGlobalScope.getVariableScope(node);
		
		this.anonLabelPlus = true;
		this.anonLabelMinus = false;
		this.numAnonLabelPlus = 0;
		this.numAnonLabelMinus = 0;
		
		String macroName = node.getIdent().getText();
		MacroInfo macroInfo = this.semantics.getMacroInfo(macroName);
		macroInfo.addParamsToScope(this.currentGlobalScope);
		
		// visite du corps
        visit(node.getBody());
        
        // si il y a un "++" ou "--" trainant on lance un exception
        if(this.numAnonLabelPlus % 2 != 0) {
        	throw new SemanticException(node.getRPar(), "macro is missing a positive anomymous label declaration");
        }
        if(this.numAnonLabelMinus % 2 != 0) {
        	throw new SemanticException(node.getRPar(), "macro is missing a negative anomymous branching");
        }
        
        // retrait de la portée
        this.currentGlobalScope = null;
	}
	
	@Override
	public void caseADeclInst(ADeclInst node) {
		Type expType = evalType(node.getExp());
		Type declType = getType(node.getType());
		
		if (expType != declType) {
            throw new SemanticException(node.getAssign(),
                    "the expression can't be assigned in this variable");
        }
		
        this.currentVariableScope.addDecl(node.getIdent(), expType);
	}
	
	@Override
	public void caseAAssignInst(AAssignInst node) {
		Type expType = evalType(node.getExp());
        Type varType = this.currentVariableScope.getType(node.getIdent());
        
        if (expType != varType) {
            throw new SemanticException(node.getAssign(),
                    "the expression can't be assigned in this variable");
        }
	}
	
	@Override
	public void caseAIfInst(AIfInst node) {
		Type type = evalType(node.getExp());
		
		if(type != Type.BOOL) {
			throw new SemanticException(node.getLPar(), "expression is not a boolean");
		}
		
		this.currentVariableScope = new VariableScope(this.currentVariableScope);
		this.currentGlobalScope.addVariableScope(node, this.currentVariableScope);
		
		visit(node.getBody());
		
		this.currentVariableScope = this.currentVariableScope.getParent();
	}
	
	@Override
	public void caseAWhileInst(AWhileInst node) {
		Type type = evalType(node.getExp());
		
		if(type != Type.BOOL) {
			throw new SemanticException(node.getLPar(), "expression is not a boolean");
		}
		
		this.currentVariableScope = new VariableScope(this.currentVariableScope);
		this.currentGlobalScope.addVariableScope(node, this.currentVariableScope);
		
		visit(node.getBody());
        
		this.currentVariableScope = this.currentVariableScope.getParent();
	}
	
	@Override
	public void caseAOrgInst(AOrgInst node) {
		Type type = evalType(node.getExp());
		
		if(type != Type.INT) {
			throw new SemanticException(node.getLPar(), "expression is not a number");
		}
	}
	
	@Override
	public void caseAFillInst(AFillInst node) {
		Type typeExp = evalType(node.getExp());
		Type typeNum = evalType(node.getNumber());
		
		if(typeExp != Type.INT || typeNum != Type.INT) {
			throw new SemanticException(node.getFill(), "expression is not a number");
		}
	}
	
	@Override
	public void caseANamespaceInst(ANamespaceInst node) {
		this.currentNameSpace = this.currentGlobalScope.getNameSpace(node.getIdent().getText());
		
		visit(node.getBody());
		
		this.currentNameSpace = this.currentNameSpace.getParent();
	}
	
	@Override
	public void caseAMacroCallInst(AMacroCallInst node) {
		String macroName = node.getIdent().getText();	
		MacroInfo macroInfo = this.semantics.getMacroInfo(macroName);
		
		if(macroInfo == null) {
			throw new SemanticException(node.getIdent(),
					"the macro " + macroName + " doesn't exist");
		}
		
		// sauvegarde la liste courante
        List<Type> previousArgTypes = this.currentArgTypes;
        this.currentArgTypes = new LinkedList<>();

        // calcul des types des arguments
        visit(node.getArgs());
        List<Type> argTypes = this.currentArgTypes;

        // remise en place de la liste courante
        this.currentArgTypes = previousArgTypes;

        macroInfo.checkArgTypes(argTypes, node.getLPar());
	}

	@Override
	public void caseAPrintInst(APrintInst node) {
		evalType(node.getExp());
	}
	
	@Override
	public void caseAPrintLnInst(APrintLnInst node) {
		if(node.getExp() != null) {
			evalType(node.getExp());
		}
	}
	
	@Override
	public void caseAArg(AArg node) {
		Type type = evalType(node.getExp());
        this.currentArgTypes.add(type);
	}
	
	@Override
	public void caseAExpByteSeq(AExpByteSeq node) {
		Type type = evalType(node.getExp());
		
		if(type != Type.INT) {
			throw new SemanticException(node.getLCbr(), "expression is not a number");
		}
	}
	
	@Override
	public void caseACmpConstOpcode(ACmpConstOpcode node) {
		visit(node.getOpArg());
		
		// si on a affaire à un nombre (e.g. CMP #$1234)
		if(node.getOpArg() instanceof ANumOpArg) {
			ANumOpArg opArg = (ANumOpArg)node.getOpArg();
			OpcodeArg arg = this.semantics.getOpcodeArg(opArg.getArgNum());
			
			// argument de 16-bit maximum
			if(arg.getNumBytes() > 2) {
				throw new SemanticException(node.getCmp(),
						"opcode argument must be one or two byte");
			}
		}
		
		// on ne peut pas utiliser ".l" puisque qu'on a un max
		// de 2 octets d'arguments
		if(node.getOpLength() instanceof ALongOpLength) {
			throw new SemanticException(node.getCmp(),
					"cannot use the long operation length specification");			
		}
	}
	
	// il y a un bug avec cette instruction, je n'ai pas eu le temps de le résoudre
	@Override
	public void caseADecAbsOpcode(ADecAbsOpcode node) {
		visit(node.getOpArg());
		
		// si on a affaire à un nombre (e.g. DEC $1234)
		if(node.getOpArg() instanceof ANumOpArg) {
			ANumOpArg opArg = (ANumOpArg)node.getOpArg();
			OpcodeArg arg = this.semantics.getOpcodeArg(opArg.getArgNum());
			
			// argument de 16-bit maximum
			if(arg.getNumBytes() > 2) {
				throw new SemanticException(node.getDec(),
						"opcode argument must be one or two byte");
			}
		}
		
		// on ne peut pas utiliser ".l" puisque qu'on a un max
		// de 2 octets d'arguments
		if(node.getOpLength() instanceof ALongOpLength) {
			throw new SemanticException(node.getDec(),
					"cannot use the long operation length specification");			
		}
	}
	
	// il y a un bug avec cette instruction, je n'ai pas eu le temps de le résoudre
	@Override
	public void caseAIncAbsOpcode(AIncAbsOpcode node) {
		visit(node.getOpArg());
		
		// si on a affaire à un nombre (e.g. INC $1234)
		if(node.getOpArg() instanceof ANumOpArg) {
			ANumOpArg opArg = (ANumOpArg)node.getOpArg();
			OpcodeArg arg = this.semantics.getOpcodeArg(opArg.getArgNum());
			System.out.println(arg);
			
			// argument de 16-bit maximum
			if(arg.getNumBytes() > 2) {
				throw new SemanticException(node.getInc(),
						"opcode argument must be one or two byte");
			}
		}
		
		// on ne peut pas utiliser ".l" puisque qu'on a un max
		// de 2 octets d'arguments
		if(node.getOpLength() instanceof ALongOpLength) {
			throw new SemanticException(node.getInc(),
					"cannot use the long operation length specification");			
		}
	}

	@Override
	public void caseAJmpOpcode(AJmpOpcode node) {
		visit(node.getOpArg());
		
		// si on a affaire à un nombre (e.g. LDA #$1234)
		if(node.getOpArg() instanceof ANumOpArg) {
			ANumOpArg opArg = (ANumOpArg)node.getOpArg();
			OpcodeArg arg = this.semantics.getOpcodeArg(opArg.getArgNum());
			
			// argument de 16-bit minimum
			if(arg.getNumBytes() < 2) {
				throw new SemanticException(node.getJmp(),
						"opcode argument must be two or three byte");
			}
		}
	}
	
	@Override
	public void caseALdaConstOpcode(ALdaConstOpcode node) {
		visit(node.getOpArg());
		
		// si on a affaire à un nombre (e.g. LDA #$1234)
		if(node.getOpArg() instanceof ANumOpArg) {
			ANumOpArg opArg = (ANumOpArg)node.getOpArg();
			OpcodeArg arg = this.semantics.getOpcodeArg(opArg.getArgNum());
			
			// argument de 16-bit maximum
			if(arg.getNumBytes() > 2) {
				throw new SemanticException(node.getLda(),
						"opcode argument must be one or two byte");
			}
		}
		
		// on ne peut pas utiliser ".l" puisque qu'on a un max
		// de 2 octets d'arguments
		if(node.getOpLength() instanceof ALongOpLength) {
			throw new SemanticException(node.getLda(),
					"cannot use the long operation length specification");			
		}
	}
	
	@Override
	public void caseALdxConstOpcode(ALdxConstOpcode node) {
		visit(node.getOpArg());
		
		// si on a affaire à un nombre (e.g. LDX #$1234)
		if(node.getOpArg() instanceof ANumOpArg) {
			ANumOpArg opArg = (ANumOpArg)node.getOpArg();
			OpcodeArg arg = this.semantics.getOpcodeArg(opArg.getArgNum());
			
			// argument de 16-bit maximum
			if(arg.getNumBytes() > 2) {
				throw new SemanticException(node.getLdx(),
						"opcode argument must be one or two byte");
			}
		}
		
		// on ne peut pas utiliser ".l" puisque qu'on a un max
		// de 2 octets d'arguments
		if(node.getOpLength() instanceof ALongOpLength) {
			throw new SemanticException(node.getLdx(),
					"cannot use the long operation length specification");			
		}
	}
	
	@Override
	public void caseALdxAbsOpcode(ALdxAbsOpcode node) {
		visit(node.getOpArg());
		
		// si on a affaire à un nombre (e.g. LDX $1234)
		if(node.getOpArg() instanceof ANumOpArg) {
			ANumOpArg opArg = (ANumOpArg)node.getOpArg();
			OpcodeArg arg = this.semantics.getOpcodeArg(opArg.getArgNum());
			
			// argument de 16-bit maximum
			if(arg.getNumBytes() > 2) {
				throw new SemanticException(node.getLdx(),
						"opcode argument must be one or two byte");
			}
		}
		
		// on ne peut pas utiliser ".l" puisque qu'on a un max
		// de 2 octets d'arguments
		if(node.getOpLength() instanceof ALongOpLength) {
			throw new SemanticException(node.getLdx(),
					"cannot use the long operation length specification");			
		}
	}
	
	@Override
	public void caseAMvnOpcode(AMvnOpcode node) {
		visit(node.getSource());
		visit(node.getDest());
		
		// si un des arguments est un label
		if(node.getSource() instanceof ALabelOpArg || node.getDest() instanceof ALabelOpArg) {
			throw new SemanticException(node.getMvn(),
					"This instruction does not support a label as opcode argument");
		}
		
		
		// si on a affaire à un nombre source (e.g. MVN $7E, $7F)
		if(node.getSource() instanceof ANumOpArg) {
			ANumOpArg opArg = (ANumOpArg)node.getSource();
			OpcodeArg arg = this.semantics.getOpcodeArg(opArg.getArgNum());
			
			// argument de 8-bit
			if(arg.getNumBytes() != 1) {
				throw new SemanticException(node.getMvn(),
						"opcode source argument must be one byte");
			}
		}
		
		// si on a affaire à un nombre destination (e.g. MVN $7E, $7F)
		if(node.getDest() instanceof ANumOpArg) {
			ANumOpArg opArg = (ANumOpArg)node.getDest();
			OpcodeArg arg = this.semantics.getOpcodeArg(opArg.getArgNum());
			
			// argument de 8-bit
			if(arg.getNumBytes() != 1) {
				throw new SemanticException(node.getMvn(),
						"opcode destination argument must be one byte");
			}
		}
	}
	
	@Override
	public void caseAMvpOpcode(AMvpOpcode node) {
		visit(node.getSource());
		visit(node.getDest());
		
		// si un des arguments est un label
		if(node.getSource() instanceof ALabelOpArg || node.getDest() instanceof ALabelOpArg) {
			throw new SemanticException(node.getMvp(),
					"This instruction does not support a label as opcode argument");
		}
		
		
		// si on a affaire à un nombre source (e.g. MVN $7E, $7F)
		if(node.getSource() instanceof ANumOpArg) {
			ANumOpArg opArg = (ANumOpArg)node.getSource();
			OpcodeArg arg = this.semantics.getOpcodeArg(opArg.getArgNum());
			
			// argument de 8-bit
			if(arg.getNumBytes() != 1) {
				throw new SemanticException(node.getMvp(),
						"opcode source argument must be one byte");
			}
		}
		
		// si on a affaire à un nombre destination (e.g. MVN $7E, $7F)
		if(node.getDest() instanceof ANumOpArg) {
			ANumOpArg opArg = (ANumOpArg)node.getDest();
			OpcodeArg arg = this.semantics.getOpcodeArg(opArg.getArgNum());
			
			// argument de 8-bit
			if(arg.getNumBytes() != 1) {
				throw new SemanticException(node.getMvp(),
						"opcode destination argument must be one byte");
			}
		}
	}
	
	@Override
	public void caseASepOpcode(ASepOpcode node) {
		
		// si l'argument est un label
		if(node.getOpArg() instanceof ALabelOpArg) {
			throw new SemanticException(node.getSep(),
					"This instruction does not support a label as opcode argument");
		}
		
		visit(node.getOpArg());
		
		// si l'argument est un nombre (e.g. sep #$20)
		if(node.getOpArg() instanceof ANumOpArg) {
			ANumOpArg opArg = (ANumOpArg)node.getOpArg();
			OpcodeArg arg = this.semantics.getOpcodeArg(opArg.getArgNum());
			
			// argument fixe de 1 octet
			if(arg.getNumBytes() > 1) {
				throw new SemanticException(node.getSep(),
						"opcode argument must be one byte");
			}
		}
	}
	
	@Override
	public void caseARepOpcode(ARepOpcode node) {
		
		// si l'argument est un label
		if(node.getOpArg() instanceof ALabelOpArg) {
			throw new SemanticException(node.getRep(),
					"This instruction does not support a label as opcode argument");
		}
		
		visit(node.getOpArg());
		
		// si l'argument est un nombre (e.g. rep #$20)
		if(node.getOpArg() instanceof ANumOpArg) {
			ANumOpArg opArg = (ANumOpArg)node.getOpArg();
			OpcodeArg arg = this.semantics.getOpcodeArg(opArg.getArgNum());
			
			// argument fixe de 1 octet
			if(arg.getNumBytes() > 1) {
				throw new SemanticException(node.getRep(),
						"opcode argument must be one byte");
			}
		}
	}
	
	@Override
	public void caseAPlusAnonLabel(APlusAnonLabel node) {
		// si on a deux déclaration de "++" de suite
		if(this.anonLabelPlus) {
			throw new SemanticException(node.getForward(),
					"misuse of positive anomymous label declaration or branching");
		}
		this.anonLabelPlus = true;
		this.numAnonLabelPlus++;
	}
	
	@Override
	public void caseAMinusAnonLabel(AMinusAnonLabel node) {
		// si on a deux déclaration de "--" de suite
		if(this.anonLabelMinus) {
			throw new SemanticException(node.getBackward(),
					"misuse of negative anomymous label declaration or branching");
		}
		this.anonLabelMinus = true;
		this.numAnonLabelMinus++;
	}
	
	@Override
	public void caseAPlusBranchType(APlusBranchType node) {
		// si on a deux utilisations de "++" de suite (e.g. BNE ++)
		if(!this.anonLabelPlus) {
			throw new SemanticException(node.getForward(),
					"misuse of positive anomymous label declaration or branching");
		}
		this.anonLabelPlus = false;
		this.numAnonLabelPlus++;
	}
	
	@Override
	public void caseAMinusBranchType(AMinusBranchType node) {
		// si on a deux utilisations de "--" de suite (e.g. BNE --)
		if(!this.anonLabelMinus) {
			throw new SemanticException(node.getBackward(),
					"misuse of negative anomymous label declaration or branching");
		}
		this.anonLabelMinus = false;
		this.numAnonLabelMinus++;
	}
	
	@Override
	public void caseAIdentBranchType(AIdentBranchType node) {
		checkIfLabelIsDeclared(node.getIdent());
	}
	
	@Override
	public void caseALabelOpArg(ALabelOpArg node) {
		checkIfLabelIsDeclared(node.getIdent());
		
		if(node.getExtraArg() != null) {
			evalType(node.getExtraArg());
		}
	}
	
	private void checkIfLabelIsDeclared(TIdent ident) {
		String labelName = ident.getText();
		
		// si le label n'exite pas dans le scope courant, dans le main scope ou dans le namespace
		if(!this.currentGlobalScope.labelIsDeclared(labelName) && !this.mainGlobalScope.labelIsDeclared(labelName) &&
		   (this.currentNameSpace == null || !this.currentNameSpace.labelIsDeclared(labelName))) {
				throw new SemanticException(ident, "Label " + labelName + " is not declared");
		}	
	}
	
	@Override
	public void caseAExpOpArg(AExpOpArg node) {
		Type type = evalType(node.getExp());
		
		if(type != Type.INT) {
			throw new SemanticException(node.getLCbr(), "expression is not a number");
		}
	}
	
	@Override
	public void caseAHexArgNum(AHexArgNum node) {
		int value = 0;
		THexadecimal token = node.getHexadecimal();
		String strNumber = token.getText();
		
		// on enlève le "$"
		String number = strNumber.substring(1);
		
		try {
			value = Integer.parseInt(number, 16);
		}
		catch(NumberFormatException e) {
			throw new SemanticException(token, "Number is invalid");
		}
		
		// limiter à un entier de 24-bit sur un système 24-bit
		if(value < 0 || value > Consts.MAX_INT) {
			throw new SemanticException(node.getHexadecimal(),
					"number is out of bounds (must be between 0 and 0x" + Integer.toHexString(Consts.MAX_INT));
		}
		
		// 6 caractères maximum
		if(number.length() > 6) {
			throw new SemanticException(token, "Number cannot have more than 6 digits");
		}
		
		// longeur de la valeur à la compilation
		int length = number.length() <= 2 ? 1: number.length() <= 4 ? 2: 3;
		
		OpcodeArg opArg = new OpcodeArg(value, length);
		
		//System.out.println(value);
		 
		this.semantics.addOpcodeArg(node, opArg);
	}
	
	@Override
	public void caseABinaryArgNum(ABinaryArgNum node) {
		int value = 0;
		TBinary token = node.getBinary();
		String strNumber = token.getText();
		
		// on enlève le "%"
		String number = strNumber.substring(1);
		
		try {
			value = Integer.parseInt(number, 2);
		}
		catch(NumberFormatException e) {
			throw new SemanticException(token, "Number is invalid");
		}
		
		// limiter à un entier de 24-bit sur un système 24-bit
		if(value < 0 || value > Consts.MAX_INT) {
			throw new SemanticException(node.getBinary(),
					"number is out of bounds (must be between 0 and 0x" + Integer.toHexString(Consts.MAX_INT));
		}
		
		// 24 caractères maximum
		if(number.length() > 24) {
			throw new SemanticException(token, "Number cannot have more than 24 digits");
		}
		
		// longeur de la valeur à la compilation
		int length = number.length() <= 8 ? 1: number.length() <= 16 ? 2: 3;
		
		OpcodeArg opArg = new OpcodeArg(value, length);
		 
		this.semantics.addOpcodeArg(node, opArg);
	}
	
	@Override
	public void caseADecimalArgNum(ADecimalArgNum node) {
		int value = 0;
		TDecimal token = node.getDecimal();
		String strNumber = token.getText();

		try {
			value = Integer.parseInt(strNumber, 10);
		}
		catch(NumberFormatException e) {
			throw new SemanticException(token, "Number is invalid");
		}
		
		// limiter à un entier de 24-bit sur un système 24-bit
		if(value < 0 || value > Consts.MAX_INT) {
			throw new SemanticException(node.getDecimal(),
					"number is out of bounds (must be between 0 and 0x" + Integer.toHexString(Consts.MAX_INT));
		}
		
		// 8 caractères maximum
		if(strNumber.length() > 8) {
			throw new SemanticException(token, "Number cannot have more than 8 digits");
		}
		
		// longeur de la valeur à la compilation
		strNumber = Integer.toHexString(value);
		int length = strNumber.length() <= 2 ? 1: strNumber.length() <= 4 ? 2: 3; 
		
		OpcodeArg opArg = new OpcodeArg(value, length);
		 
		this.semantics.addOpcodeArg(node, opArg);
	}
	
	@Override
	public void caseAAddExtraArg(AAddExtraArg node) {
		Type type = evalType(node.getExtraExp());
		
		if(type != Type.INT) {
			throw new SemanticException(node.getPlus(), "expression is not a number");
		}
	}
	
	@Override
	public void caseASubExtraArg(ASubExtraArg node) {
		Type type = evalType(node.getExtraExp());
		
		if(type != Type.INT) {
			throw new SemanticException(node.getMinus(), "expression is not a number");
		}
	}
	
	@Override
	public void caseANotNegation(ANotNegation node) {
		Type type = evalType(node.getExp());
		
		if(type != Type.BOOL) {
			throw new SemanticException(node.getNot(), "expression is not a boolean");
		}
		
		this.resultType = Type.BOOL;
	}
	
	@Override
	public void caseAAndLogicalOp(AAndLogicalOp node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.BOOL) {
			throw new SemanticException(node.getLogAnd(), "left operand is not a boolean");
		}
		if(rightType != Type.BOOL) {
			throw new SemanticException(node.getLogAnd(), "right operand is not a boolean");
		}
		
		this.resultType = Type.BOOL;
	}
	
	@Override
	public void caseAOrLogicalOp(AOrLogicalOp node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.BOOL) {
			throw new SemanticException(node.getLogOr(), "left operand is not a boolean");
		}
		if(rightType != Type.BOOL) {
			throw new SemanticException(node.getLogOr(), "right operand is not a boolean");
		}
		
		this.resultType = Type.BOOL;
	}
	
	@Override
	public void caseAEqComparison(AEqComparison node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != rightType) {
			throw new SemanticException(node.getEq(), "operands are not from the same type");
		}

		this.resultType = Type.BOOL;
	}
	
	@Override
	public void caseANotEqComparison(ANotEqComparison node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != rightType) {
			throw new SemanticException(node.getNotEq(), "operands are not from the same type");
		}

		this.resultType = Type.BOOL;
	}
	
	@Override
	public void caseALtComparison(ALtComparison node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.INT) {
			throw new SemanticException(node.getLt(), "left operand is not a number");
		}
		if(rightType != Type.INT) {
			throw new SemanticException(node.getLt(), "right operand is not a number");
		}
		
		this.resultType = Type.BOOL;
	}
	
	@Override
	public void caseALtEqComparison(ALtEqComparison node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.INT) {
			throw new SemanticException(node.getLtEq(), "left operand is not a number");
		}
		if(rightType != Type.INT) {
			throw new SemanticException(node.getLtEq(), "right operand is not a number");
		}
		
		this.resultType = Type.BOOL;
	}
	
	@Override
	public void caseAGtComparison(AGtComparison node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.INT) {
			throw new SemanticException(node.getGt(), "left operand is not a number");
		}
		if(rightType != Type.INT) {
			throw new SemanticException(node.getGt(), "right operand is not a number");
		}
		
		this.resultType = Type.BOOL;
	}
	
	@Override
	public void caseAGtEqComparison(AGtEqComparison node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.INT) {
			throw new SemanticException(node.getGtEq(), "left operand is not a number");
		}
		if(rightType != Type.INT) {
			throw new SemanticException(node.getGtEq(), "right operand is not a number");
		}
		
		this.resultType = Type.BOOL;
	}
	
	@Override
	public void caseAAndBitwise(AAndBitwise node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.INT) {
			throw new SemanticException(node.getAnd(), "left operand is not a number");
		}
		if(rightType != Type.INT) {
			throw new SemanticException(node.getAnd(), "right operand is not a number");
		}
		
		this.resultType = Type.INT;
	}
	
	@Override
	public void caseAOrBitwise(AOrBitwise node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.INT) {
			throw new SemanticException(node.getOr(), "left operand is not a number");
		}
		if(rightType != Type.INT) {
			throw new SemanticException(node.getOr(), "right operand is not a number");
		}
		
		this.resultType = Type.INT;
	}
	
	@Override
	public void caseAXorBitwise(AXorBitwise node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.INT) {
			throw new SemanticException(node.getXor(), "left operand is not a number");
		}
		if(rightType != Type.INT) {
			throw new SemanticException(node.getXor(), "right operand is not a number");
		}
		
		this.resultType = Type.INT;
	}
	
	@Override
	public void caseAShiftLeftShift(AShiftLeftShift node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.INT) {
			throw new SemanticException(node.getShiftLeft(), "left value is not a number");
		}
		if(rightType != Type.INT) {
			throw new SemanticException(node.getShiftLeft(), "right value is not a number");
		}
		
		this.resultType = Type.INT;
	}
	
	@Override
	public void caseAShiftRightShift(AShiftRightShift node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.INT) {
			throw new SemanticException(node.getShiftRight(), "left value is not a number");
		}
		if(rightType != Type.INT) {
			throw new SemanticException(node.getShiftRight(), "right value is not a number");
		}
		
		this.resultType = Type.INT;
	}
	
	@Override
	public void caseAAddAdditiveExp(AAddAdditiveExp node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType == Type.STRING || rightType == Type.STRING) {
			this.resultType = Type.STRING;
			return;
		}
		
		if(leftType != Type.INT) {
			throw new SemanticException(node.getPlus(), "left value is not a number");
		}
		if(rightType != Type.INT) {
			throw new SemanticException(node.getPlus(), "right value is not a number");
		}
		
		this.resultType = Type.INT;
	}
	
	@Override
	public void caseASubAdditiveExp(ASubAdditiveExp node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.INT) {
			throw new SemanticException(node.getMinus(), "left value is not a number");
		}
		if(rightType != Type.INT) {
			throw new SemanticException(node.getMinus(), "right value is not a number");
		}
		
		this.resultType = Type.INT;
	}
	
	@Override
	public void caseAMulFactor(AMulFactor node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.INT) {
			throw new SemanticException(node.getStar(), "left value is not a number");
		}
		if(rightType != Type.INT) {
			throw new SemanticException(node.getStar(), "right value is not a number");
		}
		
		this.resultType = Type.INT;
	}
	
	@Override
	public void caseADivFactor(ADivFactor node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.INT) {
			throw new SemanticException(node.getSlash(), "left value is not a number");
		}
		if(rightType != Type.INT) {
			throw new SemanticException(node.getSlash(), "right value is not a number");
		}
		
		this.resultType = Type.INT;
	}
	
	@Override
	public void caseAModFactor(AModFactor node) {
		Type leftType = evalType(node.getLeft());
		Type rightType = evalType(node.getRight());
		
		if(leftType != Type.INT) {
			throw new SemanticException(node.getMod(), "left value is not a number");
		}
		if(rightType != Type.INT) {
			throw new SemanticException(node.getMod(), "right value is not a number");
		}
		
		this.resultType = Type.INT;
	}
	
	@Override
    public void caseATrueTerm(ATrueTerm node) {
        this.resultType = Type.BOOL;
    }

    @Override
    public void caseAFalseTerm(AFalseTerm node) {
        this.resultType = Type.BOOL;
    }
	
	@Override
    public void caseAStringTerm(AStringTerm node) {
        this.resultType = Type.STRING;
    }

    @Override
    public void caseAVarTerm(AVarTerm node) {
        this.resultType = this.currentVariableScope.getType(node.getIdent());
    }
	
	@Override
	public void caseAHexStrTerm(AHexStrTerm node) {
		Type type = evalType(node.getExp());
		
		if(type != Type.INT) {
			throw new SemanticException(node.getLPar(), "expression is not a number");
		}
		
		this.resultType = Type.STRING;
	}
	
	@Override
	public void caseABinStrTerm(ABinStrTerm node) {
		Type type = evalType(node.getExp());
		
		if(type != Type.INT) {
			throw new SemanticException(node.getLPar(), "expression is not a number");
		}
		
		this.resultType = Type.STRING;
	}
	
	@Override
	public void caseAPcTerm(APcTerm node) {
		this.resultType = Type.INT;
	}
	
	@Override
	public void caseAHexNumber(AHexNumber node) {
		int value = 0;
		THexadecimal token = node.getHexadecimal();
		String strNumber = token.getText();
		
		// on enlève le "$"
		String number = strNumber.substring(1);
		
		try {
			if(node.getMinus() == null) {
				value = Integer.parseInt(number, 16);
			}
			else {
				value = -Integer.parseInt(number, 16);
			}
		}
		catch(NumberFormatException e) {
			throw new SemanticException(token, "Number is invalid");
		}
		
		this.semantics.addNumber(node, value);
		this.resultType = Type.INT;
	}
	
	@Override
	public void caseABinaryNumber(ABinaryNumber node) {
		int value = 0;
		TBinary token = node.getBinary();
		String strNumber = token.getText();
		
		// on enlève le "%"
		String number = strNumber.substring(1);
		
		try {
			if(node.getMinus() == null) {
				value = Integer.parseInt(number, 2);
			}
			else {
				value = -Integer.parseInt(number, 2);
			}
		}
		catch(NumberFormatException e) {
			throw new SemanticException(token, "Number is invalid");
		}
		
		this.semantics.addNumber(node, value);
		this.resultType = Type.INT;
	}
	
	@Override
	public void caseADecimalNumber(ADecimalNumber node) {
		int value = 0;
		TDecimal token = node.getDecimal();
		String strNumber = token.getText();
		
		try {
			if(node.getMinus() == null) {
				value = Integer.parseInt(strNumber, 10);
			}
			else {
				value = -Integer.parseInt(strNumber, 10);
			}
		}
		catch(NumberFormatException e) {
			throw new SemanticException(token, "Number is invalid");
		}
		
		this.semantics.addNumber(node, value);
		this.resultType = Type.INT;
	}
}
