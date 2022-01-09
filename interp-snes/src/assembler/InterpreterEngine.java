package assembler;

import assembler.syntax.analysis.DepthFirstAdapter;
import assembler.syntax.node.*;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class InterpreterEngine  extends DepthFirstAdapter{
	
	private SemanticInfo semantics;
	private Frame currentFrame;
	
	// liste des frames utilisés lors de la dernière phase de compilation
	private Map<Node, Frame> frames;
	
	private List<Value> currentArgs;
	private Value result;
	
	// liste des instructions d'assembleur
	private OpcodeList opcodeList;
	
	// contient toutes les données binaires à écrire
	private Map<DataNode, WriteData> data;
	
	// octets de l'instruction courante
	// contient les octects d'argument en premier lieu
	// puis les octets instruction + argument ensuite
	private byte[] currentWriteData;
	
	// valeur de l'expression qui suit un label
	// ex: "$10" dans "lda label_a+$10", sinon null
	private Integer currentExtraValue;
	
	// true si l'instruction peut être écrite
	// e.g. false dans le cas de "beq label_a"
	private boolean currentIsReady;
	
	// la longeur de l'argument de l'instruction
	// soit null, 1, 2 ou 3 octets
	private Integer currentLength;
	
	// la longeur maximale de l'argument de l'instruction
	// soit 1, 2 ou 3 octets. Utilisé pour les instructions avec label et expression
	private int currentMaxLength;
	
	// la longeur maximale de l'argument de l'instruction
	// soit 1, 2 ou 3 octets. Utilisé pour les instructions avec label et expressions
	private int currentMinLength;
	
	// similaire à currentWriteData sauf pour
	// les instruction db, dw, dl et fill
	private List<Integer> bytesToWrite;
	
	// offset où doit être écrite l'instruction
	// dans le fichier binaire
	private int currentOffset;
	
	// id incrémenté à chaque appel de macro
	private int macroId = 0;
	
	// id incrémenté à chaque déclartion de label anomyme
	private int anonId = 0;
	
	// true si accFlag a été modifié au moins une fois
	private boolean setClearAccFlag = false;
	
	// true pour mode 8-bit de l'accumulateur, false pour 16-bit
	private boolean accFlag = false;
	
	// true si xyFlag a été modifié au moins une fois
	private boolean setClearXyFlag = false;
	
	// true pour mode 8-bit de X/Y, false pour 16-bit
	private boolean xyFlag = false;
	
	// le préfix d'un label (concaténation des namespaces)
	private String labelPrefix = "";
	
	public InterpreterEngine(SemanticInfo semantics, Map<DataNode, WriteData> data, Map<Node, Frame> frames, OpcodeList opcodeList) {
		this.semantics = semantics;
		this.data = data;
		this.opcodeList = opcodeList;
		this.frames = frames;
		this.currentOffset = 0;
	}
	
	private void visit(Node node) {
        if (node != null) {
            node.apply(this);
        }
    }

    private Value eval(Node node) {
        visit(node);
        return this.result;
    }
    
    // initialise les variables pour une instruction (sauf branchements)
	private void initOpFields() {
		this.currentWriteData = null;
		this.currentExtraValue = null;
		this.currentLength = null;
		this.currentMaxLength = 3;
		this.currentMinLength = 1;
		this.currentIsReady = false;
	}
	
	// initialise les variable pour les instructions de branchement (toujours 2 octets)
	private void initOpFieldsBranch(int opcodeValue) {
		byte[] temp = {(byte)opcodeValue, (byte)0};
		this.currentWriteData = temp;
		this.currentExtraValue = null;
		this.currentLength = 1;
		this.currentMaxLength = 1;
		this.currentMinLength = 1;
		this.currentIsReady = false;
	}
    
	// concatène le nom de l'instruction variable avec l'identifiant pour arguments
	// à 1, 2 ou 3 octets, ensuite va chercher la bonne instruction et concatène l'argument en binaire
	// à la valeur de l'instruction
	private void getAbsOpcodeAddWriteData(Node node, String opName) {
		String name = concatLengthToName(opName, this.currentWriteData);
		Integer opcode = this.opcodeList.getOpcode(name);
		this.currentWriteData = appendOpArgsToOpcode(opcode, this.currentWriteData);
		addWriteData(node);
	}
    
	// retourne le nom d'une instruction dépendant de sa longeur
	private String concatLengthToName(String name, byte[] data) {
		if(data.length == 1) {
			return name + Consts.BYTE;
		}
		else if(data.length == 2) {
			return name + Consts.WORD;
		}
		else {
			return name + Consts.LONG;
		}
	}
	
	// met la valeur hexadécimal de l'instruction en tête de ses arguments
	private byte[] appendOpArgsToOpcode(int value, byte[] data) {
		byte[] returnData = new byte[data.length + 1];
		returnData[0] = (byte)value;
		
		for(int i = 1; i < returnData.length; i++) {
			returnData[i] = data[i - 1];
		}
		
		return returnData;
	}
	
	// ajoute une instruction à la liste d'instructions à écrire en binaire
	private void addWriteData(Node node) {
		this.data.put(new DataNode(node, this.macroId), new WriteData(this.currentOffset,
				this.currentWriteData, this.currentIsReady,
				this.currentExtraValue));
		
		// incrémentation de l'offset d'écriture
		this.currentOffset += this.currentWriteData.length;
	}
	
	// ajout à la linked list dépendant si byte, word ou long
	// utilisé pour instruction db, dw, dl et fill
	private void addToBytesToWrite(int value) {
		this.bytesToWrite.add(value);
		if(this.currentLength  >= 2) {
			this.bytesToWrite.add(value >>> 8);
		}
		if(this.currentLength == 3) {
			this.bytesToWrite.add(value >>> 16);
		}
	}
	
	// convertis la linked list de Integer en tableau de byte
	// utilisé pour instruction db, dw, dl et fill
	private void byteToWriteToWriteData() {
		this.currentWriteData = new byte[this.bytesToWrite.size()];
		for(int i = 0; i < this.bytesToWrite.size(); i++) {
			this.currentWriteData[i] = (byte)((int)this.bytesToWrite.get(i));
		}
	}
	
	// modifie l'argument de 2 octets vers 1 octet ou celui de 1 octet vers 2 octets si nécessaire
	// dépendant du mode de l'accumulateur (8-bit ou 16-bit),
	// utilisé pour les instructions LDA_CONST et CMP_CONST
	private void modifyWriteDataFromAccFlag() {
		// si on a modifié le accFlag au moins une fois
		if(this.setClearAccFlag) {
			// si mode 8-bit
			if(this.accFlag) {
				// si on a traité l'instruction comme 16-bit
				// on ne garde que le premier octet (à condition qu'on ne
				// force pas 2 octets avec ".w")
				if(this.currentWriteData.length == 2 && !(this.currentLength != null && this.currentLength == 2)) {
					byte[] temp = { this.currentWriteData[0] };
					this.currentWriteData = temp;
				}
			}
			// si mode 16-bit
			else {
				// si on a traité l'instruction comme 8-bit
				// on ajoute un high byte à 0 (à condition qu'on ne
				// force pas 1 octets avec ".b")
				if(this.currentWriteData.length == 1 && !(this.currentLength != null && this.currentLength == 1)) {
					byte[] temp = { this.currentWriteData[0], (byte)0 };
					this.currentWriteData = temp;
				}
			}
		}
	}
	
	// modifie l'argument de 2 octets vers 1 octet ou celui de 1 octet vers 2 octets si nécessaire
	// dépendant du mode de X/Y (8-bit ou 16-bit),
	// utilisé pour l'instruction LDX_CONST
	private void modifyWriteDataFromXyFlag() {
		// si on a modifié le X/Y Flag au moins une fois
		if(this.setClearXyFlag) {
			// si mode 8-bit
			if(this.xyFlag) {
				// si on a traité l'instruction comme 16-bit
				// on ne garde que le premier octet (à condition qu'on ne
				// force pas 2 octets avec ".w")
				if(this.currentWriteData.length == 2 && !(this.currentLength != null && this.currentLength == 2)) {
					byte[] temp = { this.currentWriteData[0] };
					this.currentWriteData = temp;
				}
			}
			// si mode 16-bit
			else {
				// si on a traité l'instruction comme 8-bit
				// on ajoute un high byte à 0 (à condition qu'on ne
				// force pas 1 octets avec ".b")
				if(this.currentWriteData.length == 1 && !(this.currentLength != null && this.currentLength == 1)) {
					byte[] temp = { this.currentWriteData[0], (byte)0 };
					this.currentWriteData = temp;
				}
			}
		}		
	}
	
	@Override
	public void caseAProg(AProg node) {
		Frame frame = new Frame();
		frames.put(node, frame);
		this.currentFrame = frames.get(node);
		visit(node.getInsts());
	}
	

	@Override
    public void caseADeclInst(ADeclInst node) {
        Value value = eval(node.getExp());
        
        this.currentFrame.putVariable(node.getIdent(), value, this.currentFrame);
    }

    @Override
    public void caseAAssignInst(AAssignInst node) {
        Value value = eval(node.getExp());
        
        this.currentFrame.putVariable(node.getIdent(), value, this.currentFrame);
    }
    
    @Override
    public void caseAIfInst(AIfInst node) {
    	BoolValue value = (BoolValue)eval(node.getExp());
    	
    	if(value.getValue()) {
    		visit(node.getBody());
    	}
    }
    
    @Override
    public void caseAWhileInst(AWhileInst node) {
    	
    	while(true) {
    		BoolValue value = (BoolValue)eval(node.getExp());
    		
    		if(!value.getValue()) {
    			break;
    		}
    		
    		visit(node.getBody());
    	}
    }

	@Override
	public void caseAOrgInst(AOrgInst node) {
		int value = ((IntValue)eval(node.getExp())).getValue();
		
		// limiter à un entier positif de 24-bit sur un système 24-bit
		if(value < 0 || value > Consts.MAX_INT) {
			throw new InterpreterException(node.getLPar(),
					"org expression is out of bounds (must be between 0 and 0x" + Integer.toHexString(Consts.MAX_INT));
		}
		
		// nouvel offset d'écriture
		this.currentOffset = value;
		
		// on dit à l'assembleur de ne plus tenir compte
		// du mode de l'accumulateur et de X/Y jusqu'à
		// un nouvelle utilisation des instructions SEP ou REP
		this.setClearAccFlag = false;
		this.setClearXyFlag = false;
	}
	
	@Override
	public void caseADlInst(ADlInst node) {
		initOpFields();
		this.bytesToWrite = new LinkedList<Integer>();
		
		// 3 octets
		this.currentLength = 3;
		
		// on visite la séquence d'octets
		visit(node.getByteSeqs());
		
		// transfer de la liste au currentWriteData
		byteToWriteToWriteData();
		
		this.currentIsReady = true;
		this.bytesToWrite = null;
		addWriteData(node);
	}
	
	@Override
	public void caseADwInst(ADwInst node) {
		initOpFields();
		this.bytesToWrite = new LinkedList<Integer>();
		
		// 2 octets
		this.currentLength = 2;
		
		// on visite la séquence d'octets
		visit(node.getByteSeqs());
		
		// transfer de la liste au currentWriteData
		byteToWriteToWriteData();
		
		this.currentIsReady = true;
		this.bytesToWrite = null;
		addWriteData(node);
	}
	
	@Override
	public void caseADbInst(ADbInst node) {
		initOpFields();
		this.bytesToWrite = new LinkedList<Integer>();
		
		// 1 octet
		this.currentLength = 1;
		
		// on visite la séquence d'octets
		visit(node.getByteSeqs());
		
		// transfer de la liste au currentWriteData
		byteToWriteToWriteData();
		
		this.currentIsReady = true;
		this.bytesToWrite = null;
		addWriteData(node);
	}
	
	@Override
	public void caseANamespaceInst(ANamespaceInst node) {
		String name = "";
		
		// on ajout le nom courant au préfix courant
		if(!this.labelPrefix.equals(""))
			name = "-" + node.getIdent().getText();
		else {
			name = node.getIdent().getText();
		}
		
		this.labelPrefix += name;
		
		visit(node.getBody());
		
		// retrait du nom courant
		this.labelPrefix = this.labelPrefix.replace(name, "");
	}
	
	@Override
	public void caseAFillInst(AFillInst node) {
		initOpFields();
		
		// défault
		this.currentLength = 1;
		
		this.bytesToWrite = new LinkedList<Integer>();
		
		int bytes = ((IntValue)eval(node.getExp())).getValue();
		int numRepeat = ((IntValue)eval(node.getNumber())).getValue();
		
		// si une des deux expresion est négative
		if(bytes < 0) {
			throw new InterpreterException(node.getFill(), "value to write cannot be negative");
		}	
		if(numRepeat < 0) {
			throw new InterpreterException(node.getFill(), "number of repetition cannot be negative");
		}
		
		// mise-à-jour de la longeur dépendant
		// de la valeur de l'expression
		if(bytes > 0xFFFF) {
			this.currentLength = 3;
		}
		else if(bytes > 0xFF) {
			this.currentLength = 2;
		}
		
		for(int i = 0; i < numRepeat; i++) {
			// ajout à la linked list
			addToBytesToWrite(bytes);
		}
		
		// transfer de la linked list liste au currentWriteData
		byteToWriteToWriteData();
		
		this.currentIsReady = true;
		this.bytesToWrite = null;
		addWriteData(node);
	}
	
	@Override
	public void caseAMacroCallInst(AMacroCallInst node) {
		
		String macroName = node.getIdent().getText();
        MacroInfo macroInfo = this.semantics.getMacroInfo(macroName);

        Frame frame = new Frame(this.currentFrame, macroInfo, node.getIdent());

        // sauvegarde la liste courante
        List<Value> previousArgs = this.currentArgs;
        this.currentArgs = new LinkedList<>();

        // calcul des arguments
        visit(node.getArgs());
        List<Value> args = this.currentArgs;

        // remise en place de la liste courante
        this.currentArgs = previousArgs;

        macroInfo.assignArgs(args, frame, node.getLPar());

        // noter la localisation courante
        this.currentFrame.setLocation(node.getLPar());

        // Exécuter le corps de la fonction
        this.currentFrame = frame;
        this.frames.put(node, this.currentFrame);
        
        this.macroId++;

        visit(macroInfo.getMacroBody());

        this.currentFrame = frame.getParentFrame();

        // effacer la localisation courante
        this.currentFrame.setLocation(null);
	}
	
	@Override
	public void caseAPrintInst(APrintInst node) {
		Value value = eval(node.getExp());
		
		System.out.print(value.toString());
	}
	
	@Override
	public void caseAPrintLnInst(APrintLnInst node) {
		if(node.getExp() == null) {
			System.out.println();
		}
		else {
			Value value = eval(node.getExp());
			System.out.println(value.toString());
		}
	}
	
    @Override
    public void caseAArg(AArg node) {
        this.currentArgs.add(eval(node.getExp()));
    }
	
	@Override
	public void caseANumByteSeq(ANumByteSeq node) {
		int value = ((IntValue)eval(node.getArgNum())).getValue();
		
		// ajout à la linked list
		addToBytesToWrite(value);
	}
	
	@Override
	public void caseAExpByteSeq(AExpByteSeq node) {
		int value = ((IntValue)eval(node.getExp())).getValue();
		
		// pas de négatif
		if(value < 0) {
			throw new InterpreterException(node.getLCbr(),
					"expression cannot be smaller than 0");
		}
		
		// ajout à la linked list
		addToBytesToWrite(value);
	}
	
	@Override
	public void caseABeqOpcode(ABeqOpcode node) {
		visit(node.getAnonLabel());
		Integer opcode = this.opcodeList.getOpcode(Consts.BEQ);
		initOpFieldsBranch(opcode);
		addWriteData(node);
	}
	
	@Override
	public void caseABneOpcode(ABneOpcode node) {
		visit(node.getAnonLabel());
		Integer opcode = this.opcodeList.getOpcode(Consts.BNE);
		initOpFieldsBranch(opcode);
		addWriteData(node);
	}
	
	@Override
	public void caseACmpAbsOpcode(ACmpAbsOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		this.currentMaxLength = 3;
		
		// visite de la longeur d'instruction optionelle
		if(node.getOpLength() != null) {
			visit(node.getOpLength());
		}
		
		// visite de l'argument
		visit(node.getOpArg());
		
		getAbsOpcodeAddWriteData(node, Consts.CMP_ABS);
	}
	
	@Override
	public void caseACmpConstOpcode(ACmpConstOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		this.currentMaxLength = 2;
		
		// visite de la longeur d'instruction optionelle
		if(node.getOpLength() != null) {
			visit(node.getOpLength());
		}
		
		// visite de l'argument
		visit(node.getOpArg());
		
		modifyWriteDataFromAccFlag();
		
		// on met la valeur de l'instruction en tête de l'argument, on met
		// à jour le writeData et on l'Ajoute à la liste d'instruction à écrire
		Integer opcode = this.opcodeList.getOpcode(Consts.CMP_CONST);
		this.currentWriteData = appendOpArgsToOpcode(opcode, this.currentWriteData);
		addWriteData(node);
	}
	
	@Override
	public void caseADecAccOpcode(ADecAccOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		
		// écriture de 0x3A dans le writeData
		Integer opcode = this.opcodeList.getOpcode(Consts.DEC_ACC);
		byte[] temp = {(byte)((int)opcode)};
		this.currentWriteData = temp;
		
		this.currentIsReady = true;
		addWriteData(node);
	}
	
	@Override
	public void caseADecAbsOpcode(ADecAbsOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		this.currentMaxLength = 2;
		
		// visite de la longeur d'instruction optionelle
		if(node.getOpLength() != null) {
			visit(node.getOpLength());
		}
		
		// visite de l'argument
		visit(node.getOpArg());
		
		getAbsOpcodeAddWriteData(node, Consts.DEC_ABS);
	}
	
	@Override
	public void caseAIncAccOpcode(AIncAccOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		
		// écriture de 0x1A dans le writeData
		Integer opcode = this.opcodeList.getOpcode(Consts.INC_ACC);
		byte[] temp = {(byte)((int)opcode)};
		this.currentWriteData = temp;
		
		this.currentIsReady = true;
		addWriteData(node);
	}
	
	@Override
	public void caseAIncAbsOpcode(AIncAbsOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		this.currentMaxLength = 2;
		
		// visite de la longeur d'instruction optionelle
		if(node.getOpLength() != null) {
			visit(node.getOpLength());
		}
		
		// visite de l'argument
		visit(node.getOpArg());
		
		getAbsOpcodeAddWriteData(node, Consts.INC_ABS);
	}
	
	@Override
	public void caseAJmpOpcode(AJmpOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		
		// instruction de minimum 3 octets
		this.currentMinLength = 2;
		
		// visite de l'argument
		visit(node.getOpArg());
		
		getAbsOpcodeAddWriteData(node, Consts.JMP);
	}
	
	@Override
	public void caseALdaAbsOpcode(ALdaAbsOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		
		// visite de la longeur d'instruction optionelle
		if(node.getOpLength() != null) {
			visit(node.getOpLength());
		}
		
		// visite de l'argument
		visit(node.getOpArg());
		
		getAbsOpcodeAddWriteData(node, Consts.LDA_ABS);
	}
	
	@Override
	public void caseALdaConstOpcode(ALdaConstOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		this.currentMaxLength = 2;
		
		// visite de la longeur d'instruction optionelle
		if(node.getOpLength() != null) {
			visit(node.getOpLength());
		}
		
		// visite de l'argument
		visit(node.getOpArg());
		
		// on modifie la longeur de l'argument dépendant si l'accumulateur est en mode 8-bit ou 16-bit
		modifyWriteDataFromAccFlag();
		
		// on met la valeur de l'instruction en tête de l'argument, on met
		// à jour le writeData et on l'ajoute à la liste d'instruction à écrire
		Integer opcode = this.opcodeList.getOpcode(Consts.LDA_CONST);
		this.currentWriteData = appendOpArgsToOpcode(opcode, this.currentWriteData);
		addWriteData(node);
	}
	
	@Override
	public void caseALdaIndXOpcode(ALdaIndXOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		
		// visite de la longeur d'instruction optionelle
		if(node.getOpLength() != null) {
			visit(node.getOpLength());
		}
		
		// visite de l'argument
		visit(node.getOpArg());
		
		getAbsOpcodeAddWriteData(node, Consts.LDA_IND_X);
	}
	
	@Override
	public void caseALdxAbsOpcode(ALdxAbsOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		this.currentMaxLength = 2;
		
		// visite de la longeur d'instruction optionelle
		if(node.getOpLength() != null) {
			visit(node.getOpLength());
		}
		
		// visite de l'argument
		visit(node.getOpArg());
		
		getAbsOpcodeAddWriteData(node, Consts.LDX_ABS);
	}
	
	@Override
	public void caseALdxConstOpcode(ALdxConstOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		this.currentMaxLength = 2;
		
		// visite de la longeur d'instruction optionelle
		if(node.getOpLength() != null) {
			visit(node.getOpLength());
		}
		
		// visite de l'argument
		visit(node.getOpArg());
		
		// on modifie la longeur de l'argument dépendant si X/Y est en mode 8-bit ou 16-bit
		modifyWriteDataFromXyFlag();
		
		// on met la valeur de l'instruction en tête de l'argument, on met
		// à jour le writeData et on l'ajoute à la liste d'instruction à écrire
		Integer opcode = this.opcodeList.getOpcode(Consts.LDX_CONST);
		this.currentWriteData = appendOpArgsToOpcode(opcode, this.currentWriteData);
		addWriteData(node);
	}
	
	@Override
	public void caseAMvnOpcode(AMvnOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		this.currentMaxLength = 1;
		
		// visite de l'argument à gauche
		visit(node.getSource());
		
		// on sauvegarde l'argument de gauche
		byte source = this.currentWriteData[0];
		
		// visite de l'argument à droite
		visit(node.getDest());
		
		// on met les deux ensemble
		byte[] sourceDest = { source, this.currentWriteData[0] };
		this.currentWriteData  = sourceDest;
		
		// on met la valeur de l'instruction en tête de l'argument, on met
		// à jour le writeData et on l'ajoute à la liste d'instruction à écrire
		Integer opcode = this.opcodeList.getOpcode(Consts.MVN);
		this.currentWriteData = appendOpArgsToOpcode(opcode, this.currentWriteData);
		addWriteData(node);
	}
	
	@Override
	public void caseAMvpOpcode(AMvpOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		this.currentMaxLength = 1;
		
		// visite de l'argument à gauche
		visit(node.getSource());
		
		// on sauvegarde l'argument de gauche
		byte source = this.currentWriteData[0];
		
		// visite de l'argument à droite
		visit(node.getDest());
		
		// on met les deux ensemble
		byte[] sourceDest = { source, this.currentWriteData[0] };
		this.currentWriteData  = sourceDest;
		
		// on met la valeur de l'instruction en tête de l'argument, on met
		// à jour le writeData et on l'ajoute à la liste d'instruction à écrire
		Integer opcode = this.opcodeList.getOpcode(Consts.MVP);
		this.currentWriteData = appendOpArgsToOpcode(opcode, this.currentWriteData);
		addWriteData(node);
	}

	@Override
	public void caseAStaAbsOpcode(AStaAbsOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		
		// visite de la longeur d'instruction optionelle
		if(node.getOpLength() != null) {
			visit(node.getOpLength());
		}
		
		// visite de l'argument
		visit(node.getOpArg());
		
		getAbsOpcodeAddWriteData(node, Consts.STA_ABS);
	}
	
	@Override
	public void caseAStaIndXOpcode(AStaIndXOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		
		// visite de la longeur d'instruction optionelle
		if(node.getOpLength() != null) {
			visit(node.getOpLength());
		}
		
		// visite de l'argument
		visit(node.getOpArg());
		
		getAbsOpcodeAddWriteData(node, Consts.STA_IND_X);
	}
	
	@Override
	public void caseARtsOpcode(ARtsOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		
		// écriture de 0x60 dans le writeData
		Integer opcode = this.opcodeList.getOpcode(Consts.RTS);
		byte[] temp = {(byte)((int)opcode)};
		this.currentWriteData = temp;
		
		this.currentIsReady = true;
		addWriteData(node);
	}
	
	@Override
	public void caseASepOpcode(ASepOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		
		// instruction fixe à 2 octets
		this.currentLength = 1;	
		
		// visite de l'argument
		visit(node.getOpArg());
		
		// récupération de l'argument
		int value = Byte.toUnsignedInt(this.currentWriteData[0]);
		
		// mode 8-bit pour l'accumulateur
		if((value & 0x20) == 0x20) {
			this.accFlag = true;
			this.setClearAccFlag = true;
		}
		
		// mode 8-bit pour X/Y
		if((value & 0x10) == 0x10) {
			this.xyFlag = true;
			this.setClearXyFlag = true;
		}
		
		// on met la valeur de l'instruction en tête de l'argument, on met
		// à jour le writeData et on l'ajoute à la liste d'instruction à écrire
		Integer opcode = this.opcodeList.getOpcode(Consts.SEP);
		this.currentWriteData = appendOpArgsToOpcode(opcode, this.currentWriteData);
		addWriteData(node);	
	}
	
	@Override
	public void caseARepOpcode(ARepOpcode node) {
		initOpFields();
		visit(node.getAnonLabel());
		
		// instruction fixe à 2 octets
		this.currentLength = 1;	
		
		// visite de l'argument
		visit(node.getOpArg());
		
		// récupération de l'argument
		int value = Byte.toUnsignedInt(this.currentWriteData[0]);
		
		// mode 16-bit pour l'accumulateur
		if((value & 0x20) == 0x20) {
			this.accFlag = false;
			this.setClearAccFlag = true;
		}
		
		// mode 16-bit pour X/Y
		if((value & 0x10) == 0x10) {
			this.xyFlag = false;
			this.setClearXyFlag = true;
		}
		
		// on met la valeur de l'instruction en tête de l'argument, on met
		// à jour le writeData et on l'ajoute à la liste d'instruction à écrire
		Integer opcode = this.opcodeList.getOpcode(Consts.REP);
		this.currentWriteData = appendOpArgsToOpcode(opcode, this.currentWriteData);
		addWriteData(node);	
	}
	
	@Override
	public void caseALabelInst(ALabelInst node) {
		// ajout d'un label au frame courant
		String name = node.getIdent().getText();	 
		this.currentFrame.addLabel(name, this.labelPrefix, this.macroId, this.currentOffset);
	}
	
	@Override
	public void caseAPlusAnonLabel(APlusAnonLabel node) {
		// ajout d'un label anonyme positif au frame courant
		this.currentFrame.addLabel("+" + this.anonId++, "", this.macroId, this.currentOffset);
	}
	
	@Override
	public void caseAMinusAnonLabel(AMinusAnonLabel node) {
		// ajout d'un label anonyme négatif au frame courant
		this.currentFrame.addLabel("-" + this.anonId++, "", this.macroId, this.currentOffset);
	}
	
	@Override
	public void caseANumOpArg(ANumOpArg node) {
		// cas d'un argument étant un nombre (e.g. $1234)
		OpcodeArg arg = this.semantics.getOpcodeArg(node.getArgNum());

		// si on a une longeur d'argument fixe
		// on l'utilise
		if(this.currentLength != null) {
			arg.setNumByte(this.currentLength);
		}
		
		this.currentWriteData = arg.getBytes();
		this.currentIsReady = true;
	}
	
	@Override
	public void caseALabelOpArg(ALabelOpArg node) {
		// cas d'un argument étant un label
		
		// si la longeur de l'argument est spécifiée
		// avec .b, .w or .l on utilise cette longeur
		// sinon on uilise la longeur maximale permise par l'instruction
		if(this.currentLength != null) {
			this.currentWriteData = new byte[this.currentLength];
		}
		else {
			this.currentWriteData = new byte[this.currentMaxLength];
		}
		
		if(node.getExtraArg() != null) {
			visit(node.getExtraArg());
		}
	}
	
	@Override
	public void caseAExpOpArg(AExpOpArg node) {
		// cas où l'argument est une expession à évaluer
		IntValue value = (IntValue)eval(node.getExp());		
		int numBytes = 0;
		
		// négatif non permis pour un argument étant une expression
		if(value.getValue() < 0 || value.getValue() > Consts.MAX_INT) {
			throw new InterpreterException(node.getLCbr(),
					"expression cannot be smaller than 0 or bigger than $" + Integer.toHexString(Consts.MAX_INT).toUpperCase());
		}
		
		// si la longeur a été spécifiée avec .b, .w or .l
		// on prend cette longeur
		if(this.currentLength != null) {
			numBytes = this.currentLength;
		} 
		else {
			// sinon on prend la longeur de l'expression évaluée
			String strNumber = Integer.toHexString(value.getValue());
			numBytes = strNumber.length() <= 2 ? 1: strNumber.length() <= 4 ? 2: 3; 
		}
		
		// si la longeur est plus grande que le maximum
		// que l'instruction permet on utile le maximum.
		// on aurait pu aussi lancer une exception mais par choix
		// de conception pour ne pas tracasser l'utilisateur avec
		// la longeur d'une expression évalué on enlève ce qu'il faut
		// de l'int 24-bit ou 16-bit pour le ramener à 16-bit ou 8-bit
		// si nécessaire. Même chose si la longeur est plus petite que le minimum
		// on ajoutera un ou des 00 en tête d'argument.
		if(numBytes > this.currentMaxLength) {
			numBytes = this.currentMaxLength;
		}	
		if(numBytes < this.currentMinLength) {
			numBytes = this.currentMinLength;
		}
		
		OpcodeArg arg = new OpcodeArg(value.getValue(), numBytes);
		this.currentWriteData = arg.getBytes();
		this.currentIsReady = true;
	}

	@Override
	public void caseAHexArgNum(AHexArgNum node) {
		// argument hexadécimal (e.g. $1234)
		int value = this.semantics.getOpcodeArg(node).getValue();
		
		this.result = new IntValue(value);
	}
	
	@Override
	public void caseABinaryArgNum(ABinaryArgNum node) {
		// argument binaire (e.g. %1234)
		int value = this.semantics.getOpcodeArg(node).getValue();
		
		this.result = new IntValue(value);
	}
	
	@Override
	public void caseADecimalArgNum(ADecimalArgNum node) {
		// argument décimal
		int value = this.semantics.getOpcodeArg(node).getValue();
		
		this.result = new IntValue(value);
	}
	
	@Override
	public void caseAAddExtraArg(AAddExtraArg node) {
		// argument additionel positif (e.g. "16" dans "LDA label_a+16")
		IntValue value = (IntValue)eval(node.getExtraExp());
		
		this.currentExtraValue = (Integer)value.getValue();
	}
	
	@Override
	public void caseASubExtraArg(ASubExtraArg node) {
		// argument additionel négatif (e.g. "16" dans "LDA label_a-16")
		IntValue value = (IntValue)eval(node.getExtraExp());
		
		this.currentExtraValue = -(Integer)value.getValue();
	}
	
	@Override
	public void caseAByteOpLength(AByteOpLength node) {
		// e.g. ".b" dans "LDA.b $1000"
		this.currentLength = 1;
	}
	
	@Override
	public void caseAWordOpLength(AWordOpLength node) {
		// e.g. ".w" dans "LDA.b $10"
		this.currentLength = 2;
	}
	
	@Override
	public void caseALongOpLength(ALongOpLength node) {
		// e.g. ".l" dans "LDA.l $1000"
		this.currentLength = 3;
	}
	
	@Override
	public void caseANotNegation(ANotNegation node) {
		BoolValue value = (BoolValue)eval(node.getExp());
		
		this.result = new BoolValue(!value.getValue());
	}
	
	@Override
	public void caseAAndLogicalOp(AAndLogicalOp node) {
		BoolValue leftValue = (BoolValue)eval(node.getLeft());
		BoolValue rightValue = (BoolValue)eval(node.getRight());
		
		this.result = new BoolValue(leftValue.getValue() && rightValue.getValue());
	}
	
	@Override
	public void caseAOrLogicalOp(AOrLogicalOp node) {
		BoolValue leftValue = (BoolValue)eval(node.getLeft());
		BoolValue rightValue = (BoolValue)eval(node.getRight());
		
		this.result = new BoolValue(leftValue.getValue() || rightValue.getValue());
	}
	
	@Override
	public void caseAEqComparison(AEqComparison node) {
        Value leftValue = eval(node.getLeft());
        Value rightValue = eval(node.getRight());

        if (leftValue instanceof StringValue) {
            this.result = new BoolValue(((StringValue)leftValue).getValue().equals(((StringValue)rightValue).getValue()));
        }
        else if (leftValue instanceof IntValue) {
            this.result = new BoolValue(((IntValue)leftValue).getValue() == ((IntValue)rightValue).getValue());
        }
        else {
            // booléens
            this.result = new BoolValue(((BoolValue)leftValue).getValue() == ((BoolValue)rightValue).getValue());
        }
	}
	
	@Override
	public void caseANotEqComparison(ANotEqComparison node) {
		Value leftValue = eval(node.getLeft());
        Value rightValue = eval(node.getRight());

        if (leftValue instanceof StringValue) {
            this.result = new BoolValue(!((StringValue)leftValue).getValue().equals(((StringValue)rightValue).getValue()));
        }
        else if (leftValue instanceof IntValue) {
            this.result = new BoolValue(((IntValue)leftValue).getValue() != ((IntValue)rightValue).getValue());
        }
        else {
            // booléens
            this.result = new BoolValue(((BoolValue)leftValue).getValue() != ((BoolValue)rightValue).getValue());
        }
	}
	
	@Override
	public void caseALtComparison(ALtComparison node) {
		IntValue leftValue = (IntValue)eval(node.getLeft());
		IntValue rightValue = (IntValue)eval(node.getRight());
		
		this.result = new BoolValue(leftValue.getValue() < rightValue.getValue());
	}
	
	@Override
	public void caseALtEqComparison(ALtEqComparison node) {
		IntValue leftValue = (IntValue)eval(node.getLeft());
		IntValue rightValue = (IntValue)eval(node.getRight());
		
		this.result = new BoolValue(leftValue.getValue() <= rightValue.getValue());
	}
	
	@Override
	public void caseAGtComparison(AGtComparison node) {
		IntValue leftValue = (IntValue)eval(node.getLeft());
		IntValue rightValue = (IntValue)eval(node.getRight());
		
		this.result = new BoolValue(leftValue.getValue() > rightValue.getValue());
	}
	
	@Override
	public void caseAGtEqComparison(AGtEqComparison node) {
		IntValue leftValue = (IntValue)eval(node.getLeft());
		IntValue rightValue = (IntValue)eval(node.getRight());
		
		this.result = new BoolValue(leftValue.getValue() >= rightValue.getValue());
	}
	
	@Override
	public void caseAAndBitwise(AAndBitwise node) {
		IntValue leftValue = (IntValue)eval(node.getLeft());
		IntValue rightValue = (IntValue)eval(node.getRight());
		
		this.result = new IntValue(leftValue.getValue() & rightValue.getValue());
	}
	
	@Override
	public void caseAOrBitwise(AOrBitwise node) {
		IntValue leftValue = (IntValue)eval(node.getLeft());
		IntValue rightValue = (IntValue)eval(node.getRight());
		
		this.result = new IntValue(leftValue.getValue() | rightValue.getValue());
	}
	
	@Override
	public void caseAXorBitwise(AXorBitwise node) {
		IntValue leftValue = (IntValue)eval(node.getLeft());
		IntValue rightValue = (IntValue)eval(node.getRight());
		
		this.result = new IntValue(leftValue.getValue() ^ rightValue.getValue());
	}
	
	@Override
	public void caseAShiftLeftShift(AShiftLeftShift node) {
		IntValue leftValue = (IntValue)eval(node.getLeft());
		IntValue rightValue = (IntValue)eval(node.getRight());
		
		this.result = new IntValue(leftValue.getValue() << rightValue.getValue());
	}
	
	@Override
	public void caseAShiftRightShift(AShiftRightShift node) {
		IntValue leftValue = (IntValue)eval(node.getLeft());
		IntValue rightValue = (IntValue)eval(node.getRight());
		
		this.result = new IntValue(leftValue.getValue() >> rightValue.getValue());
	}

	@Override
	public void caseAAddAdditiveExp(AAddAdditiveExp node) {
		Value leftValue = eval(node.getLeft());
		Value rightValue = eval(node.getRight());
		if(leftValue instanceof StringValue || rightValue instanceof StringValue) {
			this.result = new StringValue(leftValue.toString() + rightValue.toString());
		}	
		else {
			this.result = new IntValue(((IntValue)leftValue).getValue() + ((IntValue)rightValue).getValue());
		}
	}
	
	@Override
	public void caseASubAdditiveExp(ASubAdditiveExp node) {
		IntValue leftValue = (IntValue)eval(node.getLeft());
		IntValue rightValue = (IntValue)eval(node.getRight());
		
		this.result = new IntValue(leftValue.getValue() - rightValue.getValue());
	}

	@Override
	public void caseAMulFactor(AMulFactor node) {
		IntValue leftValue = (IntValue)eval(node.getLeft());
		IntValue rightValue = (IntValue)eval(node.getRight());
		
		this.result = new IntValue(leftValue.getValue() * rightValue.getValue());
	}
	
	@Override
	public void caseADivFactor(ADivFactor node) {
		IntValue leftValue = (IntValue)eval(node.getLeft());
		IntValue rightValue = (IntValue)eval(node.getRight());
		
		this.result = new IntValue(leftValue.getValue() / rightValue.getValue());
	}
	
	@Override
	public void caseAModFactor(AModFactor node) {
		IntValue leftValue = (IntValue)eval(node.getLeft());
		IntValue rightValue = (IntValue)eval(node.getRight());
		
		this.result = new IntValue(leftValue.getValue() % rightValue.getValue());
	}

	@Override
    public void caseATrueTerm(ATrueTerm node) {
        this.result = new BoolValue(true);
    }

    @Override
    public void caseAFalseTerm(AFalseTerm node) {
        this.result = new BoolValue(false);
    }
	
	@Override
    public void caseAStringTerm(AStringTerm node) {
        String string = node.getStr().getText();
        // on enlève les doubles guillements
        string = string.substring(1, string.length() - 1);
        this.result = new StringValue(string);
    }
	
    @Override
    public void caseAVarTerm(AVarTerm node) {
    	this.result = this.currentFrame.getVariable(node.getIdent(), this.currentFrame);
    }
    
    @Override
    public void caseAHexStrTerm(AHexStrTerm node) {
    	// e.g. hex(16)
    	IntValue value = (IntValue)eval(node.getExp());
    	String hexStr = Integer.toHexString(value.getValue());
    	
    	// on pad avec un 0 si nécessaire pour toujours avoir un multiple de 2
    	// (e.g. "$04" comparativement à "$4")
    	hexStr = hexStr.length() % 2 != 0 ? "$0" + hexStr: "$" + hexStr;
    	
    	this.result = new StringValue(hexStr);
    }
    
    @Override
    public void caseABinStrTerm(ABinStrTerm node) {
    	// e.g. bin(16)
    	IntValue value = (IntValue)eval(node.getExp());
    	String binStr = Integer.toBinaryString(value.getValue());
    	
    	// on pad avec des 0 si nécessaire pour toujours avoir un multiple de 8
    	// (e.g. "%00001000" comparativement à "%1000")
    	for(int i = 0; i < binStr.length() % 8; i++) {
    		binStr = "0" + binStr;
    	}
    	binStr = "%" + binStr;
    	
    	this.result = new StringValue(binStr);
    }
		
    @Override
    public void caseAPcTerm(APcTerm node) {
    	// e.g. pc()
    	this.result = new IntValue(this.currentOffset);
    }
	
	@Override
	public void caseAHexNumber(AHexNumber node) {
		int value = this.semantics.getNumber(node);
		this.result = new IntValue(value);
	}
	
	@Override
	public void caseABinaryNumber(ABinaryNumber node) {
		int value = this.semantics.getNumber(node);
		this.result = new IntValue(value);
	}
	
	@Override
	public void caseADecimalNumber(ADecimalNumber node) {
		int value = this.semantics.getNumber(node);
		this.result = new IntValue(value);
	}
}
