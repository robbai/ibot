package ibot.bot.abort;

import ibot.bot.utils.DataBot;
import ibot.input.DataPacket;

public class CommitAbort extends AbortCondition {

	private final double minTime;

	public CommitAbort(DataBot bot, double minTime){
		super(bot);
		this.minTime = minTime;
	}

	@Override
	public boolean shouldAbort(DataPacket packet){
		if(packet.time - this.getStartTime() < this.minTime){
			return false;
		}
		return this.bot.commit;
	}

}
