package ibot.bot.actions;

import java.util.ArrayList;

import ibot.bot.abort.AbortCondition;
import ibot.bot.utils.DataBot;
import ibot.input.DataPacket;
import ibot.output.ControlsOutput;
import ibot.output.Output;

public abstract class Action extends Output {

	protected DataBot bot;
	private boolean finished;
	private double startTime;
	private ArrayList<AbortCondition> abortConditions;

	public Action(DataBot bot){
		this.bot = bot;
		this.finished = false;
		this.startTime = bot.time;
		this.abortConditions = new ArrayList<AbortCondition>();
	}

	public abstract ControlsOutput getOutput(DataPacket packet);

	public boolean isFinished(DataPacket packet){
		if(!this.finished){
			for(AbortCondition abort : this.abortConditions){
				if(abort.shouldAbort(packet)){
					this.finished = true;
					return true;
				}
			}
		}
		return finished;
	}

	protected void setFinished(boolean finished){
		if(this.finished) return;
		this.finished = finished;
	}

	public double getStartTime(){
		return startTime;
	}

	@Override
	public boolean isControls(){
		return false;
	}

	@Override
	public boolean isAction(){
		return true;
	}

	public Action withAbortCondition(AbortCondition... abortConditions){
		for(AbortCondition abort : abortConditions){
			this.abortConditions.add(abort);
		}
		return this;
	}

}
