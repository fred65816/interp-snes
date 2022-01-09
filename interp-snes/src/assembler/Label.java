package assembler;

public class Label {
	
	private String name;
	private String prefix;
	private int offset;
	private int macroId;
	
	public Label(String name, String prefix, int macroId, int offset) {
		this.name = name;
		this.prefix = prefix;
		this.macroId = macroId;
		this.offset = offset;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getPrefix() {
		return this.prefix;
	}
	
	public int getOffset() {
		return this.offset;
	}
	
	public int getMacroId() {
		return this.macroId;
	}
	
	public void setOffset(int offset) {
		this.offset = offset;
	}
}
