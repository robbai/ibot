package ibot.bot.bots;

import ibot.bot.input.Info;
import ibot.bot.step.Step;
import ibot.bot.step.steps.DefenseStep;
import ibot.bot.step.steps.GrabBoostStep;
import ibot.bot.step.steps.KickoffStep;
import ibot.bot.step.steps.OffenseStep;
import ibot.bot.step.steps.SaveStep;
import ibot.bot.utils.Mode;

public class IBot extends ABot {

	public IBot(int index, int team){
		super(index, team);
	}

	@Override
	protected Step fallbackStep(){
//		DataPacket packet = this.bundle.packet;
//		Pencil pencil = this.bundle.pencil;
		Info info = this.bundle.info;
//		Car car = packet.car;

		if(info.isKickoff && info.mode != Mode.DROPSHOT){
			if(info.commit){
				return new KickoffStep(this.bundle);
			}else{
				GrabBoostStep grabBoost = GrabBoostStep.findBoost(this.bundle);
				if(grabBoost != null){
					return grabBoost;
				}
				return new DefenseStep(this.bundle);
			}
		}

		if(SaveStep.mustSave(this.bundle)){
			return new SaveStep(this.bundle);
		}

		if(info.commit){
			return new OffenseStep(this.bundle);
		}

		return new DefenseStep(this.bundle);
	}

}
