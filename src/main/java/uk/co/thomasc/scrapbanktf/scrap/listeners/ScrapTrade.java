package uk.co.thomasc.scrapbanktf.scrap.listeners;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import uk.co.thomasc.scrapbanktf.Bot;
import uk.co.thomasc.scrapbanktf.Main;
import uk.co.thomasc.scrapbanktf.inventory.Item;
import uk.co.thomasc.scrapbanktf.trade.TradeItem;
import uk.co.thomasc.scrapbanktf.trade.TradeItemDescription;
import uk.co.thomasc.scrapbanktf.trade.TradeListener;
import uk.co.thomasc.scrapbanktf.util.ConsoleColor;
import uk.co.thomasc.scrapbanktf.util.DualMInt;
import uk.co.thomasc.scrapbanktf.util.ItemInfo;
import uk.co.thomasc.scrapbanktf.util.Util;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EChatEntryType;

public class ScrapTrade extends TradeListener {
	private final Map<Integer, Boolean> accepted = new HashMap<Integer, Boolean>();

	private final List<Integer> reservedGiven = new ArrayList<Integer>();
	private boolean onlyApplicable = true;
	private boolean check = false;

	public ScrapTrade(Bot bot) {
		super(bot);
	}

	@Override
	public void onTimeout() {
		bot.steamFriends.sendChatMessage(trade.otherSID, EChatEntryType.ChatMsg, "Trade took too long, please rejoin queue");
		OnFinished(false);
	}

	@Override
	public void onError(int eid) {
		Util.printConsole("Error(" + eid + ") during trade with user", bot, ConsoleColor.Red);
		if (eid == 2) {
			bot.queueHandler.tradeEnded();
			bot.currentTrade = null;
			return;
		}
		OnFinished(false);
	}

	@Override
	public void onAfterInit() {
		trade.sendMessage("Welcome to ScrapBank!");
		respond();
	}

	@Override
	public void onUserAccept() {
		respond();
		if (check && onlyApplicable) {
			try {
				final JSONObject js = trade.acceptTrade();
				if ((boolean) js.get("success") == true) {
					Util.printConsole("Trade Success", bot, ConsoleColor.Green, true);
					return;
				}
			} catch (final ParseException e) {
				e.printStackTrace();
			}
			Util.printConsole("Trade Failure", bot, ConsoleColor.Red, true);
		}
	}

	@Override
	public void onComplete() {
		Util.printConsole("OnComplete called", bot, ConsoleColor.White, true);

		final Map<Integer, DualMInt> itemTotals = new HashMap<Integer, DualMInt>();
		itemTotals.put(5000, new DualMInt(0, 0));

		for (final long child : trade.OtherTrade) {
			final Item record = trade.OtherInventory.getItem(child);
			if (itemTotals.containsKey(record.defIndex)) {
				itemTotals.get(record.defIndex).get1().increment();
			} else {
				itemTotals.put(record.defIndex, new DualMInt(1, 0));
			}
		}

		for (final long child : trade.MyTrade) {
			final Item record = trade.MyInventory.getItem(child);
			if (record.defIndex != 5000) {
				Main.sql.update("DELETE FROM reservation WHERE itemid = '" + record.defIndex + "' && steamid = '" + trade.otherSID.convertToLong() + "' LIMIT 1");
			}
			if (itemTotals.containsKey(record.defIndex)) {
				itemTotals.get(record.defIndex).get2().increment();
			} else {
				itemTotals.put(record.defIndex, new DualMInt(0, 1));
			}
		}

		final int id = Main.sql.insert("INSERT INTO tradelog (steamid, botid, success) VALUES ('" + trade.otherSID.convertToLong() + "', '" + bot.getBotId() + "', '1')");

		for (final int itemid : itemTotals.keySet()) {
			Main.sql.update("UPDATE items SET stock = stock + " + itemTotals.get(itemid).diff() + ", `in` = `in` + " + itemTotals.get(itemid).get1().get() + ", `out` = `out` + " + itemTotals.get(itemid).get2().get() + " WHERE schemaid = '" + itemid + "'");
			Main.sql.update("INSERT INTO tradeitems (tradeid, schemaid, quantityIn, quantityOut) VALUES (" + id + ", '" + itemid + "', " + itemTotals.get(itemid).get1().get() + ", " + itemTotals.get(itemid).get2().get() + ")");
		}

		Main.sql.update("UPDATE bots SET trades = trades + 1, scrap = scrap + " + itemTotals.get(5000).diff() + ", items = items + " + (trade.OtherTrade.size() - itemTotals.get(5000).get1().get() - reservedGiven.size()) + " WHERE botid = '" + bot.getBotId() + "'");
		reservedGiven.clear();
		bot.steamFriends.sendChatMessage(trade.otherSID, EChatEntryType.ChatMsg, "Thanks for trading with us :)");
		OnFinished(true);
	}

	public void OnFinished(boolean success) {
		bot.queueHandler.tradeEnded();
		bot.currentTrade = null;
		if (!success) {
			Main.sql.update("INSERT INTO tradelog (steamid, botid, success) VALUES ('" + trade.otherSID.convertToLong() + "', '" + bot.getBotId() + "', '0')");
			Main.sql.update("UPDATE bots SET trades = trades + 1 WHERE botid = '" + bot.getBotId() + "'");
		}
	}

	@Override
	public void onUserSetReadyState(boolean ready) {
		if (ready) {
			respond();
		} else {
			trade.setReady(false);
		}
	}

	@Override
	public void onUserAddItem(ItemInfo schemaItem, Item invItem) {

	}

	@Override
	public void onUserRemoveItem(ItemInfo schemaItem, Item invItem) {

	}

	@Override
	public void onMessage(String message) {

	}

	@Override
	public void onNewVersion() {
		respond();
	}

	private int[] applicableItems() {
		final int[] items = { 0, 0 };
		onlyApplicable = true;
		String chat = "";
		for (final long child : trade.OtherTrade) {
			final TradeItem item = trade.OtherItems.get(child);
			final TradeItemDescription itemInfo = trade.OtherItems.getDescription(item.classId + "_" + item.instanceId);

			final Item record = trade.OtherInventory.getItem(child);
			final boolean itemok = !record.isNotCraftable && record.quality == 6;

			if (!accepted.containsKey(record.defIndex)) {
				final ResultSet rs = Main.sql.selectQuery("SELECT '' FROM items WHERE schemaid='" + record.defIndex + "'");
				try {
					accepted.put(record.defIndex, rs.first());
				} catch (final SQLException e) {
					e.printStackTrace();
				}
			}

			if (accepted.get(record.defIndex) && itemok) {
				items[0]++;
			} else if (itemok && record.defIndex == 5000) {
				items[1]++;
			} else {
				onlyApplicable = false;
				chat += "Item '" + itemInfo.name + " (" + record.defIndex + ")' not accepted\n";
			}
		}
		trade.sendMessage(chat);
		return items;
	}

	private void respond() {
		try {
			final int[] items = applicableItems();
			trade.sendMessage("Applicable Items: " + items[0]);

			int scrapA = items[1];
			int scrapB = items[0] / 2;
			int cScrap = 0;

			final List<Integer> reserved = bot.queueHandler.getReservedItems();
			final List<Long> alreadyTrade = new ArrayList<Long>();

			for (final long child : trade.MyTrade) {
				final Item item = trade.MyInventory.getItem(child);
				if (item.defIndex != 5000) {
					if (reserved.contains(item.defIndex)) {
						alreadyTrade.add(child);
						reserved.remove((Integer) item.defIndex);
						if (scrapA > 0) {
							scrapA--;
						} else {
							scrapB--;
						}
					} else {
						trade.removeItem(child);
						reservedGiven.remove((Integer) item.defIndex);
						slot--;
					}
				}
			}

			for (final long child : trade.MyItems.getIds()) {
				final Item item = trade.MyInventory.getItem(child);
				if (reserved.contains(item.defIndex) && !alreadyTrade.contains(item.id)) {
					trade.addItem(child, slot++);
					reservedGiven.add(item.defIndex);
					reserved.remove((Integer) item.defIndex);
					if (scrapA > 0) {
						scrapA--;
					} else {
						scrapB--;
					}
				}
			}

			if (reserved.size() > 0) {
				// This really shouldn't happen :/
				trade.sendMessage("Missing " + reserved.size() + " of your reserved items. Join the queue again to get them.");
			}

			for (final long child : trade.MyTrade) {
				final TradeItem item = trade.MyItems.get(child);
				if (item.classId == 2675) {
					cScrap++;
					if (cScrap > scrapB) {
						trade.removeItem(child);
						slot--;
						//sendChat("Remove some scrap?" + scrap + "/" + cScrap);
					}
				}
			}
			if (cScrap < scrapB) {
				for (final long child : trade.MyItems.getIds()) {
					if (trade.MyItems.get(child).classId == 2675 && !trade.MyTrade.contains(child)) {
						trade.addItem(child, slot++);
						cScrap++;
						if (cScrap >= scrapB) {
							break;
						}
					}
				}

				if (cScrap < scrapB) {
					trade.sendMessage("I don't have enough scrap to complete this transaction D:");
				}
			}
			check = scrapA == 0 && cScrap >= scrapB && scrapB >= 0 && items[0] % 2 == 0;

			if (trade.otherReady) {
				if (check && onlyApplicable) {
					trade.setReady(true);
				} else {
					trade.sendMessage("Please fix issues with your items before the trade can be completed");
					trade.setReady(false);
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}
}
