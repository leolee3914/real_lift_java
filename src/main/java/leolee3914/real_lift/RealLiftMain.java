package leolee3914.real_lift;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public final class RealLiftMain extends JavaPlugin implements Listener {

	public enum Movement {
		UP, DOWN, STOP,
	}

	public enum LiftSize {
		SIZE_1, SIZE_3, SIZE_5,
	}

	public final int CHEST_MENU_ITEM_CENTER_INDEX = 22;
	public final int[] CHEST_MENU_ITEM_PATH_UP_INDEX_LIST = {22, 13, 4, 5, 6, 15, 24, 33, 42, 51, 52, 53, 44, 35, 26, 17, 8};
	public final int[] CHEST_MENU_ITEM_PATH_DOWN_INDEX_LIST = {31, 40, 49, 48, 47, 38, 29, 20, 11, 2, 1, 0, 9, 18, 27, 36, 45};

	public final List<XzArray> QUEUE_CHECK_XZ_REDSTONE_LAMP = List.of(
		new XzArray(1, 0),
		new XzArray(0, 1),
		new XzArray(-1, 0),
		new XzArray(0, -1)
	);

	public final List<XzArray> QUEUE_CHECK_XZ_SIGN = List.of(
		new XzArray(1, 1),
		new XzArray(-1, 1),
		new XzArray(1, -1),
		new XzArray(-1, -1),
		new XzArray(2, 2),
		new XzArray(-2, 2),
		new XzArray(2, -2),
		new XzArray(-2, -2),
		new XzArray(3, 3),
		new XzArray(-3, 3),
		new XzArray(3, -3),
		new XzArray(-3, -3)
	);

	public record XzArray ( int x, int z ) {}

	public record FloorDataArray ( String floorName, int floorY ) {}

	public record ChestMenuData ( HashMap<Integer, Integer> invIndexToTargetYMap, Location pos, boolean fastMode ) {}

	private boolean multiple_floors_mode, enable3x3, enable5x5, tp_entity;

	private final HashMap<String, MovingLift> movingLift = new HashMap<>();

	private final WeakHashMap<Inventory, ChestMenuData> invToChestMenuDataMap = new WeakHashMap<>();

	@Override
	public void onEnable () {
		getServer().getPluginManager().registerEvents(this, this);

		saveDefaultConfig();

		multiple_floors_mode = getConfig().getBoolean("multiple_floors_mode");
		enable3x3 = getConfig().getBoolean("enable3x3");
		enable5x5 = getConfig().getBoolean("enable5x5");
		tp_entity = getConfig().getBoolean("tp_entity");

		saveResource("locale.yml", false);
		saveResource("locale-eng.yml", false);
		saveResource("locale-zh-TW.yml", false);

		YamlConfiguration locale = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "locale.yml"));
		Locale.setData(locale);

		getServer().getScheduler().scheduleSyncRepeatingTask(this, this::moveLift, 0, 1);
	}

	public void moveLift () {
		Iterator<Map.Entry<String, MovingLift>> movingLiftIterator = movingLift.entrySet().iterator();
		while ( movingLiftIterator.hasNext() ) {
			Map.Entry<String, MovingLift> movingLiftEntry = movingLiftIterator.next();
			String hash = movingLiftEntry.getKey();
			MovingLift lift = movingLiftEntry.getValue();

			Location liftPos = lift.position;
			if ( !liftPos.isWorldLoaded() ) {
				movingLiftIterator.remove();
				continue;
			}
			int liftPosX = liftPos.getBlockX();
			int liftPosY = liftPos.getBlockY();
			int liftPosZ = liftPos.getBlockZ();
			World world = liftPos.getWorld();
			if ( !isLiftColumn(world, liftPosX, liftPosY, liftPosZ) ) {
				movingLiftIterator.remove();
				continue;
			}

			boolean hasQueue = !lift.queue.isEmpty();
			if ( hasQueue ) {
				for ( QueueEntry queueEntry : lift.queue.values() ) {
					if ( --queueEntry.buttonBlinkTimer <= 0 ) {
						Location queueEntryPos = queueEntry.position;

						Block block = world.getBlockAt(queueEntryPos);
						if ( block.getState() instanceof Sign ) {
							world.spawnParticle(Particle.DUST, queueEntryPos.toBlockLocation().add(0.5, 0.5, 0.5), 1, new Particle.DustOptions(Color.RED, 1));
						} else {
							if ( block.getType() == Material.REDSTONE_LAMP ) {
								BlockData blockData = block.getBlockData();
								if ( blockData instanceof Lightable lightable ) {
									lightable.setLit(!lightable.isLit());
									block.setBlockData(lightable, false);
								}
							}
						}
						queueEntry.buttonBlinkTimer = 6;
					}
				}
			}

			if ( lift.unset ) {
				if ( hasQueue ) {
					boolean first = true;
					Iterator<Map.Entry<Integer, QueueEntry>> queueIterator = lift.queue.entrySet().iterator();
					while ( queueIterator.hasNext() ) {
						Map.Entry<Integer, QueueEntry> queueEntry = queueIterator.next();
						QueueEntry queueEntryObject = queueEntry.getValue();

						if ( first && lift.hasQueue ) {
							Location queueEntryPos = queueEntryObject.position;

							first = false;

							Block block = world.getBlockAt(queueEntryPos);
							if ( block.getType() == Material.REDSTONE_LAMP ) {
								BlockData blockData = block.getBlockData();
								if ( blockData instanceof Lightable lightable && lightable.isLit() ) {
									lightable.setLit(false);
									block.setBlockData(lightable, false);
								}
							}
							queueIterator.remove();
						} else {
							if ( liftPosY > queueEntryObject.getTargetY() ) {
								lift.movement = Movement.DOWN;
								lift.waiting = null;
								lift.targetY = queueEntryObject.getTargetY();
							} else if ( liftPosY < queueEntryObject.getTargetY() ) {
								lift.movement = Movement.UP;
								lift.waiting = null;
								lift.targetY = queueEntryObject.getTargetY();
							}
							lift.unset = false;
							lift.hasQueue = true;
							lift.fastMode = true;
							break;
						}
					}
				}
				if ( lift.unset ) {
					movingLiftIterator.remove();
				}
				continue;
			}

			if ( lift.waiting != null ) {
				if ( --lift.waiting <= 0 ) {
					lift.unset = true;
				}
				if ( lift.waiting == 20 && lift.playSound ) {
					world.playSound(liftPos, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 2);
				}
				continue;
			}

			checkLiftEntity(world, hash);
			for ( Entity entity : lift.insideEntities.values() ) {
				entity.setFallDistance(0);
			}
			boolean canMove = true;
			if ( lift.movement == Movement.UP ) {
				for ( Entity p : lift.insideEntities.values() ) {
					if ( p instanceof Player ) {
						p.setVelocity(new Vector(0, 0.8, 0));
						if ( p.getLocation().getY() < (liftPosY - 2.4) ) {
							canMove = false;
						}
					}
				}
			} else if ( lift.movement == Movement.DOWN ) {
				for ( Entity p : lift.insideEntities.values() ) {
					if ( p instanceof Player ) {
						p.setVelocity(new Vector(0, -0.4, 0));
						if ( p.getLocation().getY() > (liftPosY - 3) ) {
							canMove = false;
						}
					}
				}
			}
			int offset = switch ( lift.liftSize ) {
				case SIZE_5 -> 2;
				case SIZE_3 -> 1;
				default -> 0;
			};

			if ( hasQueue && !lift.fastMode ) {
				lift.fastMode = true;
			}
			if ( lift.fastMode ) {
				if ( swapBlock(lift, lift.targetY - liftPosY, offset) ) {
					lift.moving = true;
					continue;
				} else {
					lift.fastMode = false;
				}
			}
			ArrayList<Material> fillerBlockIds = new ArrayList<>();
			boolean stop = false;
			if ( canMove && lift.movement == Movement.UP ) {
				if ( (liftPosY + 1) >= world.getMaxHeight() || liftPosY == lift.targetY ) {
					stop = true;
				}
				for ( int X = -offset; X <= offset; ++X ) {
					for ( int Z = -offset; Z <= offset; ++Z ) {
						Material curFillerBlockId = world.getBlockAt(liftPosX + X, liftPosY + 1, liftPosZ + Z).getType();
						if ( stop || (curFillerBlockId != Material.AIR && curFillerBlockId != Material.GLASS) ) {
							stop = true;
							X = offset; break;//break 2
						}
						fillerBlockIds.add(curFillerBlockId);
					}
				}
			} else if ( canMove && lift.movement == Movement.DOWN ) {
				if ( (liftPosY - 5) < world.getMinHeight() || liftPosY == lift.targetY ) {
					stop = true;
				}
				for ( int X = -offset; X <= offset; ++X ) {
					for ( int Z = -offset; Z <= offset; ++Z ) {
						Material curFillerBlockId = world.getBlockAt(liftPosX + X, liftPosY - 6, liftPosZ + Z).getType();
						if ( stop || (curFillerBlockId != Material.AIR && curFillerBlockId != Material.GLASS) ) {
							stop = true;
							X = offset; break;//break 2
						}
						fillerBlockIds.add(curFillerBlockId);
					}
				}
			} else {
				canMove = false;
			}
			if ( stop ) {
				if ( !lift.moving ) {
					lift.unset = true;
					continue;
				}
				lift.movement = Movement.STOP;
				lift.waiting = 40;
				lift.playSound = true;
				continue;
			}
			if ( canMove ) {
				if ( getLiftSizeByPosition(world, liftPosX, liftPosY, liftPosZ) != lift.liftSize ) {
					movingLiftIterator.remove();
					continue;
				}
				if ( lift.movement == Movement.UP ) {
					int fillerBlockIdsIndex = 0;
					for ( int X = -offset; X <= offset; ++X ) {
						for ( int Z = -offset; Z <= offset; ++Z ) {
							Material setBlockId = ((X == 0 && Z == 0) ? Material.GOLD_BLOCK : Material.IRON_BLOCK);
							world.getBlockAt(liftPosX + X, liftPosY, liftPosZ + Z).setType(Material.AIR, false);
							world.getBlockAt(liftPosX + X, liftPosY + 1, liftPosZ + Z).setType(setBlockId, false);
							world.getBlockAt(liftPosX + X, liftPosY - 5, liftPosZ + Z).setType(fillerBlockIds.get(fillerBlockIdsIndex++), false);
							world.getBlockAt(liftPosX + X, liftPosY - 4, liftPosZ + Z).setType(setBlockId, false);
						}
					}
					for ( Entity entity : lift.insideEntities.values() ) {
						if ( !(entity instanceof Player) ) {
							entity.teleport(entity.getLocation().add(0, 1, 0));
						}
					}
					liftPos.setY(liftPosY + 1);
					lift.moving = true;
				} else if ( lift.movement == Movement.DOWN ) {
					int fillerBlockIdsIndex = 0;
					for ( int X = -offset; X <= offset; ++X ) {
						for ( int Z = -offset; Z <= offset; ++Z ) {
							Material setBlockId = ((X == 0 && Z == 0) ? Material.GOLD_BLOCK : Material.IRON_BLOCK);
							world.getBlockAt(liftPosX + X, liftPosY, liftPosZ + Z).setType(fillerBlockIds.get(fillerBlockIdsIndex++), false);
							world.getBlockAt(liftPosX + X, liftPosY - 1, liftPosZ + Z).setType(setBlockId, false);
							world.getBlockAt(liftPosX + X, liftPosY - 5, liftPosZ + Z).setType(Material.AIR, false);
							world.getBlockAt(liftPosX + X, liftPosY - 6, liftPosZ + Z).setType(setBlockId, false);
						}
					}
					for ( Entity entity : lift.insideEntities.values() ) {
						if ( !(entity instanceof Player) ) {
							entity.teleport(entity.getLocation().add(0, -1, 0));
						}
					}
					liftPos.setY(liftPosY - 1);
					lift.moving = true;
				}
			}
		}
	}

	public boolean swapBlock ( @NotNull MovingLift lift, int h, int offset ) {
		Location liftPos = lift.position;
		int posBlockY = liftPos.getBlockY();
		if ( !liftPos.isWorldLoaded() ) {
			return false;
		}
		World world = liftPos.getWorld();
		if ( lift.movement == Movement.UP ) {
			if ( h < 6 || (posBlockY + 6) > (world.getMaxHeight() - 1) ) {
				return false;
			}
			h = 6;
		} else if ( lift.movement == Movement.DOWN ) {
			if ( h > -6 || (posBlockY - 5 - 6) < world.getMinHeight() ) {
				return false;
			}
			h = -6;
		} else {
			return false;
		}
		int posBlockX = liftPos.getBlockX();
		int posBlockZ = liftPos.getBlockZ();
		int minY = posBlockY + h - 5;
		int maxY = posBlockY + h;
		ArrayList<Material> fillerBlockIds = new ArrayList<>();
		for ( int X = -offset; X <= offset; ++X ) {
			for ( int Y = minY; Y <= maxY; ++Y ) {
				for ( int Z = -offset; Z <= offset; ++Z ) {
					Material curFillerBlockId = world.getBlockAt(posBlockX + X, Y, posBlockZ + Z).getType();
					if ( curFillerBlockId != Material.AIR && curFillerBlockId != Material.GLASS ) {
						return false;
					}
					fillerBlockIds.add(curFillerBlockId);
				}
			}
		}
		for ( Entity entity : lift.insideEntities.values() ) {
			entity.teleport(entity.getLocation().add(0, h, 0));
		}
		int fillerBlockIdsIndex = 0;
		for ( int X = -offset; X <= offset; ++X ) {
			for ( int Y = posBlockY - 5; Y <= posBlockY; ++Y ) {
				for ( int Z = -offset; Z <= offset; ++Z ) {
					world.getBlockAt(posBlockX + X, Y, posBlockZ + Z).setType(fillerBlockIds.get(fillerBlockIdsIndex++), false);
				}
			}
		}
		for ( int X = -offset; X <= offset; ++X ) {
			for ( int Z = -offset; Z <= offset; ++Z ) {
				Material setBlockId = (X == 0 && Z == 0) ? Material.GOLD_BLOCK : Material.IRON_BLOCK;
				world.getBlockAt(posBlockX + X, posBlockY + h, posBlockZ + Z).setType(setBlockId, false);

				world.getBlockAt(posBlockX + X, posBlockY + h - 1, posBlockZ + Z).setType(Material.AIR, false);
				world.getBlockAt(posBlockX + X, posBlockY + h - 2, posBlockZ + Z).setType(Material.AIR, false);
				world.getBlockAt(posBlockX + X, posBlockY + h - 3, posBlockZ + Z).setType(Material.AIR, false);
				world.getBlockAt(posBlockX + X, posBlockY + h - 4, posBlockZ + Z).setType(Material.AIR, false);

				world.getBlockAt(posBlockX + X, posBlockY + h - 5, posBlockZ + Z).setType(setBlockId, false);
			}
		}
		liftPos.setY(posBlockY + h);

		return true;
	}

	@EventHandler(ignoreCancelled = false)
	public void tap ( PlayerInteractEvent e ) {
		if ( e.getAction() != Action.RIGHT_CLICK_BLOCK ) {
			return;
		}
		Player p = e.getPlayer();
		if ( p.isSneaking() ) {
			return;
		}
		Block b = e.getClickedBlock();
		if ( b == null ) {
			return;
		}
		Location bPos = b.getLocation().toBlockLocation();
		World world = b.getWorld();
		Material blockId = b.getType();
		if ( blockId == Material.GOLD_BLOCK ) {
			Location liftPos = bPos.clone();
			if ( bPos.getY() < p.getLocation().getY() ) {
				liftPos.add(0, 5, 0);
			}
			if ( isLiftColumn(world, liftPos.getBlockX(), liftPos.getBlockY(), liftPos.getBlockZ()) ) {
				e.setCancelled(true);
				String hash = getLiftHash(world, liftPos.getBlockX(), liftPos.getBlockZ());
				@Nullable MovingLift lift = movingLift.get(hash);
				if ( lift == null ) {
					if ( multiple_floors_mode ) {
						int worldMaxY = world.getMaxHeight();
						int worldMinY = world.getMinHeight();
						int liftMinY = worldMinY + 5;

						ArrayList<FloorDataArray> floorDataListUp = new ArrayList<>();
						ArrayList<FloorDataArray> floorDataListDown = new ArrayList<>();
						String currentHeightFloorName = null;
						boolean fastMode = false;
						int liftPosX = liftPos.getBlockX();
						int liftPosY = liftPos.getBlockY();
						int liftPosZ = liftPos.getBlockZ();
						for ( int y = worldMaxY - 1; y >= liftMinY; --y ) {
							boolean nextY = false;
							for ( XzArray xzArray : QUEUE_CHECK_XZ_SIGN ) {
								if ( world.getBlockAt(liftPosX + xzArray.x, y - 3, liftPosZ + xzArray.z).getState() instanceof Sign signBlockState ) {
									for ( Side face : Arrays.asList(Side.FRONT, Side.BACK) ) {
										SignSide signSide = signBlockState.getSide(face);
										if ( PlainTextComponentSerializer.plainText().serialize(signSide.line(0)).equalsIgnoreCase("[lift]") ) {
											String floorText = "§e" + PlainTextComponentSerializer.plainText().serialize(signSide.line(1)) + "§r§e" + " (" + Locale.get(Locale.HEIGHT) + ":" + (y - 4) + ")";

											if ( y > liftPosY ) {
												floorDataListUp.add(new FloorDataArray(floorText, y));
											} else if ( y < liftPosY ) {
												floorDataListDown.add(new FloorDataArray(floorText, y));
											} else {
												currentHeightFloorName = floorText;
											}

											if ( !fastMode && PlainTextComponentSerializer.plainText().serialize(signSide.line(2)).equalsIgnoreCase("fast") ) {
												fastMode = true;
											}
											nextY = true;
											break;
										}
									}
								}
								if ( nextY ) {
									break;
								}
							}
						}
						if ( !floorDataListUp.isEmpty() || !floorDataListDown.isEmpty() || currentHeightFloorName != null ) {
							if ( p.getOpenInventory().getType() == InventoryType.CHEST ) {
								return;
							}

							HashMap<Integer, Integer> invIndexToTargetYMap = new HashMap<>();

							p.closeInventory();
							Inventory inv = getServer().createInventory(null, 54, Component.text(Locale.get(fastMode ? Locale.MENU_TITLE_LIFT_FAST_MODE : Locale.MENU_TITLE_LIFT)));

							if ( currentHeightFloorName != null ) {
								inv.setItem(CHEST_MENU_ITEM_CENTER_INDEX, getItem(Material.REDSTONE, 1, currentHeightFloorName + " §c[* " + Locale.get(Locale.CURRENT_HEIGHT) + " *]"));
							}

							int floorDataListUpLastIndex = Math.min(CHEST_MENU_ITEM_PATH_UP_INDEX_LIST.length, floorDataListUp.size() + 1 + (currentHeightFloorName == null ? 0 : 1)) - 1;
							for ( int invIndexListIndex = (currentHeightFloorName == null ? 0 : 1), floorDataIndex = floorDataListUp.size() - 1; invIndexListIndex <= floorDataListUpLastIndex; ++invIndexListIndex, --floorDataIndex ) {
								int invIndex = CHEST_MENU_ITEM_PATH_UP_INDEX_LIST[invIndexListIndex];

								if ( invIndexListIndex != floorDataListUpLastIndex ) {
									FloorDataArray floorDataArray = floorDataListUp.get(floorDataIndex);

									invIndexToTargetYMap.put(invIndex, floorDataArray.floorY);
									inv.setItem(invIndex, getItem(Material.OAK_SIGN, 1, floorDataArray.floorName));
								} else {
									invIndexToTargetYMap.put(invIndex, worldMaxY - 1);
									inv.setItem(invIndex, getItem(Material.SEA_LANTERN, 1, "§c" + Locale.get(Locale.HIGHEST) + " §e" + " (" + Locale.get(Locale.HEIGHT) + ":" + (worldMaxY - 5) + ")"));
								}
							}

							int floorDataListDownLastIndex = Math.min(CHEST_MENU_ITEM_PATH_DOWN_INDEX_LIST.length, floorDataListDown.size() + 1) - 1;
							for ( int index = 0; index <= floorDataListDownLastIndex; ++index ) {
								int invIndex = CHEST_MENU_ITEM_PATH_DOWN_INDEX_LIST[index];

								if ( index != floorDataListDownLastIndex ) {
									FloorDataArray floorDataArray = floorDataListDown.get(index);

									invIndexToTargetYMap.put(invIndex, floorDataArray.floorY);
									inv.setItem(invIndex, getItem(Material.OAK_SIGN, 1, floorDataArray.floorName));
								} else {
									invIndexToTargetYMap.put(invIndex, liftMinY);
									inv.setItem(invIndex, getItem(Material.SEA_LANTERN, 1, "§c" + Locale.get(Locale.LOWEST) + " §e" + " (" + Locale.get(Locale.HEIGHT) + ":" + (worldMinY + 1) + ")"));
								}
							}

							for ( int index = 0; index < inv.getSize(); ++index ) {
								if ( inv.getItem(index) == null ) {
									inv.setItem(index, getItem(Material.BLACK_STAINED_GLASS_PANE, 1, ""));
								}
							}

							invToChestMenuDataMap.put(inv, new ChestMenuData(invIndexToTargetYMap, liftPos.clone(), fastMode));
							p.openInventory(inv);
							return;
						}
					}
					Movement movement = ((bPos.getY() > p.getLocation().getY()) ? Movement.UP : Movement.DOWN);
					movingLift.put(hash, new MovingLift(
						liftPos.clone(),
						movement,
						null,
						false,
						false,
						(movement == Movement.UP) ? world.getMaxHeight() - 1 : world.getMinHeight() + 5,
						false,
						getLiftSizeByPosition(world, liftPos.getBlockX(), liftPos.getBlockY(), liftPos.getBlockZ()),
						false
					));
				} else if ( lift.waiting != null ) {
					p.sendMessage("§e!!! " + Locale.get(Locale.LIFT_WAITING) + " !!!");
				} else if ( lift.insideEntities.containsKey(p.getUniqueId()) ) {
					lift.movement = Movement.STOP;
					lift.waiting = 40;
					p.sendMessage("§a> " + Locale.get(Locale.STOPPED_LIFT));
				}
			}
		} else if ( blockId == Material.REDSTONE_LAMP ) {
			boolean cancel = addToQueue(p, world, bPos.getBlockX(), bPos.getBlockY(), bPos.getBlockZ(), QUEUE_CHECK_XZ_REDSTONE_LAMP);
			if ( cancel ) {
				world.playSound(bPos, Sound.BLOCK_LEVER_CLICK, 1f, 0.6f);
				e.setCancelled(true);
			}
		} else if ( b.getState() instanceof Sign signState ) {
			if (
				PlainTextComponentSerializer.plainText().serialize(signState.getSide(Side.FRONT).line(0)).equalsIgnoreCase("[lift]")
			||
				PlainTextComponentSerializer.plainText().serialize(signState.getSide(Side.BACK).line(0)).equalsIgnoreCase("[lift]")
			) {
				e.setCancelled(true);
				if ( addToQueue(p, world, bPos.getBlockX(), bPos.getBlockY(), bPos.getBlockZ(), QUEUE_CHECK_XZ_SIGN) ) {
					world.playSound(bPos, Sound.BLOCK_LEVER_CLICK, 1f, 0.6f);
				}
			}
		}
	}

	private static ItemStack getItem ( Material id, int count, String name ) {
		ItemStack item = ItemStack.of(id, count);
		ItemMeta itemMeta = item.getItemMeta();
		itemMeta.itemName(Component.text(name));
		item.setItemMeta(itemMeta);
		return item;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void invClick ( InventoryClickEvent e ) {
		if ( invToChestMenuDataMap.get(e.getClickedInventory()) != null ) {
			e.setCancelled(true);

			HumanEntity whoClicked = e.getWhoClicked();
			if ( !(whoClicked instanceof Player p) ) {
				return;
			}

			ChestMenuData chestMenuData = invToChestMenuDataMap.get(e.getClickedInventory());
			int slot = e.getSlot();

			@Nullable Integer targetY = chestMenuData.invIndexToTargetYMap.get(slot);
			if ( targetY == null ) {
				p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.6f);
				return;
			}

			if ( !chestMenuData.pos.isWorldLoaded() ) {
				p.closeInventory();
				return;
			}

			Location liftPos = chestMenuData.pos;
			World world = liftPos.getWorld();
			String hash = getLiftHash(world, liftPos.getBlockX(), liftPos.getBlockZ());

			p.closeInventory();

			if ( movingLift.get(hash) == null && isLiftColumn(world, liftPos.getBlockX(), liftPos.getBlockY(), liftPos.getBlockZ()) ) {
				movingLift.put(hash, new MovingLift(
					liftPos.clone(),
					targetY > liftPos.getBlockY() ? Movement.UP : Movement.DOWN,
					null,
					false,
					true,
					targetY,
					false,
					getLiftSizeByPosition(world, liftPos.getBlockX(), liftPos.getBlockY(), liftPos.getBlockZ()),
					chestMenuData.fastMode
				));
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
			} else {
				p.sendMessage("§c!!! " + Locale.get(Locale.LIFT_MOVED) + " !!!");
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void invDrag ( InventoryDragEvent e ) {
		if ( invToChestMenuDataMap.get(e.getInventory()) != null ) {
			e.setCancelled(true);
		}
	}

	public boolean addToQueue ( Player p, World world, int blockX, int blockY, int blockZ, List<XzArray> xzArrayList ) {
		int worldMinY = world.getMinHeight();
		int worldMaxY = world.getMaxHeight();
		if ( blockY >= (worldMinY + 2) && blockY <= (worldMaxY - 4) ) {
			for ( XzArray xzArray : xzArrayList ) {
				int x = blockX + xzArray.x;
				int z = blockZ + xzArray.z;
				for ( int y = worldMinY + 5; y < worldMaxY; ++y ) {
					if ( isLiftColumn(world, x, y, z) ) {
						int targetY = blockY + 3;
						if ( targetY == y ) {
							p.sendMessage("§a> " + Locale.get(Locale.LIFT_ARRIVED));
							return true;
						}
						String hash = getLiftHash(world, x, z);
						Location pos = new Location(world, x, y, z);

						@Nullable MovingLift lift = movingLift.get(hash);
						if ( lift == null ) {
							lift = new MovingLift(
								pos,
								Movement.STOP,
								null,
								false,
								false,
								y,
								true,
								getLiftSizeByPosition(world, x, y, z),
								true
							);
							movingLift.put(hash, lift);
						}
						lift.fastMode = true;

						if ( lift.queue.get(targetY) == null ) {
							lift.queue.put(targetY, new QueueEntry(new Location(world, blockX, blockY, blockZ), targetY));
						}

						return true;
					}
				}
			}
		}
		return false;
	}

	public void checkLiftEntity ( World world, String hash ) {
		@Nullable MovingLift lift = movingLift.get(hash);
		if ( lift == null ) {
			return;
		}

		Location pos = lift.position;
		HashMap<UUID, Entity> insideEntities = new HashMap<>();
		int offset = switch ( lift.liftSize ) {
			case LiftSize.SIZE_5 -> 2;
			case LiftSize.SIZE_3 -> 1;
			default -> 0;
		};
		var bb = new BoundingBox(
			pos.getBlockX() - offset,
			pos.getBlockY() - 6.5,
			pos.getBlockZ() - offset,
			pos.getBlockX() + offset + 1,
			pos.getBlockY(),
			pos.getBlockZ() + offset + 1
		);
		for ( Entity entity : world.getNearbyEntities(bb) ) {
			if ( entity instanceof Player player ) {
				if ( player.getGameMode() == GameMode.SPECTATOR ) {
					continue;
				}
			} else {
				if ( !tp_entity ) {
					continue;
				}
			}
			insideEntities.put(entity.getUniqueId(), entity);
		}
		lift.insideEntities = insideEntities;
	}

	public LiftSize getLiftSizeByPosition ( World world, int x, int y, int z ) {
		if ( isLift_9(world, x, y, z) ) {
			if ( isLift_25(world, true, x, y, z) ) {
				return LiftSize.SIZE_5;
			}
			return LiftSize.SIZE_3;
		}
		return LiftSize.SIZE_1;
	}

	public boolean isLiftColumnIronBlock ( World world, int x, int y, int z ) {
		return isLiftColumn(world, x, y, z, Material.IRON_BLOCK);
	}

	public boolean isLiftColumn ( World world, int x, int y, int z ) {
		return isLiftColumn(world, x, y, z, Material.GOLD_BLOCK);
	}

	public boolean isLiftColumn ( World world, int x, int y, int z, Material blockId ) {
		if ( y > (world.getMaxHeight() - 1) ) {
			return false;
		}

		int worldMinY = world.getMinHeight();

		Material[] columnBlockIdList = {
			blockId,
			Material.AIR,
			Material.AIR,
			Material.AIR,
			Material.AIR,
			blockId,
		};
		for ( int i = 0; i < columnBlockIdList.length; ++i ) {
			Material curBlockId = columnBlockIdList[i];
			int yToCheck = y - i;
			if ( yToCheck < worldMinY || world.getBlockAt(x, yToCheck, z).getType() != curBlockId ) {
				return false;
			}
		}
		return true;
	}

	public boolean isLift_25 ( World world, boolean isLift_9, int x, int y, int z ) {
		if ( !enable5x5 ) {
			return false;
		}

		for ( int X = -2; X <= 2; ++X ) {
			for ( int Z = -2; Z <= 2; ++Z ) {
				if ( X == -2 || X == 2 || Z == -2 || Z == 2 ) {
					if ( !isLiftColumnIronBlock(world, x + X, y, z + Z) ) {
						return false;
					}
				}
			}
		}
		return isLift_9 || isLift_9(world, x, y, z);
	}

	public boolean isLift_9 ( World world, int x, int y, int z ) {
		if ( !enable3x3 ) {
			return false;
		}

		for ( int X = -1; X <= 1; ++X ) {
			for ( int Z = -1; Z <= 1; ++Z ) {
				if ( X != 0 || Z != 0 ) {
					if ( !isLiftColumnIronBlock(world, x + X, y, z + Z) ) {
						return false;
					}
				} else {
					if ( !isLiftColumn(world, x, y, z) ) {
						return false;
					}
				}
			}
		}
		return true;
	}

	public static String getLiftHash ( World world, int x, int z ) {
		return x + world.getUID().toString() + z;
	}

	@EventHandler(ignoreCancelled = true)
	public void pvp ( EntityDamageEvent e ) {
		EntityDamageEvent.DamageCause cause = e.getCause();
		if ( cause == EntityDamageEvent.DamageCause.FALL || cause == EntityDamageEvent.DamageCause.SUFFOCATION ) {
			UUID uuid = e.getEntity().getUniqueId();
			for ( MovingLift lift : movingLift.values() ) {
				if ( lift.insideEntities.containsKey(uuid) ) {
					e.setCancelled(true);
				}
			}
		}
	}

}
