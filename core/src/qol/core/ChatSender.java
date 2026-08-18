package qol.core;

import mindustry.gen.Call;

/** Shared by {@link qol.quickchat.QuickChatFeature} and {@link qol.cbinds.CustomBindsFeature} - both fire user-authored, possibly multi-line text as chat messages. */
public final class ChatSender{
    private ChatSender(){
    }

    /** Splits on newlines, and further splits any line over the game's 150-char chat limit into multiple sends. */
    public static void send(String text){
        if(text == null || text.isEmpty()) return;
        for(String line : text.split("\n")){
            while(line.length() > 150){
                Call.sendChatMessage(line.substring(0, 150));
                line = line.substring(150);
            }
            if(!line.isEmpty()) Call.sendChatMessage(line);
        }
    }
}
