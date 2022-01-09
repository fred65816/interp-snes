package assembler;

import java.util.Arrays;

public class OpcodeArg {
	private int numBytes;
	private int value;
	
	public OpcodeArg(int value, int numBytes) {
		this.value = value;
		this.numBytes = numBytes;
	}
	
	public int getValue() {
		return this.value;
	}
	
	public void setNumByte(int numBytes) {
		this.numBytes = numBytes;
	}
	
	public int getNumBytes() {
		return this.numBytes;
	}
	
	public byte[] getBytes() {
		byte[] valLong = new byte[] {
				(byte)(this.value),
				(byte)(this.value >>> 8),
				(byte)(this.value >>> 16)
		};
		
		if(this.numBytes == 2) {
			return new byte [] { valLong[0], valLong[1] };
		}
		else if(this.numBytes == 1) {
			return new byte [] { valLong[0] };
		}
		else {
			return valLong;
		}
	}
}
