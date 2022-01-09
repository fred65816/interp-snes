package assembler;

import assembler.syntax.node.*;

// classe d'un identifiant d'instruction à écrire en binaire
// composé du node de l'instruction et du macro ID. Avenue utilisée
// puisque l'appel de deux fois un macro ajoutait des doublons à la liste
// si l'identifiant était seulement le node.

public class DataNode {
	private int macroId;
	private Node node;
	
	public DataNode(Node node, int macroId) {
		this.macroId = macroId;
		this.node = node;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o == this) {
			return true;
		}
		
		if(!(o instanceof DataNode)) {
			return false;
		}
		
		DataNode dn = (DataNode)o;	
		return macroId == dn.macroId && node.hashCode() == dn.node.hashCode();
	}
}
