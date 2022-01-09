package assembler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Map;

import assembler.syntax.analysis.DepthFirstAdapter;
import assembler.syntax.node.*;

public class CompilationEngine  extends DepthFirstAdapter{
	
	private SemanticInfo semantics;
	
	// liste d'instruction à écrire dans le fichier binaire
	private Map<DataNode, WriteData> data;
	
	private Map<Node, Frame> frames;
	private Frame currentFrame;
	private Frame mainFrame;
	private String filename;
	private int macroId = 0;
	private String labelPrefix = "";
	
	public CompilationEngine(SemanticInfo semantics, Map<DataNode, WriteData> data, Map<Node, Frame> frames, String filename) {
		this.semantics = semantics;
		this.data = data;
		this.frames = frames;
		this.filename = filename;
	}
	
	private void visit(Node node) {
        if (node != null) {
            node.apply(this);
        }
    }
	
	// calcul la valeur hexadécimal du branchement et met à jour le write data
	private void changeBranchWriteData(WriteData writeData, int labelOffset, Token token) {
		int difference = 0;
		
		// on va chercher le offset du branch
		int branchOffset = writeData.getOffset();
		
		// branchement négatif (vers l'arrière)
		if(labelOffset <= branchOffset) {
			difference = 0xFE - (branchOffset - labelOffset);
			
			// branchement trop long
			if(difference < 0x80) {
				throw new InterpreterException(token, "negative branching exceed limit");
			}
		}
		// branchement positif (vers l'avant)
		else {
			difference = (labelOffset - branchOffset) - 2;
			
			// branchement trop long
			if(difference > 0x7F) {
				throw new InterpreterException(token, "positive branching exceed limit");
			}
		}
		
		// on met à jour le write data avec le branchement
		byte[] newData = writeData.getData();
		newData[1] = (byte)difference;
		writeData.setData(newData);
		
		// les octets sont prêts à être écris dans le fichier
		writeData.setReady(true);
	}
	
	// calcu la valeur du label et met à jour le write data de l'instruction
	private void changeAbsWriteData(Token token, WriteData writeData, String name) {
		
		// on va cherche le offset du label
		Integer labelOffset = getLabelOffset(name);
		
		// si il y a une expression à additionner on l'additionne
		if(writeData.getExtraValue() != null) {
			labelOffset += writeData.getExtraValue();
			if(labelOffset < 0 || labelOffset > 0xFFFFFF) {
				throw new InterpreterException(token, "label offset is out of bound");
			}
		}
		
		byte[] oldData = writeData.getData();
		byte[] newData = new byte[oldData.length];
		
		// l'octet d'instruction ne change pas
		newData[0] = oldData[0];
		
		// nouvel argument qui remplace l'ancienne suite de 00
		byte[] offset = new byte[] {
				(byte)((int)labelOffset),
				(byte)((int)labelOffset >>> 8),
				(byte)((int)labelOffset >>> 16)
		};
		
		// copier dans le nouvel write data
		for(int i = 1; i < newData.length; i++) {
			newData[i] = offset[i - 1];
		}
		writeData.setData(newData);
		
		// les octets sont prêts à être écris dans le fichier
		writeData.setReady(true);
	}

	// Trouve le bon label et retourne son offset, si le frame courant ne l'a pas on va voir dans le main
	private Integer getLabelOffset(String name) {
		Integer labelOffset = this.currentFrame.getLabelOffset(name, this.labelPrefix, this.macroId);
		if(labelOffset == null) {
			labelOffset = this.mainFrame.getLabelOffset(name);
		}		
		return labelOffset;
	}
	
	// retourne la node de l'instruction courante
	private WriteData getWriteData(Node node) {
		DataNode currentDataNode = new DataNode(node, this.macroId);
		for(DataNode dataNode: this.data.keySet()) {
			if(dataNode.equals(currentDataNode)) {
				return this.data.get(dataNode);
			}
		}
		return null;
	}
	
	// écris les données dans le fichier binaires
	private void writeBinaryToFile() {
		if(this.data.size() > 0) {
			
			// création du dossier s'il n'existe pas
			String folder = "results/";
			File dir = new File(folder);
			
			if(!dir.exists()) {
				dir.mkdir();
			}
			
			try {
				String output = folder + this.filename + ".bin";
				RandomAccessFile raf = new RandomAccessFile(output, "rw");
				for(Map.Entry<DataNode, WriteData> entry : this.data.entrySet()) {
					WriteData writeData = entry.getValue();
					
					// le offset où on écrit
					int offset = writeData.getOffset();
					
					// les octets à écrire
					byte[] bytes = writeData.getData();
					
					// ne devrait jamais se produire
					if(!writeData.isReady()) {
						String strOffset = Integer.toHexString(offset);
						throw new CompilationException("Data at offset $" + strOffset + "is not ready to write");
					}
					
					// écriture des octets
					raf.seek(offset);
					raf.write(bytes, 0, bytes.length);
				}
			} catch (IOException e) {
				throw new CompilationException("Error writing binary file");
			}
		}
	}
	
	private void writeBinaryToText() {
		String nl = "\r\n";
		if(this.data.size() > 0) {
			
			// création du dossier s'il n'existe pas
			String folder = "results/";
			File dir = new File(folder);
			
			if(!dir.exists()) {
				dir.mkdir();
			}
			
			try {
				String output = folder + this.filename + ".txt";
				FileWriter fileWriter = new FileWriter(output);
				
				// écriture du header du fichier
				String headerA = "Program " + this.filename + nl;
				String headerB = "--------------------" + nl;
				fileWriter.write(headerA);
				fileWriter.write(headerB);
				System.out.println();
				System.out.print(headerA);
				System.out.print(headerB);
				
				for(Map.Entry<DataNode, WriteData> entry : this.data.entrySet()) {
					WriteData writeData = entry.getValue();
					
					// le offset où on écrit
					int offset = writeData.getOffset();
					
					// offset version texte
					String strOffset = Integer.toHexString(offset).toUpperCase();
					strOffset = strOffset.length() % 2 != 0 ? "0" + strOffset: strOffset;
					if(strOffset.length() == 2) {
						strOffset = "0000" + strOffset;
					}
					else if(strOffset.length() == 4) {
						strOffset = "00" + strOffset;
					}
					strOffset += ": ";
					
					// les octets à écrire
					byte[] bytes = writeData.getData();
					
					// octets version texte
					String strBytes = "";
					for(int i = 0; i < bytes.length; i++) {
						String strByte = Integer.toHexString(Byte.toUnsignedInt(bytes[i])).toUpperCase();
						strByte = strByte.length() % 2 != 0 ? "0" + strByte: strByte;
						
						if(i < bytes.length - 1) {
							strByte += " ";
						}
						
						strBytes += strByte;
					}
					
					// ne devrait jamais se produire
					if(!writeData.isReady()) {
						throw new CompilationException("Data at offset " + strOffset + "is not ready to write");
					}
					
					// écriture de l'instruction
					String line = strOffset + strBytes + nl;
					fileWriter.write(line);
					System.out.print(line);				
				}
				fileWriter.close();
			} catch (IOException e) {
				throw new CompilationException("Error writing binary file");
			}
		}
	}

	@Override
	public void caseAProg(AProg node) {
		this.currentFrame = frames.get(node);
		this.mainFrame = frames.get(node);
		visit(node.getInsts());
		
		// écrit le fichier binaire
		writeBinaryToFile();
		
		// écrit le fichier texte et dans la console
		writeBinaryToText();
	}
	
	@Override
	public void caseAMacroCallInst(AMacroCallInst node) {
		
		String macroName = node.getIdent().getText();
        MacroInfo macroInfo = this.semantics.getMacroInfo(macroName);
        
		this.currentFrame = frames.get(node);
		
		this.macroId++;

        visit(macroInfo.getMacroBody());

        this.currentFrame = this.currentFrame.getParentFrame();
	}
	
	@Override
	public void caseANamespaceInst(ANamespaceInst node) {
		String name = "";
		
		// concaténation du préfix courant
		if(!this.labelPrefix.equals(""))
			name = "-" + node.getIdent().getText();
		else {
			name = node.getIdent().getText();
		}
		
		this.labelPrefix += name;
		
		visit(node.getBody());
		
		// retrait du préfix courant
		this.labelPrefix = this.labelPrefix.replace(name, "");
	}
	
	@Override
	public void caseABeqOpcode(ABeqOpcode node) {
		WriteData writeData = getWriteData(node);
		int labelOffset = -1;
		
		if(writeData != null) {
			if(node.getBranchType() instanceof AIdentBranchType) {	
				// nom du label
				String labelName = ((AIdentBranchType)node.getBranchType()).getIdent().getText();
				
				// on va cherche le offset du label
				labelOffset = getLabelOffset(labelName);
			}
			else if(node.getBranchType() instanceof APlusBranchType) {
				// on va cherche le offset du label
				labelOffset = this.currentFrame.getAnonLabelOffset(writeData.getOffset(), this.macroId, "+", node.getBeq());
			}
			else {
				// on va cherche le offset du label
				labelOffset = this.currentFrame.getAnonLabelOffset(writeData.getOffset(), this.macroId, "-", node.getBeq());
			}
			
			// on met à jour le write data avec la longeur du branchement
			changeBranchWriteData(writeData, labelOffset, node.getBeq());		
		}
	}

	@Override
	public void caseABneOpcode(ABneOpcode node) {
		WriteData writeData = getWriteData(node);
		int labelOffset = -1;
		
		if(writeData != null) {
			if(node.getBranchType() instanceof AIdentBranchType) {	
				// nom du label
				String labelName = ((AIdentBranchType)node.getBranchType()).getIdent().getText();
				
				// on va cherche le offset du label
				labelOffset = getLabelOffset(labelName);
			}
			else if(node.getBranchType() instanceof APlusBranchType) {
				// on va cherche le offset du label
				labelOffset = this.currentFrame.getAnonLabelOffset(writeData.getOffset(), this.macroId, "+", node.getBne());
			}
			else {
				// on va cherche le offset du label
				labelOffset = this.currentFrame.getAnonLabelOffset(writeData.getOffset(), this.macroId, "-", node.getBne());
			}
			
			// on met à jour le write data avec la longeur du branchement
			changeBranchWriteData(writeData, labelOffset, node.getBne());		
		}
	}
	
	@Override
	public void caseACmpAbsOpcode(ACmpAbsOpcode node) {
		WriteData writeData = getWriteData(node);
		
		// si on a affaire à un label
		if(writeData != null && !writeData.isReady()) {
			// nom du label
			String name = ((ALabelOpArg)node.getOpArg()).getIdent().getText();
			// on met à jour le write data avec l'offset du label
			changeAbsWriteData(node.getCmp(), writeData, name);			
		}
	}
	
	@Override
	public void caseACmpConstOpcode(ACmpConstOpcode node) {
		WriteData writeData = getWriteData(node);
		
		// si on a affaire à un label
		if(writeData != null && !writeData.isReady()) {
			// nom du label
			String name = ((ALabelOpArg)node.getOpArg()).getIdent().getText();
			// on met à jour le write data avec l'offset du label
			changeAbsWriteData(node.getCmp(), writeData, name);			
		}
	}
	
	@Override
	public void caseADecAbsOpcode(ADecAbsOpcode node) {
		WriteData writeData = getWriteData(node);
		
		// si on a affaire à un label
		if(writeData != null && !writeData.isReady()) {
			// nom du label
			String name = ((ALabelOpArg)node.getOpArg()).getIdent().getText();
			// on met à jour le write data avec l'offset du label
			changeAbsWriteData(node.getDec(), writeData, name);			
		}
	}
	
	@Override
	public void caseAIncAbsOpcode(AIncAbsOpcode node) {
		WriteData writeData = getWriteData(node);
		
		// si on a affaire à un label
		if(writeData != null && !writeData.isReady()) {
			// nom du label
			String name = ((ALabelOpArg)node.getOpArg()).getIdent().getText();
			// on met à jour le write data avec l'offset du label
			changeAbsWriteData(node.getInc(), writeData, name);			
		}
	}
	
	@Override
	public void caseAJmpOpcode(AJmpOpcode node) {
		WriteData writeData = getWriteData(node);
		
		// si on a affaire à un label
		if(writeData != null && !writeData.isReady()) {
			// nom du label
			String name = ((ALabelOpArg)node.getOpArg()).getIdent().getText();
			// on met à jour le write data avec l'offset du label
			changeAbsWriteData(node.getJmp(), writeData, name);			
		}
	}
	
	@Override
	public void caseALdaAbsOpcode(ALdaAbsOpcode node) {
		WriteData writeData = getWriteData(node);
		
		// si on a affaire à un label
		if(writeData != null && !writeData.isReady()) {
			// nom du label
			String name = ((ALabelOpArg)node.getOpArg()).getIdent().getText();
			// on met à jour le write data avec l'offset du label
			changeAbsWriteData(node.getLda(), writeData, name);			
		}
	}
	
	@Override
	public void caseALdaConstOpcode(ALdaConstOpcode node) {
		WriteData writeData = getWriteData(node);
		
		// si on a affaire à un label
		if(writeData != null && !writeData.isReady()) {
			// nom du label
			String name = ((ALabelOpArg)node.getOpArg()).getIdent().getText();
			// on met à jour le write data avec l'offset du label
			changeAbsWriteData(node.getLda(), writeData, name);			
		}
	}
	
	@Override
	public void caseALdaIndXOpcode(ALdaIndXOpcode node) {
		WriteData writeData = getWriteData(node);
		
		// si on a affaire à un label
		if(writeData != null && !writeData.isReady()) {
			// nom du label
			String name = ((ALabelOpArg)node.getOpArg()).getIdent().getText();
			// on met à jour le write data avec l'offset du label
			changeAbsWriteData(node.getLda(), writeData, name);			
		}
	}
	
	@Override
	public void caseALdxAbsOpcode(ALdxAbsOpcode node) {
		WriteData writeData = getWriteData(node);
		
		// si on a affaire à un label
		if(writeData != null && !writeData.isReady()) {
			// nom du label
			String name = ((ALabelOpArg)node.getOpArg()).getIdent().getText();
			// on met à jour le write data avec l'offset du label
			changeAbsWriteData(node.getLdx(), writeData, name);			
		}
	}
	
	@Override
	public void caseALdxConstOpcode(ALdxConstOpcode node) {
		WriteData writeData = getWriteData(node);
		
		// si on a affaire à un label
		if(writeData != null && !writeData.isReady()) {
			// nom du label
			String name = ((ALabelOpArg)node.getOpArg()).getIdent().getText();
			// on met à jour le write data avec l'offset du label
			changeAbsWriteData(node.getLdx(), writeData, name);			
		}
	}
	
	@Override
	public void caseAStaAbsOpcode(AStaAbsOpcode node) {
		WriteData writeData = getWriteData(node);
		
		// si on a affaire à un label
		if(writeData != null && !writeData.isReady()) {
			// nom du label
			String name = ((ALabelOpArg)node.getOpArg()).getIdent().getText();
			// on met à jour le write data avec l'offset du label
			changeAbsWriteData(node.getSta(), writeData, name);			
		}
	}
	
	@Override
	public void caseAStaIndXOpcode(AStaIndXOpcode node) {
		WriteData writeData = getWriteData(node);
		
		// si on a affaire à un label
		if(writeData != null && !writeData.isReady()) {
			// nom du label
			String name = ((ALabelOpArg)node.getOpArg()).getIdent().getText();
			// on met à jour le write data avec l'offset du label
			changeAbsWriteData(node.getSta(), writeData, name);			
		}
	}
}
