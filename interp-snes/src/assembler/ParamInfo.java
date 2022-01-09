package assembler;

import assembler.Type;
import assembler.syntax.node.*;

public class ParamInfo {
    private AParam declaration;

    private TIdent name;

    private Type type;

    public ParamInfo(AParam declaration, Type type) {

        this.declaration = declaration;
        this.name = declaration.getIdent();
        this.type = type;
    }

    public TIdent getName() {
        return this.name;
    }

    public Type getType() {
        return this.type;
    }
}
