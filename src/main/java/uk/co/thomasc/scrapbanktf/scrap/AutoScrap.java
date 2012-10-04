package uk.co.thomasc.scrapbanktf.scrap;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import uk.co.thomasc.scrapbanktf.Bot;
import uk.co.thomasc.scrapbanktf.Main;
import uk.co.thomasc.scrapbanktf.inventory.Inventory;
import uk.co.thomasc.scrapbanktf.inventory.Item;
import uk.co.thomasc.scrapbanktf.util.ConsoleColor;
import uk.co.thomasc.scrapbanktf.util.MutableInt;
import uk.co.thomasc.scrapbanktf.util.Util;
import uk.co.thomasc.steamkit.base.ClientMsgProtobuf;
import uk.co.thomasc.steamkit.base.gc.ClientGCMsg;
import uk.co.thomasc.steamkit.base.gc.tf2.GCMsgCraftItem;
import uk.co.thomasc.steamkit.base.generated.SteammessagesClientserver.CMsgClientGamesPlayed;
import uk.co.thomasc.steamkit.base.generated.SteammessagesClientserver.CMsgClientGamesPlayed.GamePlayed;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EMsg;
import uk.co.thomasc.steamkit.steam3.handlers.steamgamecoordinator.SteamGameCoordinator;

public class AutoScrap {

	private final Bot bot;
	private Boolean ingame = false;
	private static Object lck = new Object();

	public AutoScrap(Bot bot) {
		this.bot = bot;
	}

	private void opengame() {
		final ClientMsgProtobuf<CMsgClientGamesPlayed.Builder> playGame = new ClientMsgProtobuf<CMsgClientGamesPlayed.Builder>(CMsgClientGamesPlayed.class, EMsg.ClientGamesPlayed);

		playGame.getBody().addGamesPlayed(GamePlayed.newBuilder().setGameId(440).build());

		bot.steamClient.send(playGame);
	}

	public void onWelcome() {
		ingame = true;
		notify();
	}

	private void scrap(long item1, long item2) {
		if (!ingame) {
			opengame();
			try {
				wait();
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}
		}
		craft(item1, item2);
	}

	private void craft(long item1, long item2) {
		final ClientGCMsg<GCMsgCraftItem> msg = new ClientGCMsg<GCMsgCraftItem>(GCMsgCraftItem.class);

		msg.getBody().recipe = 3;
		msg.getBody().items.add(item1);
		msg.getBody().items.add(item2);

		bot.steamClient.getHandler(SteamGameCoordinator.class).send(msg, 440);
	}

	public void run() {
		synchronized (AutoScrap.lck) {
			final Inventory MyInventory = Inventory.fetchInventory(bot.steamClient.getSteamId().convertToLong());
			if (MyInventory == null) {
				Util.printConsole("Could not fetch own inventory via Steam API! (AutoScrap)", bot, ConsoleColor.White, true);
			}

			final ResultSet result = Main.sql.selectQuery("SELECT schemaid, classid, (stock - COUNT(reservation.Id) + IF(highvalue=1 and stock - COUNT(reservation.Id) > 1,-2,0)) as stk FROM items LEFT JOIN reservation ON items.schemaid = reservation.itemid WHERE highvalue != 2 GROUP BY items.schemaid HAVING stk > 4");
			final Map<Integer, MutableInt> count = new HashMap<Integer, MutableInt>();
			final Map<Integer, Byte> classid = new HashMap<Integer, Byte>();
			final Map<Integer, MutableInt> scraped = new HashMap<Integer, MutableInt>();
			try {
				while (result.next()) {
					try {
						count.put(result.getInt("schemaid"), new MutableInt(result.getInt("stk")));
						classid.put(result.getInt("schemaid"), result.getByte("classid"));
						scraped.put(result.getInt("schemaid"), new MutableInt(0));
					} catch (final SQLException e) {
						e.printStackTrace();
					}
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
			final Map<Byte, Long> otherid = new HashMap<Byte, Long>();

			for (final Long id : MyInventory.getItemIds()) {
				final Item item = MyInventory.getItem(id);

				if (count.containsKey(item.defIndex) && count.get(item.defIndex).get() > 4) {
					count.get(item.defIndex).decrement();
					if (otherid.containsKey(classid.get(item.defIndex))) {
						final Item item2 = MyInventory.getItem(otherid.get(classid.get(item.defIndex)));
						scrap(otherid.get(classid.get(item.defIndex)), id);
						scraped.get(item.defIndex).increment();
						scraped.get(item2.defIndex).increment();
						otherid.remove(classid.get(item.defIndex));
					} else {
						otherid.put(classid.get(item.defIndex), id);
					}
				}
			}

			int totalItems = 0;
			for (final int id : scraped.keySet()) {
				if (scraped.get(id).get() > 0) {
					totalItems += scraped.get(id).get();
					Main.sql.update("UPDATE items SET stock = stock - " + scraped.get(id).get() + " WHERE schemaid = " + id);
				}
			}
			Main.sql.update("UPDATE bots SET items = items - " + totalItems + ", scrap = scrap + " + totalItems / 2 + " WHERE botid = " + bot.getBotId());

			if (ingame) {
				final ClientMsgProtobuf<CMsgClientGamesPlayed.Builder> playGame = new ClientMsgProtobuf<CMsgClientGamesPlayed.Builder>(CMsgClientGamesPlayed.class, EMsg.ClientGamesPlayed);
				bot.steamClient.send(playGame);
				ingame = false;
			}
		}
	}
}
