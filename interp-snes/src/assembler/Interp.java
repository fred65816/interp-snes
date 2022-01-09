package assembler;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import assembler.InterpreterException;
import assembler.SemanticException;
import assembler.SemanticInfo;
import assembler.syntax.lexer.Lexer;
import assembler.syntax.lexer.LexerException;
import assembler.syntax.node.Node;
import assembler.syntax.node.Start;
import assembler.syntax.parser.Parser;
import assembler.syntax.parser.ParserException;

public class Interp {
    public static void main(
            String[] args) {

        if (args.length != 1) {
            System.err.println("Usage: java interp.Interp nomficher [test]");
            System.exit(1);
        }
        
        String[] path = args[0].split("/");
        String filename = path[path.length - 1];
        filename = filename.substring(0, filename.lastIndexOf('.'));

        try {
            Lexer lexer = new Lexer(new PushbackReader(new FileReader(args[0]), 2));
            Parser parser = new Parser(lexer);

            Start tree = parser.parse();
            
            SemanticInfo semantics = new SemanticInfo();
            
            OpcodeList opcodeList = new OpcodeList();
            
            Map<DataNode, WriteData> data = new LinkedHashMap<>();
            
            Map<Node, Frame> frames = new HashMap<>();
            
            tree.apply(new SemanticAnalysisPhase1(semantics));
            
            tree.apply(new SemanticAnalysisPhase2(semantics));

            tree.apply(new InterpreterEngine(semantics, data, frames, opcodeList));
            
            tree.apply(new CompilationEngine(semantics, data, frames, filename));
        }
        catch (FileNotFoundException e) {
            System.err.println("The file " + args[0] + " has not been found.");
            System.exit(1);
        }
        catch (ParserException e) {
            System.err.println("SYNTAX ERROR: " + e.getMessage());
            System.exit(1);
        }
        catch (LexerException e) {
            System.err.println("LEXICAL ERROR: " + e.getMessage());
            System.exit(1);
        }
        catch (IOException e) {
            System.err.println("I/O ERROR: " + e.getMessage());
            System.exit(1);
        }
        catch (InterpreterException e) {
            System.err.println("INTERPRETATION ERROR: " + e.getMessage());
            //e.printCallContext();
            System.exit(1);
        }
        catch (SemanticException e) {
        	System.err.println("SEMANTIC ERROR: " + e.getMessage());
            System.exit(1);
        }
        catch (CompilationException e) {
        	System.err.println("COMPILATION ERROR: " + e.getMessage());
            System.exit(1);
        }
    }
}
