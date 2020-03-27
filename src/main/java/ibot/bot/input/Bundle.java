package ibot.bot.input;

import ibot.bot.bots.ABot;
import ibot.input.DataPacket;

public class Bundle {

	public ABot bot;
	public DataPacket packet;
	public Info info;
	public Pencil pencil;

	public Bundle(ABot bot, Info info){
		super();
		this.bot = bot;
		this.info = info;
		this.pencil = new Pencil(bot);
	}

}
