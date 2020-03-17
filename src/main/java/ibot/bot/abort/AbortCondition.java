package ibot.bot.abort;

import ibot.bot.utils.DataBot;
import ibot.input.DataPacket;

public abstract class AbortCondition {
	
	protected DataBot bot;
	private double startTime;

	public AbortCondition(DataBot bot){
		this.bot = bot;
		this.startTime = bot.time;
	}

	public abstract boolean shouldAbort(DataPacket packet);
	
	public double getStartTime(){
		return startTime;
	}

}
