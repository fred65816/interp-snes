package assembler;

// Constantes des instructions

public class Consts {
	public static final String BEQ = "beq";
	public static final String BNE = "bne";
	public static final String CMP_ABS = "cmp_abs";
	public static final String CMP_CONST = "cmp_const";
	public static final String DEC_ACC = "dec_acc";
	public static final String DEC_ABS = "dec_abs";
	public static final String INC_ACC = "inc_acc";
	public static final String INC_ABS = "inc_abs";
	public static final String JMP = "jmp";
	public static final String LDA_ABS = "lda_abs";
	public static final String LDA_CONST = "lda_const";
	public static final String LDA_IND_X = "lda_ind_x";
	public static final String LDX_ABS = "ldx_abs";
	public static final String LDX_CONST = "ldx_const";
	public static final String MVN = "mvn";
	public static final String MVP = "mvp";
	public static final String REP = "rep";
	public static final String SEP = "sep";
	public static final String STA_ABS = "sta_abs";
	public static final String STA_IND_X = "sta_ind_x";
	public static final String RTS = "rts";
	
	public static final String BYTE = "_b";
	public static final String WORD = "_w";
	public static final String LONG = "_l";
	
	// nombre max 24-bit
	static final int MAX_INT = 0xFFFFFF;
}
