package ibot;

import rlbot.Bot;
import rlbot.manager.BotManager;
import rlbot.pyinterop.SocketServer;
import ibot.bot.bots.*;

public class PythonInterface extends SocketServer {

	public PythonInterface(int port, BotManager botManager){
		super(port, botManager);
	}

	protected Bot initBot(int index, String name, int team){
		return (name.toLowerCase().contains("tbot") ? new TBot(index, team) : new IBot(index, team));
	}

	@Override
	public void shutdown(){
		if(Main.getArguments().contains("never-shutdown")){
			System.out.println("Preventing shut down...");
//    		for(int i = 0; i < Main.bots.size(); i++) this.retireBot(i);
			return;
		}
		super.shutdown();
	}

}
