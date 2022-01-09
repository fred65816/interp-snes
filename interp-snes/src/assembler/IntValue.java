package assembler;

public class IntValue extends Value {
    private int value;

    public IntValue(int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }
    
    public void setValue(int value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "" + this.value;
    }
}
