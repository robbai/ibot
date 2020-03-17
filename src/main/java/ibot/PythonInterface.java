package ibot;

import ibot.bot.bots.*;
import rlbot.Bot;
import rlbot.manager.BotManager;
import rlbot.pyinterop.SocketServer;

public class PythonInterface extends SocketServer {

    public PythonInterface(int port, BotManager botManager){
        super(port, botManager);
    }

    protected Bot initBot(int index, String botType, int team){
    	System.out.println("Receiving: " + botType);
        return (botType.toLowerCase().contains("testbot") ? new TestBot(index) : new IBot(index));
    }
    
}
