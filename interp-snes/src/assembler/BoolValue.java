package assembler;

public class BoolValue extends Value {
    private boolean value;

    public BoolValue(boolean value) {
        this.value = value;
    }

    public boolean getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        return "" + this.value;
    }
}
