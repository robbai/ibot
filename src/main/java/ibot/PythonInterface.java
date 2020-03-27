package ibot;

import rlbot.Bot;
import rlbot.manager.BotManager;
import rlbot.pyinterop.SocketServer;
import ibot.bot.bots.*;

public class PythonInterface extends SocketServer {

	public PythonInterface(int port, BotManager botManager){
		super(port, botManager);
	}

	protected Bot initBot(int index, String botType, int team){
		System.out.println("Receiving: " + botType);
		return (botType.toLowerCase().contains("testbot") ? new TestBot(index, team) : new IBot(index, team));
	}

}
