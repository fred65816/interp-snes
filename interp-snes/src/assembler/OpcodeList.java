package assembler;

import java.util.HashMap;
import java.util.Map;

// liste des valeurs hexadécimal des instructions
public class OpcodeList {
	private Map<String, Integer> opcodeList = new HashMap<>();
	
	public OpcodeList() {
		opcodeList.put(Consts.BEQ, 0xF0);
		opcodeList.put(Consts.BNE, 0xD0);
		opcodeList.put(Consts.CMP_ABS + Consts.BYTE, 0xC5);
		opcodeList.put(Consts.CMP_ABS + Consts.WORD, 0xCD);
		opcodeList.put(Consts.CMP_ABS + Consts.LONG, 0xCF);
		opcodeList.put(Consts.CMP_CONST, 0xC9);
		opcodeList.put(Consts.DEC_ACC, 0x3A);
		opcodeList.put(Consts.DEC_ABS + Consts.BYTE, 0xC6);
		opcodeList.put(Consts.DEC_ABS + Consts.WORD, 0xCE);
		opcodeList.put(Consts.INC_ACC, 0x1A);
		opcodeList.put(Consts.INC_ABS + Consts.BYTE, 0xE6);
		opcodeList.put(Consts.INC_ABS + Consts.WORD, 0xEE);
		opcodeList.put(Consts.JMP + Consts.WORD, 0x4C);
		opcodeList.put(Consts.JMP + Consts.LONG, 0x5C);
		opcodeList.put(Consts.LDA_ABS + Consts.BYTE, 0xA5);
		opcodeList.put(Consts.LDA_ABS + Consts.WORD, 0xAD);
		opcodeList.put(Consts.LDA_ABS + Consts.LONG, 0xAF);
		opcodeList.put(Consts.LDA_CONST, 0xA9);
		opcodeList.put(Consts.LDA_IND_X + Consts.BYTE, 0xB5);
		opcodeList.put(Consts.LDA_IND_X + Consts.WORD, 0xBD);
		opcodeList.put(Consts.LDA_IND_X + Consts.LONG, 0xBF);
		opcodeList.put(Consts.LDX_ABS + Consts.BYTE, 0xA6);
		opcodeList.put(Consts.LDX_ABS + Consts.WORD, 0xAE);
		opcodeList.put(Consts.LDX_CONST, 0xA0);
		opcodeList.put(Consts.MVN, 0x54);
		opcodeList.put(Consts.MVP, 0x44);
		opcodeList.put(Consts.REP, 0xC2);
		opcodeList.put(Consts.SEP, 0xE2);
		opcodeList.put(Consts.STA_ABS + Consts.BYTE, 0x85);
		opcodeList.put(Consts.STA_ABS + Consts.WORD, 0x8D);
		opcodeList.put(Consts.STA_ABS + Consts.LONG, 0x8F);
		opcodeList.put(Consts.STA_IND_X + Consts.BYTE, 0x95);
		opcodeList.put(Consts.STA_IND_X + Consts.WORD, 0x9D);
		opcodeList.put(Consts.STA_IND_X + Consts.LONG, 0x9F);
		opcodeList.put(Consts.RTS, 0x60);
	}
	
	public Integer getOpcode(String key) {
		return this.opcodeList.get(key);
	}
}
