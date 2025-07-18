package logisticspipes.utils.gui;

import java.util.*;
import java.util.Map.Entry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.client.FMLClientHandler;
import logisticspipes.config.Configs;
import logisticspipes.interfaces.ISpecialItemRenderer;
import logisticspipes.utils.Color;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.item.ItemStackRenderer;
import logisticspipes.utils.item.ItemStackRenderer.DisplayAmount;
import logisticspipes.utils.tuples.Pair;
import lombok.Getter;

public class ItemDisplay {

    public enum DisplayOption {
        ID,
        ID_DOWN,
        SIZE,
        SIZE_DOWN,
        NAME,
        NAME_DOWN,
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/icons.png");
    private static final int PANELSIZEX = 20;
    private static final int PANELSIZEY = 20;

    private ItemIdentifierStack selectedItem = null;
    public final LinkedList<ItemIdentifierStack> _allItems = new LinkedList<>();
    private final Map<Pair<Integer, Integer>, ItemIdentifierStack> map = new HashMap<>();

    @Getter
    private int page = 0;

    private int maxPage = 0;
    private int requestCount = 1;
    private Object[] tooltip = null;
    private boolean listbyserver = false;

    private final IItemSearch search;
    private final FontRenderer fontRenderer;
    private final LogisticsBaseGuiScreen screen;
    private final ISpecialItemRenderer renderer;
    private int left, top, height, width;
    private int itemsPerPage;
    private final int[] amountChangeMode;
    private final boolean shiftPageChange;
    private final Minecraft mc = FMLClientHandler.instance().getClient();
    private static DisplayOption option = DisplayOption.ID;

    public ItemDisplay(IItemSearch search, FontRenderer fontRenderer, LogisticsBaseGuiScreen screen,
            ISpecialItemRenderer renderer, int left, int top, int width, int height, int[] amountChangeMode,
            boolean shiftPageChange) {
        this.search = search;
        this.fontRenderer = fontRenderer;
        this.screen = screen;
        this.renderer = renderer;
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        itemsPerPage = this.width * this.height / (20 * 20);
        if (amountChangeMode.length != 4) {
            throw new UnsupportedOperationException("amountChangeMode.length needs to be 4");
        }
        this.amountChangeMode = amountChangeMode;
        this.shiftPageChange = shiftPageChange;
    }

    public void reposition(int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        itemsPerPage = this.width * this.height / (20 * 20);
    }

    public void setItemList(Collection<ItemIdentifierStack> allItems) {
        listbyserver = true;
        _allItems.clear();
        _allItems.addAll(allItems);
        _allItems.sort(new StackComparitor());
        boolean found = false;
        if (selectedItem == null) {
            return;
        }
        for (ItemIdentifierStack itemStack : _allItems) {
            if (itemStack.getItem().equals(selectedItem.getItem())) {
                selectedItem = itemStack;
                found = true;
                break;
            }
        }
        if (!found) {
            selectedItem = null;
        }
    }

    private static class StackComparitor implements Comparator<ItemIdentifierStack> {

        @Override
        public int compare(ItemIdentifierStack o1, ItemIdentifierStack o2) {
            if (ItemDisplay.option == DisplayOption.ID) {
                int c = basicCompare(o1, o2);
                if (c != 0) {
                    return c;
                }
                return o2.getStackSize() - o1.getStackSize();
            } else if (ItemDisplay.option == DisplayOption.ID_DOWN) {
                int c = basicCompare(o2, o1);
                if (c != 0) {
                    return c;
                }
                return o1.getStackSize() - o2.getStackSize();
            } else if (ItemDisplay.option == DisplayOption.SIZE) {
                int c = o2.getStackSize() - o1.getStackSize();
                if (c != 0) {
                    return c;
                }
                return basicCompare(o1, o2);
            } else if (ItemDisplay.option == DisplayOption.SIZE_DOWN) {
                int c = o1.getStackSize() - o2.getStackSize();
                if (c != 0) {
                    return c;
                }
                return basicCompare(o2, o1);
            } else if (ItemDisplay.option == DisplayOption.NAME) {
                int c = o1.getItem().getFriendlyName().compareToIgnoreCase(o2.getItem().getFriendlyName());
                if (c != 0) {
                    return c;
                }
                c = basicCompare(o1, o2);
                if (c != 0) {
                    return c;
                }
                return o2.getStackSize() - o1.getStackSize();
            } else if (ItemDisplay.option == DisplayOption.NAME_DOWN) {
                int c = o2.getItem().getFriendlyName().compareToIgnoreCase(o1.getItem().getFriendlyName());
                if (c != 0) {
                    return c;
                }
                c = basicCompare(o2, o1);
                if (c != 0) {
                    return c;
                }
                return o1.getStackSize() - o2.getStackSize();
            } else {
                int c = basicCompare(o1, o2);
                if (c != 0) {
                    return c;
                }
                return o2.getStackSize() - o1.getStackSize();
            }
        }

        private int basicCompare(ItemIdentifierStack o1, ItemIdentifierStack o2) {
            return o1.compareTo(o2);
        }
    }

    public void cycle() {
        int i = ItemDisplay.option.ordinal();
        i++;
        if (i >= DisplayOption.values().length) {
            i = 0;
        }
        ItemDisplay.option = DisplayOption.values()[i];
        _allItems.sort(new StackComparitor());
    }

    public void renderSortMode(int x, int y) {
        String name = ItemDisplay.option.name();
        boolean up = true;
        if (name.endsWith("_DOWN")) {
            name = name.substring(0, name.length() - 5);
            up = false;
        }
        name += !up ? " /\\" : " \\/";
        fontRenderer.drawString(name, x - fontRenderer.getStringWidth(name) / 2, y, 0x404040);
    }

    public void renderPageNumber(int x, int y) {
        maxPage = (getSearchedItemNumber() - 1) / itemsPerPage;
        if (maxPage == -1) {
            maxPage = 0;
        }
        if (page > maxPage) {
            page = maxPage;
        }
        String pageString = "Page " + (page + 1) + " / " + (maxPage + 1);
        fontRenderer.drawString(pageString, x - fontRenderer.getStringWidth(pageString) / 2, y, 0x404040);
    }

    private int getSearchedItemNumber() {
        int count = 0;
        for (ItemIdentifierStack item : _allItems) {
            if (search == null || search.itemSearched(item.getItem())) {
                count++;
            }
        }
        return count;
    }

    public void renderAmount(int x, int y, int stackAmount) {
        String StackrequestCount = "" + (requestCount / stackAmount) + "+" + (requestCount % stackAmount);
        fontRenderer.drawString(requestCount + "", x - fontRenderer.getStringWidth(requestCount + "") / 2, y, 0x404040);
        fontRenderer.drawString(
                StackrequestCount + "",
                x - fontRenderer.getStringWidth(StackrequestCount + "") / 2,
                y + 10,
                0x404040);
    }

    public void renderItemArea(double zLevel) {
        GL11.glPushMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        screen.drawRect(left, top, left + width, top + height, Color.GREY);

        tooltip = null;
        int ppi = 0;
        int panelxSize = 20;
        int panelySize = 20;
        int x = 2;
        int y = 2;
        ScaledResolution scaledresolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scaleX = scaledresolution.getScaledWidth();
        int scaleY = scaledresolution.getScaledHeight();
        int mouseX = Mouse.getX() * scaleX / mc.displayWidth - left;
        int mouseY = scaleY - Mouse.getY() * scaleY / mc.displayHeight - top;

        GL11.glTranslatef(left, top, 0.0F);

        if (!listbyserver) {
            int graphic = ((int) (System.currentTimeMillis() / 250) % 5);
            // GL11.glBindTexture(GL11.GL_TEXTURE_2D,
            // this.mc.renderEngine.getTexture());
            screen.getMC().renderEngine.bindTexture(ItemDisplay.TEXTURE);
            Tessellator tesselator = Tessellator.instance;
            tesselator.startDrawingQuads();
            int xPosition = (width / 2) - 50;
            int yPosition = 40;
            tesselator.addVertexWithUV(xPosition, yPosition + 100, zLevel, 0.04, 0.72 + (graphic * 0.03125));
            tesselator.addVertexWithUV(xPosition + 100, yPosition + 100, zLevel, 0.08, 0.72 + (graphic * 0.03125));
            tesselator.addVertexWithUV(xPosition + 100, yPosition, zLevel, 0.08, 0.69 + (graphic * 0.03125));
            tesselator.addVertexWithUV(xPosition, yPosition, zLevel, 0.04, 0.69 + (graphic * 0.03125));
            tesselator.draw();
        } else {
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);

            for (ItemIdentifierStack itemIdentifierStack : _allItems) {
                ItemIdentifier item = itemIdentifierStack.getItem();
                if (search != null && !search.itemSearched(item)) {
                    continue;
                }
                ppi++;

                if (ppi <= itemsPerPage * page) {
                    continue;
                }
                if (ppi > itemsPerPage * (page + 1)) {
                    break;
                }

                ItemStack itemstack = itemIdentifierStack.unsafeMakeNormalStack();
                // -2 on both, because field starts there (see black rect below)
                int realX = x - 2;
                int realY = y - 2;

                Pair<Integer, Integer> pair = new Pair<>(realX, realY);
                if (map.get(pair) != itemIdentifierStack) {
                    map.put(pair, itemIdentifierStack);
                }

                if (mouseX >= realX && mouseX < realX + panelxSize && mouseY >= realY && mouseY < realY + panelySize) {
                    screen.drawRect(x - 2, y - 2, x + panelxSize - 2, y + panelySize - 2, Color.BLACK);
                    screen.drawRect(x - 1, y - 1, x + panelxSize - 3, y + panelySize - 3, Color.DARKER_GREY);

                    tooltip = new Object[] { mouseX + left, mouseY + top, itemstack };
                }

                /*
                 * if (lastClickedx >= realX && lastClickedx < realX + panelxSize && lastClickedy >= realY &&
                 * lastClickedy < realY + panelySize) { selectedItem = itemIdentifierStack; screen.drawRect(x - 2, y -
                 * 2, x + panelxSize - 2, y + panelySize - 2, Color.BLACK); screen.drawRect(x - 1, y - 1, x + panelxSize
                 * - 3, y + panelySize - 3, Color.LIGHTER_GREY); screen.drawRect(x, y, x + panelxSize - 4, y +
                 * panelySize - 4, Color.DARKER_GREY); }
                 */
                if (selectedItem == itemIdentifierStack) {
                    screen.drawRect(x - 2, y - 2, x + panelxSize - 2, y + panelySize - 2, Color.BLACK);
                    screen.drawRect(x - 1, y - 1, x + panelxSize - 3, y + panelySize - 3, Color.LIGHTER_GREY);
                    screen.drawRect(x, y, x + panelxSize - 4, y + panelySize - 4, Color.DARKER_GREY);
                    if (renderer != null) {
                        renderer.specialItemRendering(itemIdentifierStack.getItem(), x, y);
                    }
                }

                // use GuiGraphics to render the ItemStacks
                ItemStackRenderer itemstackRenderer = new ItemStackRenderer(x, y, 100.0F, true, false, true);
                itemstackRenderer.setItemstack(itemstack).setDisplayAmount(DisplayAmount.HIDE_ONE);
                itemstackRenderer.renderInGui();

                x += panelxSize;
                if (x > width) {
                    x = 2;
                    y += panelySize;
                }
            }

            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        GL11.glPopMatrix();
    }

    public void handleMouse() {
        boolean isShift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        boolean isControl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        int wheel = Mouse.getEventDWheel() / 120;
        if (wheel == 0) {
            return;
        }

        if (isShift && !isControl && isShiftPageChange()) {
            if (wheel > 0) {
                if (!Configs.LOGISTICS_ORDERER_PAGE_INVERTWHEEL) {
                    prevPage();
                } else {
                    nextPage();
                }
            } else {
                if (!Configs.LOGISTICS_ORDERER_PAGE_INVERTWHEEL) {
                    nextPage();
                } else {
                    prevPage();
                }
            }
        } else if (isShift && !isControl) {
            if (wheel > 0) {
                if (!Configs.LOGISTICS_ORDERER_COUNT_INVERTWHEEL) {
                    requestCount = Math.max(1, requestCount - (wheel * getAmountChangeMode(4)));
                } else {
                    if (requestCount == 1) {
                        requestCount -= 1;
                    }
                    requestCount += wheel * getAmountChangeMode(4);
                }
            } else {
                if (!Configs.LOGISTICS_ORDERER_COUNT_INVERTWHEEL) {
                    if (requestCount == 1) {
                        requestCount -= 1;
                    }
                    requestCount -= (wheel * getAmountChangeMode(4));
                } else {
                    requestCount = Math.max(1, requestCount + wheel * getAmountChangeMode(4));
                }
            }
        } else if (!isControl) {
            if (wheel > 0) {
                if (!Configs.LOGISTICS_ORDERER_COUNT_INVERTWHEEL) {
                    requestCount = Math.max(1, requestCount - (wheel * getAmountChangeMode(1)));
                } else {
                    requestCount += wheel * getAmountChangeMode(1);
                }
            } else {
                if (!Configs.LOGISTICS_ORDERER_COUNT_INVERTWHEEL) {
                    requestCount -= (wheel * getAmountChangeMode(1));
                } else {
                    requestCount = Math.max(1, requestCount + wheel * getAmountChangeMode(1));
                }
            }
        } else if (!isShift) {
            if (wheel > 0) {
                if (!Configs.LOGISTICS_ORDERER_COUNT_INVERTWHEEL) {
                    requestCount = Math.max(1, requestCount - wheel * getAmountChangeMode(2));
                } else {
                    if (requestCount == 1) {
                        requestCount -= 1;
                    }
                    requestCount += wheel * getAmountChangeMode(2);
                }
            } else {
                if (!Configs.LOGISTICS_ORDERER_COUNT_INVERTWHEEL) {
                    if (requestCount == 1) {
                        requestCount -= 1;
                    }
                    requestCount += -wheel * getAmountChangeMode(2);
                } else {
                    requestCount = Math.max(1, requestCount + wheel * getAmountChangeMode(2));
                }
            }
        } else {
            if (wheel > 0) {
                if (!Configs.LOGISTICS_ORDERER_COUNT_INVERTWHEEL) {
                    requestCount = Math.max(1, requestCount - wheel * getAmountChangeMode(3));
                } else {
                    if (requestCount == 1) {
                        requestCount -= 1;
                    }
                    requestCount += wheel * getAmountChangeMode(3);
                }
            } else {
                if (!Configs.LOGISTICS_ORDERER_COUNT_INVERTWHEEL) {
                    if (requestCount == 1) {
                        requestCount -= 1;
                    }
                    requestCount += -wheel * getAmountChangeMode(3);
                } else {
                    requestCount = Math.max(1, requestCount + wheel * getAmountChangeMode(3));
                }
            }
        }
    }

    private int getAmountChangeMode(int step) {
        return amountChangeMode[step - 1];
    }

    private boolean isShiftPageChange() {
        return shiftPageChange;
    }

    public Object[] getToolTip() {
        return tooltip;
    }

    public void resetAmount() {
        requestCount = 1;
    }

    public void setMaxAmount() {
        if (selectedItem != null && selectedItem.getStackSize() != 0) {
            requestCount = selectedItem.getStackSize();
        }
    }

    public void nextPage() {
        if (page < maxPage) {
            page++;
        } else {
            page = 0;
        }
    }

    public void prevPage() {
        if (page > 0) {
            page--;
        } else {
            page = maxPage;
        }
    }

    public void add(int i) {
        if (i != 1 && requestCount == 1) {
            requestCount -= 1;
        }
        requestCount += getAmountChangeMode(i);
    }

    public void sub(int i) {
        requestCount = Math.max(1, requestCount - getAmountChangeMode(i));
    }

    public ItemIdentifierStack getSelectedItem() {
        return selectedItem;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public boolean handleClick(int x, int y, int k) {
        x -= left;
        y -= top;
        if (x < 0 || y < 0 || x > width || y > height) {
            return false;
        }
        selectedItem = null;
        for (Entry<Pair<Integer, Integer>, ItemIdentifierStack> entry : map.entrySet()) {
            if (x >= entry.getKey().getValue1() && x < entry.getKey().getValue1() + ItemDisplay.PANELSIZEX
                    && y >= entry.getKey().getValue2()
                    && y < entry.getKey().getValue2() + ItemDisplay.PANELSIZEY) {
                selectedItem = entry.getValue();
                return true;
            }
        }
        return false;
    }
}
