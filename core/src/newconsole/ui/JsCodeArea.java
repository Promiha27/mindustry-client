package newconsole.ui;

/*
 * A text area somewhat assisting in writing JS scripts.
 * @author Mnemotechnician
 */
@SuppressWarnings("CanBeFinal")
public class JsCodeArea extends CodeArea {

    public JsCodeArea(String text) {
        super(text);
    }

    public JsCodeArea(String text, TextFieldStyle style) {
        super(text, style);
    }

    @Override
    public void loadInfo(){
        keywords.addAll("function", "class", "const", "let", "var", "delete", "in", "of");
        statements.addAll("if", "else", "do", "while", "for", "switch", "return", "break", "continue");
        literals.addAll("this", "this$super", "super", "true", "false");
        specials.addAll("_autorun_event");
    }

}

