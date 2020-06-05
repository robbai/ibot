package ibot.bot.input;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;

import rlbot.manager.BotLoopRenderer;
import ibot.bot.bots.ABot;
import ibot.bot.utils.maths.MathsUtils;
import ibot.bot.utils.rl.RenderString;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

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

	public void postRender(Bundle bundle){
		for(int i = 0; i < this.renderStack.size(); i++){
			this.renderer.drawString2d(this.renderStack.get(i).string, this.renderStack.get(i).colour,
					new Point(20 + 400 * this.bot.index, 30 * (i + 1)), 2, 2);
		}
		this.renderStack.clear();

		Info info = bundle.info;
		Vector2 trace = MathsUtils.traceToWall(info.groundIntercept.position.flatten(),
				info.groundIntercept.getOffset().scale(-1).flatten(), info.arena.getWidth(), info.arena.getLength());
		if(trace != null)
			this.renderer.drawLine3d(Color.RED, info.groundIntercept.position,
					trace.withZ(info.groundIntercept.position.z));

//		if(bundle.packet.teammates.length == 0 || bot.index < bundle.packet.teammates[0].index){
//			for(Car c : bundle.packet.cars)
//				if(c.index != bot.index){
//					this.renderer.drawLine3d(Color.BLACK, bundle.info.groundIntercepts[c.index].intersectPosition,
//							bundle.info.groundIntercepts[c.index].position);
//					this.renderer.drawRectangle3d(c.team == bot.team ? this.colour : Color.WHITE,
//							bundle.info.groundIntercepts[c.index].intersectPosition, 8, 8, true);
//				}
//		}

//		if(bundle.packet.cars.length > 2){
//			for(Car c : bundle.packet.cars){
//				if(c.team != this.bot.team || bundle.info.interceptValues.length <= c.index)
//					continue;
//				this.renderer.drawString3d(MathsUtils.round(bundle.info.interceptValues[c.index]) + "", Color.WHITE,
//						c.position.plus(Vector3.Z.scale(100 * (this.bot.index + 1))), 2, 2);
//			}
//		}
	}

	public void renderCrosshair(Color colour, double size, Vector3 centre, Vector3 facing){
		Vector3 line1 = centre.minus(facing).withZ(0);
		Vector3 line2 = line1.flatten().rotate(Math.PI / 2).withZ(0).scaleToMagnitude(size);
		this.renderer.drawLine3d(colour, centre.plus(line2), centre.minus(line2));
		line1 = line1.cross(line2).scaleToMagnitude(size);
		this.renderer.drawLine3d(colour, centre.plus(line1), centre.minus(line1));
	}

	public void renderCrosshair(Color colour, Vector3 centre, Vector3 facing){
		renderCrosshair(colour, 75, centre, facing);
	}

}
