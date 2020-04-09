package ibot.bot.bots;

import ibot.bot.input.Info;
import ibot.bot.step.Step;
import ibot.bot.step.steps.DefenseStep;
import ibot.bot.step.steps.OffenseStep;
import ibot.bot.step.steps.SaveStep;
import ibot.bot.step.steps.kickoff.KickoffStep;
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

		if(info.isKickoff && info.mode == Mode.SOCCAR){
			return new KickoffStep(this.bundle);
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
