package uk.co.thomasc.scrapbanktf.scrap.listeners;

import java.util.HashMap;
import java.util.Map;

import org.json.simple.parser.ParseException;

import uk.co.thomasc.scrapbanktf.Bot;
import uk.co.thomasc.scrapbanktf.Main;
import uk.co.thomasc.scrapbanktf.inventory.Inventory;
import uk.co.thomasc.scrapbanktf.inventory.Item;
import uk.co.thomasc.scrapbanktf.scrap.QueueHandler;
import uk.co.thomasc.scrapbanktf.trade.StatusObj;
import uk.co.thomasc.scrapbanktf.trade.TradeInventory;
import uk.co.thomasc.scrapbanktf.trade.TradeListener;
import uk.co.thomasc.scrapbanktf.util.ConsoleColor;
import uk.co.thomasc.scrapbanktf.util.ItemInfo;
import uk.co.thomasc.scrapbanktf.util.MutableInt;
import uk.co.thomasc.scrapbanktf.util.Util;

public class ExchangeTrade extends TradeListener {
	private int scrapDiff = 0;
	private int itemDiff = 0;
	private boolean done = false;

	private boolean otherOK = false;
	private boolean OK = false;

	public ExchangeTrade(Bot bot) {
		super(bot);
	}

	@Override
	public void onTimeout() {
		Util.printConsole("Exchange trade timeout, ignore", bot, ConsoleColor.Red, true);
	}

	@Override
	public void onError(int eid) {
		Util.printConsole("Error(" + eid + ") while exchanging items with another bot", bot, ConsoleColor.Red);
		if (bot.queueHandler.needItemsBool) {
			bot.queueHandler.reQueue();
		}
		bot.currentTrade = null;
	}

	private Map<Integer, MutableInt> countItems(TradeInventory items, Inventory inv) {
		final Map<Integer, MutableInt> response = new HashMap<Integer, MutableInt>();
		for (final long child : items.getIds()) {
			final Item item = inv.getItem(child);
			if (response.containsKey(item.defIndex)) {
				response.get(item.defIndex).increment();
			} else {
				response.put(item.defIndex, new MutableInt());
			}
		}
		return response;
	}

	@Override
	public void onAfterInit() {
		done = false;
		itemDiff = 0;
		scrapDiff = 0;

		final Map<Integer, MutableInt> otherCount = countItems(trade.OtherItems, trade.OtherInventory);
		final Map<Integer, MutableInt> myCount = countItems(trade.MyItems, trade.MyInventory);

		for (final int i : bot.toTrade) {
			myCount.get(i).decrement();
		}
		for (final int i : bot.queueHandler.getReservedItems()) {
			if (myCount.containsKey(i)) {
				myCount.get(i).decrement();
			}
		}

		for (final long child : trade.MyItems.getIds()) {
			final Item item = trade.MyInventory.getItem(child);

			if (bot.toTrade.contains(item.defIndex) || (myCount.containsKey(item.defIndex) ? myCount.get(item.defIndex).get() : 0) - (otherCount.containsKey(item.defIndex) ? otherCount.get(item.defIndex).get() : 0) > 1) {
				if (item.defIndex == 5000) {
					scrapDiff--;
				} else {
					itemDiff--;
				}
				Util.printConsole(slot + ", added " + item.defIndex, bot, ConsoleColor.White, true);
				trade.addItem(item.id, slot++);
				if (myCount.containsKey(item.defIndex)) {
					myCount.get(item.defIndex).decrement();
					if (otherCount.containsKey(item.defIndex)) {
						otherCount.get(item.defIndex).increment();
					} else {
						otherCount.put(item.defIndex, new MutableInt());
					}
				}
				bot.toTrade.remove((Integer) item.defIndex);
			}
		}

		OK = true;
		trade.sendMessage("k");
		Util.printConsole("Sent OK " + bot.steamClient.getSteamId(), bot, ConsoleColor.White, true);
		bot.toTrade.clear();
	}

	@Override
	public void onUserAccept() {
		OnFinished();
	}

	@Override
	public void onUserSetReadyState(boolean ready) {
		if (trade.meReady) {
			OnFinished();
		} else if (ready) {
			while (!trade.setReady(true)) {
				;
			}
		}
	}

	public void OnFinished() {
		done = false;
		try {
			StatusObj js = null;
			while (!done) {
				try {
					Thread.sleep(500);
				} catch (final InterruptedException e) {
					e.printStackTrace();
				}
				js = new StatusObj(trade.acceptTrade());
				done = js.trade_status == 1 || js.me.confirmed;
			}
			if (js.success == true) {
				Util.printConsole("Success " + bot.steamClient.getSteamId(), bot, ConsoleColor.Yellow, true);
				return;
			}
		} catch (final ParseException e) {
			e.printStackTrace();
		}
		Util.printConsole("Failure " + bot.steamClient.getSteamId(), bot, ConsoleColor.Yellow, true);
	}

	@Override
	public void onComplete() {
		Main.sql.update("UPDATE bots SET items = items + " + itemDiff + ", scrap = scrap + " + scrapDiff + " WHERE botid = '" + bot.getBotId() + "'");
		if (bot.queueHandler.neededItems.size() > 0) {
			QueueHandler.needItems.add(bot);
			Util.printConsole("Still need to acquire " + bot.queueHandler.neededItems.size() + " items before trading", bot, ConsoleColor.Yellow);
		} else if (bot.queueHandler.needItemsBool) {
			bot.queueHandler.needItemsBool = false;
			bot.queueHandler.gotItems = true;
		}
		if (bot.queueHandler.currentTrader != null && bot.queueHandler.currentTrader.getSteamId() == null) {
			bot.queueHandler.currentTrader = null;
		}
		bot.currentTrade = null;
	}

	@Override
	public void onUserAddItem(ItemInfo schemaItem, Item invItem) {
		if (invItem.defIndex == 5000) {
			scrapDiff++;
		} else {
			itemDiff++;
		}
	}

	@Override
	public void onUserRemoveItem(ItemInfo schemaItem, Item invItem) {
		Util.printConsole("wat, removed item?", bot, ConsoleColor.Cyan, true);
	}

	@Override
	public void onMessage(String message) {
		otherOK = true;
	}

	@Override
	public void onNewVersion() {
		if (!done && OK && otherOK && !trade.meReady) {
			while (!trade.setReady(true)) {
				;
			}
		}
	}
}
