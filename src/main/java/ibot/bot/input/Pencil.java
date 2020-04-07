package ibot.bot.input;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;

import rlbot.manager.BotLoopRenderer;
import ibot.bot.bots.ABot;
import ibot.bot.utils.RenderString;

public class Pencil {

	public final BotLoopRenderer renderer;

	private final ArrayList<RenderString> renderStack;

	private ABot bot;

	public Color colour, altColour;

	public Pencil(ABot bot){
		this.bot = bot;

		this.renderer = BotLoopRenderer.forBotLoop(bot);

		this.renderStack = new ArrayList<RenderString>();
	}

	public void stackRenderString(String string, Color colour){
		this.renderStack.add(new RenderString(string, colour));
	}

	public void preRender(Bundle bundle){
		int team = bundle.packet.car.team;
		this.colour = (team == 0 ? Color.BLUE : Color.ORANGE);
		this.altColour = (team == 0 ? Color.CYAN : Color.RED);
	}

	public void postRender(){
		for(int i = 0; i < this.renderStack.size(); i++){
			renderer.drawString2d(this.renderStack.get(i).string, this.renderStack.get(i).colour,
					new Point(20 + 500 * this.bot.index, 30 * (i + 1)), 2, 2);
		}
		this.renderStack.clear();
	}

}
