package raton.meme.hcf.economy;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.doctordark.utils.InventoryUtils;
import com.doctordark.utils.JavaUtils;
import com.doctordark.utils.internal.com.doctordark.base.BasePlugin;

import raton.meme.hcf.HCF;
import raton.meme.hcf.listener.Crowbar;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Listener that allows {@link Player}s to buy or sell items via signs.
 */
public class ShopSignListener implements Listener {

    private static final long SIGN_TEXT_REVERT_TICKS = 100L;
    private static final Pattern ALPHANUMERIC_REMOVER = Pattern.compile("[^A-Za-z0-9]");

    private final HCF plugin;

    public ShopSignListener(HCF plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            BlockState state = block.getState();
            if (state instanceof Sign) {
                Sign sign = (Sign) state;
                String[] lines = sign.getLines();

                Integer quantity = JavaUtils.tryParseInt(lines[2]);
                if (quantity == null)
                    return;

                Integer price = JavaUtils.tryParseInt(ALPHANUMERIC_REMOVER.matcher(lines[3]).replaceAll(""));
                if (price == null)
                    return;

                ItemStack stack;
                if (lines[1].equalsIgnoreCase("Crowbar")) {
                    stack = new Crowbar().getItemIfPresent();
                } else if(lines[1].equalsIgnoreCase("Gopple")) {
                	stack = new ItemStack(Material.GOLDEN_APPLE, 1, (short) 0, (byte) 1);
                } else if(lines[1].equalsIgnoreCase("Sharp Sword")) {
                	ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
                	ItemMeta meta = sword.getItemMeta();
                	sword.addEnchantment(Enchantment.DAMAGE_ALL, 4);
                	sword.addEnchantment(Enchantment.DURABILITY, 3);
                	
                	meta.setDisplayName(ChatColor.RED + "Sharpness Sword");
                	sword.setItemMeta(meta);
                	stack = sword;
                } else if(lines[1].equalsIgnoreCase("Power Bow")) {
                	ItemStack bow = new ItemStack(Material.BOW, 1);
                	ItemMeta meta = bow.getItemMeta();
                	bow.addEnchantment(Enchantment.ARROW_DAMAGE, 5);
                	bow.addEnchantment(Enchantment.ARROW_INFINITE, 1);
                	bow.addEnchantment(Enchantment.ARROW_FIRE, 1);
                	bow.addEnchantment(Enchantment.DURABILITY, 3);
                	
                	meta.setDisplayName(ChatColor.RED + "Power Bow");
                	bow.setItemMeta(meta);
                	
                	stack = bow;
                } else if ((stack = BasePlugin.getPlugin().getItemDb().getItem(ALPHANUMERIC_REMOVER.matcher(lines[1]).replaceAll(""), quantity)) == null) {
                    return;
                }

                // Final handling of shop.
                Player player = event.getPlayer();
                String[] fakeLines = Arrays.copyOf(sign.getLines(), 4);
                if (lines[0].contains("Sell") && lines[0].contains(ChatColor.RED.toString())) {
                    int sellQuantity = Math.min(quantity, InventoryUtils.countAmount(player.getInventory(), stack.getType(), stack.getDurability()));
                    if (sellQuantity <= 0) {
                        fakeLines[0] = ChatColor.RED + "Not carrying any";
                        fakeLines[2] = ChatColor.RED + "on you.";
                        fakeLines[3] = "";
                    } else {
                        // Recalculate the price.
                        int newPrice = (int) (((double) price / (double) quantity) * (double) sellQuantity);
                        fakeLines[0] = ChatColor.GREEN + "Sold " + sellQuantity;
                        fakeLines[3] = ChatColor.GREEN + "for " + EconomyManager.ECONOMY_SYMBOL + newPrice;

                        plugin.getEconomyManager().addBalance(player.getUniqueId(), newPrice);
                        InventoryUtils.removeItem(player.getInventory(), stack.getType(), stack.getData().getData(), sellQuantity);
                        player.updateInventory();
                    }
                } else if (lines[0].contains("Buy") && lines[0].contains(ChatColor.GREEN.toString())) {
                    if (price > plugin.getEconomyManager().getBalance(player.getUniqueId())) {
                        fakeLines[0] = ChatColor.RED + "Cannot afford";
                    } else {
                        fakeLines[0] = ChatColor.GREEN + "Item bought";
                        fakeLines[3] = ChatColor.GREEN + "for " + EconomyManager.ECONOMY_SYMBOL + price;
                        plugin.getEconomyManager().subtractBalance(player.getUniqueId(), price);

                        World world = player.getWorld();
                        Location location = player.getLocation();
                        Map<Integer, ItemStack> excess = player.getInventory().addItem(stack);
                        for (Map.Entry<Integer, ItemStack> excessItemStack : excess.entrySet()) {
                            world.dropItemNaturally(location, excessItemStack.getValue());
                        }

                        player.setItemInHand(player.getItemInHand()); // resend held item packet.
                    }
                } else {
                    return;
                }

                event.setCancelled(true);
                BasePlugin.getPlugin().getSignHandler().showLines(player, sign, fakeLines, SIGN_TEXT_REVERT_TICKS, true);
            }
        }
    }
}
