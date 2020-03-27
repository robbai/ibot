package ibot.bot.abort;

import ibot.bot.input.Bundle;

public abstract class AbortCondition {

	protected Bundle bundle;
	private double startTime;

	public AbortCondition(Bundle bundle){
		this.bundle = bundle;
		this.startTime = bundle.packet.time;
	}

	public abstract boolean shouldAbort();

	public double getStartTime(){
		return this.startTime;
	}

}
