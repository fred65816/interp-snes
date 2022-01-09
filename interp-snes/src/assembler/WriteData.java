package assembler;

public class WriteData {
	private int offset;
	private byte[] data;
	private boolean isReady;
	private String macroId;
	private Integer extraValue;
	
	public WriteData(int offset, byte[] data, boolean isReady, Integer extraValue) {
		this.offset = offset;
		this.data = data;
		this.isReady = isReady;
		this.macroId = null;
		this.extraValue = extraValue;
	}
	
	public int getOffset() {
		return this.offset;
	}
	
	public byte[] getData() {
		return this.data;
	}
	
	public void setData(byte[] data) {
		this.data = data;
	}
	
	public void setReady(boolean isReady) {
		this.isReady = isReady;
	}
	
	public boolean isReady() {
		return this.isReady;
	}
	
	public String getMacroId() {
		return this.macroId;
	}
	
	public void setMacroId(String macroId) {
		this.macroId = macroId;
	}
	
	public Integer getExtraValue() {
		return this.extraValue;
	}
	
	public void setExtraValue(Integer extraValue) {
		this.extraValue = extraValue;
	}
}
