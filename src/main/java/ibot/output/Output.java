package ibot.output;

import ibot.bot.actions.Action;
import ibot.input.DataPacket;

public abstract class Output {

	public abstract boolean isControls();
	
	public abstract boolean isAction();
	
	public ControlsOutput getOutput(DataPacket packet){
		if(this.isAction()) return ((Action)this).getOutput(packet);
		return (ControlsOutput)this;
	}

}
