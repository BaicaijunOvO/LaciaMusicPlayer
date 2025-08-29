package ovo.baicaijun.laciamusicplayer.command;


import ovo.baicaijun.laciamusicplayer.command.commands.Music;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CommandManager {
    private final Map<String[], Command> commands = new HashMap<String[], Command>(){};

    public CommandManager() {

    }

    public void load(){
        Music music = new Music();

        commands.put(music.getKey(), music);
    }

    public void run(String message){
        String submessage = message.substring(1);
        String[] s = submessage.split(" ");
        String key = s[0];
        String[] args = Arrays.copyOfRange(s, 1, s.length);
        Command command = getCommand(key);
        if (command != null) {
            command.run(args);
        }

    }

    public Command getCommand(String key){
        for (Map.Entry<String[], Command> commandEntry : commands.entrySet()) {
            for (String k : commandEntry.getKey()) {
                if (k.equals(key)) {
                    return commandEntry.getValue();
                }
            }
        }

        return null;
    }
}
