package com.simibubi.create.content.logistics.stockTicker;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.BigItemStack;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;

public final class StockTickerScreen extends AbstractContainerScreen<StockTickerMenu> {
    private record Row(int category, List<BigItemStack> items) { boolean header() { return items==null; } }
    private EditBox search, address, categoryName;
    private Button save, add, lock, done;
    private final List<BigItemStack> order=new ArrayList<>();
    private final List<Row> rows=new ArrayList<>();
    private final Set<Integer> hidden=new HashSet<>();
    private final List<StockTickerCrafting.Selection> recipes=new ArrayList<>();
    private final List<StockTickerCrafting.Choice> recipeChoices=new ArrayList<>();
    private final Deque<Runnable> edits=new ArrayDeque<>();
    private int revision,appliedRevision=-1,basketOffset;
    private boolean recipeMode,catalogRequested,exactMaterials,previewPending;
    private int scroll, version=-1, editing=StockTickerMenu.IDLE, age;
    private boolean encoded, closed, awaitingName;
    public String keeperPortrait="none";
    public StockTickerScreen(StockTickerMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title,menu.categoryMode?208:256,menu.categoryMode?232:236);
        hidden.addAll(menu.owner.hiddenCategoriesByPlayer.getOrDefault(inventory.player.getUUID(),List.of()));
        encoded=inventory.player.getMainHandItem().is(AllBlocks.REDSTONE_REQUESTER.asItem())||inventory.player.getMainHandItem().getItem() instanceof com.simibubi.create.content.logistics.tableCloth.TableClothBlockItem;
    }
    @Override protected void init() {
        super.init();
        if (menu.categoryMode) {
            categoryName=new EditBox(font,leftPos+40,topPos+43,142,16,Component.translatable("create.stock_ticker.category_name"));categoryName.setMaxLength(28);categoryName.setResponder(value->awaitingName=false);addRenderableWidget(categoryName);
            save=addRenderableWidget(Button.builder(Component.translatable("gui.done"),button->action(StockTickerMenu.FINISH,0,categoryName.getValue())).bounds(leftPos+136,topPos+18,54,18).build());
            add=addRenderableWidget(Button.builder(Component.literal("+"),button->action(StockTickerMenu.BEGIN,-1,"")).bounds(leftPos+14,topPos+18,20,18).tooltip(Tooltip.create(Component.translatable("create.gui.stock_ticker.new_category"))).build());
            done=addRenderableWidget(Button.builder(Component.literal("✓"),button->onClose()).bounds(leftPos+172,topPos+150,18,18).build());
        } else {
            search=new EditBox(font,leftPos+86,topPos+22,100,9,Component.translatable("itemGroup.search"));search.setBordered(false);search.setTextColor(0xFF4A2D31);search.setMaxLength(64);search.setResponder(value->{scroll=0;rebuild();});addRenderableWidget(search);
            address=new EditBox(font,leftPos+42,topPos+200,92,10,Component.translatable("create.gui.redstone_requester.requester_address"));address.setBordered(false);address.setTextColor(0xFF714A40);address.setMaxLength(25);address.setValue(menu.owner.previouslyUsedAddress);addRenderableWidget(address);
            addRenderableWidget(Button.builder(Component.translatable(encoded?"create.gui.stock_keeper.configure":"create.gui.stock_keeper.send"),button->sendOrder()).bounds(leftPos+158,topPos+197,78,18).build());
            lock=addRenderableWidget(Button.builder(Component.literal("◇"),button->action(StockTickerMenu.LOCK,menu.locked?0:1,"")).bounds(leftPos+201,topPos+18,18,18).tooltip(Tooltip.create(Component.translatable("create.stock_ticker.network_lock"))).build());
            addRenderableWidget(Button.builder(Component.literal("R"),button->{recipeMode=!recipeMode;scroll=0;catalogRequested=true;queryRecipes();rebuild();}).bounds(leftPos+40,topPos+18,18,18).tooltip(Tooltip.create(Component.translatable("create.stock_ticker.recipes"))).build());
            setInitialFocus(search);rebuild();
        }
        updateWidgets();
    }
    private void action(int action, int index, String name) { ClientPlayNetworking.send(new StockTickerMenu.Action(menu.containerId,action,index,name,List.of())); }
    private void updateWidgets() {
        if (menu.categoryMode) {
            boolean active=menu.editing!=StockTickerMenu.IDLE;categoryName.visible=active;save.visible=active;add.active=!active;add.visible=!active;done.visible=!active;
            if (editing!=menu.editing) { editing=menu.editing;categoryName.setValue(editing>=0&&!menu.proxy.getItem(0).isEmpty()?menu.proxy.getItem(0).getHoverName().getString():Component.translatable("create.gui.stock_ticker.new_category").getString());awaitingName=active; }
            if(awaitingName&&!menu.proxy.getItem(0).isEmpty()){if(editing>=0||menu.proxy.getItem(0).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME))categoryName.setValue(menu.proxy.getItem(0).getHoverName().getString());awaitingName=false;}
        } else { lock.visible=menu.admin;lock.setMessage(Component.literal(menu.locked?"◆":"◇")); }
    }
    @Override protected void containerTick() {
        super.containerTick();updateWidgets();
        if (version!=menu.snapshotVersion) { version=menu.snapshotVersion;rebuild(); }
        applyCraftingReply();flushEdits();
        if (!menu.categoryMode && ++age>15) { age=0;action(StockTickerMenu.REFRESH,0,"");if(catalogRequested)queryRecipes();if(!recipes.isEmpty()&&!previewPending)preview(false); }
    }
    private void applyCraftingReply(){
        if(menu.craftingReply==revision&&appliedRevision!=revision){
            appliedRevision=revision;previewPending=false;recipes.clear();recipes.addAll(menu.craftingCart.selections());
            var materials=exactMaterials?menu.craftingCart.order().stacks():menu.craftingCart.manual();order.clear();order.addAll(materials.stream().map(entry->new BigItemStack(entry.stack.copy(),entry.count)).toList());
            if(recipes.isEmpty())exactMaterials=false;
            basketOffset=Math.min(basketOffset,Math.max(0,recipes.size()-9));rebuild();
        }
    }
    private void queryRecipes(){ClientPlayNetworking.send(new StockTickerCraftingPackets.Query(menu.containerId,menu.craftingCatalogVersion));}
    private void preview(boolean submit){
        menu.craftingRevision=++revision;previewPending=true;
        ClientPlayNetworking.send(new StockTickerCraftingPackets.Edit(menu.containerId,revision,List.copyOf(recipes),order.stream().map(entry->new BigItemStack(entry.stack.copy(),entry.count)).toList(),submit,address.getValue(),encoded,exactMaterials));
    }
    private void enqueue(Runnable edit){edits.addLast(edit);flushEdits();}
    private void flushEdits(){while(!previewPending&&!edits.isEmpty())edits.removeFirst().run();}
    private int visibleRows(){return recipes.isEmpty()?6:4;}
    private boolean matches(ItemStack stack) {
        String query=search==null?"":search.getValue().toLowerCase(Locale.ROOT);
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (query.startsWith("@")) return id.getNamespace().contains(query.substring(1));
        if (query.startsWith("#")) return stack.typeHolder().tags().anyMatch(tag->tag.location().toString().contains(query.substring(1)));
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)||id.getPath().contains(query);
    }
    private void rebuild() {
        rows.clear();if (menu.categoryMode) return;
        recipeChoices.clear();recipeChoices.addAll(menu.craftingChoices.stream().filter(choice->matches(choice.result())).toList());
        if(recipeMode){scroll=Math.clamp(scroll,0,Math.max(0,(recipeChoices.size()+8)/9-visibleRows()));return;}
        var groups=menu.owner.categorize(menu.stock);boolean named=false;
        for (int i=0;i<groups.size()-1;i++) if(groups.get(i).stream().anyMatch(entry->matches(entry.stack)))named=true;
        for (int i=0;i<groups.size();i++) {
            var entries=groups.get(i).stream().filter(entry->matches(entry.stack)&&entry.count>0).toList();if(entries.isEmpty())continue;
            int category=i==groups.size()-1?-1:i;
            if(named){rows.add(new Row(category,null));if(hidden.contains(category))continue;}
            for(int start=0;start<entries.size();start+=9)rows.add(new Row(category,entries.subList(start,Math.min(entries.size(),start+9))));
        }
        scroll=Math.clamp(scroll,0,Math.max(0,rows.size()-visibleRows()));
    }
    private BigItemStack entryAt(double x,double y) {
        int column=(int)((x-leftPos-38)/20),row=(int)((y-topPos-40)/20);
        if(recipeMode||x<leftPos+38||column<0||column>=9||y<topPos+40||row<0||row>=visibleRows()||scroll+row>=rows.size())return null;
        var line=rows.get(scroll+row);return line.header()||column>=line.items.size()?null:line.items.get(column);
    }
    private List<BigItemStack> displayedOrder(){return recipes.isEmpty()||exactMaterials?order:menu.craftingCart.order().stacks();}
    private int orderAt(double x,double y) { int index=(int)((x-leftPos-38)/20);return x>=leftPos+38&&y>=topPos+164&&y<topPos+182&&index>=0&&index<displayedOrder().size()?index:-1; }
    private StockTickerCrafting.Choice recipeAt(double x,double y){
        if(!recipeMode||x<leftPos+38||x>=leftPos+218||y<topPos+40||y>=topPos+40+visibleRows()*20)return null;int index=(scroll+(int)((y-topPos-40)/20))*9+(int)((x-leftPos-38)/20);return index<recipeChoices.size()?recipeChoices.get(index):null;
    }
    private int recipeOrderAt(double x,double y){int index=basketOffset+(int)((x-leftPos-38)/20);return !recipes.isEmpty()&&x>=leftPos+38&&x<leftPos+218&&y>=topPos+136&&y<topPos+154&&index<recipes.size()?index:-1;}
    private StockTickerCrafting.Choice choice(Identifier id){return menu.craftingCart.results().stream().filter(entry->entry.recipe().equals(id)).findFirst().orElseGet(()->menu.craftingChoices.stream().filter(entry->entry.recipe().equals(id)).findFirst().orElse(null));}
    private void changeRecipe(StockTickerCrafting.Choice choice,int difference){
        if(choice!=null)enqueue(()->applyRecipeChange(choice,difference));
    }
    private void applyRecipeChange(StockTickerCrafting.Choice choice,int difference){
        var materials=displayedOrder().stream().map(entry->new BigItemStack(entry.stack.copy(),entry.count)).toList();order.clear();order.addAll(materials);exactMaterials=true;
        menu.craftingRevision=++revision;previewPending=true;
        ClientPlayNetworking.send(new StockTickerCraftingPackets.Edit(menu.containerId,revision,List.copyOf(recipes),materials,false,address.getValue(),encoded,true,new StockTickerCrafting.Adjustment(choice.recipe(),difference)));
    }
    private int available(ItemStack stack) { return menu.stock.stream().filter(entry->ItemStack.isSameItemSameComponents(entry.stack,stack)).mapToInt(entry->entry.count).findFirst().orElse(0); }
    private void change(BigItemStack entry,int delta) {
        var copy=new BigItemStack(entry.stack.copy(),entry.count);enqueue(()->applyMaterialChange(copy,delta));
    }
    private void applyMaterialChange(BigItemStack entry,int delta) {
        if(!recipes.isEmpty()&&!exactMaterials){var ingredients=menu.craftingCart.order().stacks().stream().map(item->new BigItemStack(item.stack.copy(),item.count)).toList();order.clear();order.addAll(ingredients);exactMaterials=true;}
        var current=order.stream().filter(item->ItemStack.isSameItemSameComponents(item.stack,entry.stack)).findFirst().orElse(null);
        if(current==null){if(delta<=0||order.size()>=9)return;current=new BigItemStack(entry.stack.copyWithCount(1),0);order.add(current);}
        current.count=(int)Math.clamp((long)current.count+delta,0,available(current.stack));if(current.count==0)order.remove(current);
        if(!recipes.isEmpty())preview(false);rebuild();
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        applyCraftingReply();
        if(!menu.categoryMode){
            var recipe=recipeAt(event.x(),event.y());
            if(recipe!=null){int outputs=minecraft.hasShiftDown()?recipe.result().getMaxStackSize():minecraft.hasControlDown()?10:1;changeRecipe(recipe,(event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT?-1:1)*Math.max(1,(outputs+recipe.result().getCount()-1)/recipe.result().getCount()));return true;}
            int queued=recipeOrderAt(event.x(),event.y());if(queued>=0){changeRecipe(choice(recipes.get(queued).recipe()),-1);return true;}
            if(!recipes.isEmpty()&&event.y()>=topPos+136&&event.y()<topPos+154){if(event.x()>=leftPos+20&&event.x()<leftPos+38){basketOffset=Math.max(0,basketOffset-9);return true;}if(event.x()>=leftPos+220&&event.x()<leftPos+238){basketOffset=Math.min(Math.max(0,recipes.size()-9),basketOffset+9);return true;}}
            int index=orderAt(event.x(),event.y());int amount=minecraft.hasShiftDown()?64:minecraft.hasControlDown()?10:1;
            if(index>=0){var entry=displayedOrder().get(index);change(entry,-(minecraft.hasShiftDown()?entry.stack.getMaxStackSize():amount));return true;}
            var entry=entryAt(event.x(),event.y());
            if(entry!=null){change(entry,(event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT?-1:1)*(minecraft.hasShiftDown()?entry.stack.getMaxStackSize():amount));return true;}
            if(!recipeMode&&event.x()>=leftPos+38&&event.x()<leftPos+218&&event.y()>=topPos+40&&event.y()<topPos+40+visibleRows()*20){int row=(int)((event.y()-topPos-40)/20)+scroll;if(row<rows.size()&&rows.get(row).header()){int category=rows.get(row).category;if(!hidden.add(category))hidden.remove(category);ClientPlayNetworking.send(new StockTickerMenu.Action(menu.containerId,StockTickerMenu.HIDE,0,"",List.copyOf(hidden)));rebuild();return true;}}
        } else if(menu.editing==StockTickerMenu.IDLE&&event.y()>=topPos+66&&event.y()<topPos+148&&event.x()>=leftPos+14&&event.x()<leftPos+192){
            int index=scroll+(int)((event.y()-topPos-66)/20);if(index<menu.owner.categories.size())action(event.x()>leftPos+174?StockTickerMenu.DELETE:event.x()>leftPos+158?StockTickerMenu.DOWN:event.x()>leftPos+142?StockTickerMenu.UP:StockTickerMenu.BEGIN,index,"");return true;
        }
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        applyCraftingReply();
        if(vertical==0)return super.mouseScrolled(x,y,horizontal,vertical);
        if(!menu.categoryMode){
            var recipe=recipeAt(x,y);int queued=recipeOrderAt(x,y);if(queued>=0)recipe=choice(recipes.get(queued).recipe());if(recipe!=null){int outputs=(int)Math.ceil(Math.abs(vertical))*(minecraft.hasControlDown()?10:1);changeRecipe(recipe,(vertical>0?1:-1)*Math.max(1,(outputs+recipe.result().getCount()-1)/recipe.result().getCount()));return true;}
            int index=orderAt(x,y);var entry=index>=0?displayedOrder().get(index):entryAt(x,y);if(entry!=null){change(entry,(int)Math.ceil(Math.abs(vertical))*(vertical>0?1:-1)*(minecraft.hasControlDown()?10:1));return true;}}
        int max=menu.categoryMode?Math.max(0,menu.owner.categories.size()-4):Math.max(0,(recipeMode?(recipeChoices.size()+8)/9:rows.size())-visibleRows());scroll=Math.clamp(scroll+(vertical<0?1:-1),0,max);return true;
    }
    public void sendOrder() {
        applyCraftingReply();enqueue(this::sendCurrentOrder);
    }
    private void sendCurrentOrder() {
        if(!recipes.isEmpty()){preview(true);recipes.clear();order.clear();exactMaterials=false;previewPending=false;menu.craftingRevision=++revision;menu.craftingCart=new StockTickerCrafting.Cart(PackageOrderWithCrafts.empty(),List.of(),List.of());rebuild();return;}
        if(order.isEmpty())return;
        ClientPlayNetworking.send(new StockTickerMenu.Request(menu.containerId,PackageOrderWithCrafts.simple(order.stream().map(entry->new BigItemStack(entry.stack.copy(),entry.count)).toList()),address.getValue(),encoded));order.clear();
    }
    @Override public void onClose() {
        if(!closed){closed=true;if(menu.categoryMode){if(menu.editing!=StockTickerMenu.IDLE)action(StockTickerMenu.FINISH,0,categoryName.getValue());}
        else {ClientPlayNetworking.send(new StockTickerMenu.Request(menu.containerId,PackageOrderWithCrafts.empty(),address.getValue(),false));ClientPlayNetworking.send(new StockTickerMenu.Action(menu.containerId,StockTickerMenu.HIDE,0,"",List.copyOf(hidden)));}}
        super.onClose();
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if(event.isEscape())return super.keyPressed(event);
        if(event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_RETURN&&minecraft.hasShiftDown()&&!menu.categoryMode){sendOrder();return true;}
        if(getFocused() instanceof EditBox edit&&edit.canConsumeInput()){edit.keyPressed(event);return true;}
        return super.keyPressed(event);
    }
    @Override public void extractBackground(GuiGraphicsExtractor gui,int mouseX,int mouseY,float delta) {
        applyCraftingReply();updateWidgets();
        super.extractBackground(gui,mouseX,mouseY,delta);
        if(menu.categoryMode){
            gui.fill(leftPos+8,topPos,leftPos+200,topPos+173,0xFFB4AAA1);
            gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/stock_keeper_categories.png"),leftPos+8,topPos,32,0,192,18,256,256);
            if(menu.editing!=StockTickerMenu.IDLE)gui.blit(RenderPipelines.GUI_TEXTURED,Create.asResource("textures/gui/player_inventory.png"),leftPos+8,topPos+124,0,0,176,108,256,256);
            if(menu.editing!=StockTickerMenu.IDLE)gui.fill(leftPos+15,topPos+41,leftPos+33,topPos+59,0xFFCEC6BD);
            if(menu.editing==StockTickerMenu.IDLE)for(int row=0;row<4&&scroll+row<menu.owner.categories.size();row++){
                int index=scroll+row,y=topPos+66+row*20;var item=menu.owner.categories.get(index);
                gui.fill(leftPos+14,y,leftPos+192,y+18,0xFFCEC6BD);gui.item(item,leftPos+16,y+1);gui.text(font,font.plainSubstrByWidth(item.getHoverName().getString(),104),leftPos+36,y+5,0xFF303030,false);
                gui.text(font,"↑ ↓ ×",leftPos+144,y+5,0xFF303030,false);
            }
        }else{
            keeperPortrait=StockKeeperPortrait.extract(gui,menu,leftPos,topPos,imageHeight,mouseX,mouseY,delta);
            var texture=Create.asResource("textures/gui/stock_keeper.png");gui.blit(RenderPipelines.GUI_TEXTURED,texture,leftPos,topPos,0,0,256,36,256,256);
            for(int row=0;row<6;row++)gui.blit(RenderPipelines.GUI_TEXTURED,texture,leftPos,topPos+36+row*20,0,48,256,20,256,256);
            gui.blit(RenderPipelines.GUI_TEXTURED,texture,leftPos,topPos+156,0,80,256,80,256,256);
            if(recipeMode){
                for(int row=0;row<visibleRows();row++)for(int column=0;column<9;column++){int index=(scroll+row)*9+column;if(index>=recipeChoices.size())continue;var choice=recipeChoices.get(index);int x=leftPos+38+column*20,y=topPos+40+row*20;if(choice.available()==0)gui.fill(x,y,x+16,y+16,0x60303030);gui.item(choice.result(),x,y);drawAmount(gui,choice.result(),x,y,count((int)Math.min(BigItemStack.INF,(long)choice.available()*choice.result().getCount())));}
            }else for(int row=0;row<visibleRows()&&scroll+row<rows.size();row++){
                var line=rows.get(scroll+row);int y=topPos+40+row*20;
                if(line.header()){var name=line.category<0?Component.translatable("create.gui.stock_keeper.unsorted_category"):menu.owner.categories.get(line.category).getHoverName();gui.text(font,(hidden.contains(line.category)?"▸ ":"▾ ")+name.getString(),leftPos+38,y+4,0xFF40382F,false);continue;}
                for(int column=0;column<line.items.size();column++){var entry=line.items.get(column);int x=leftPos+38+column*20;gui.item(entry.stack,x,y);drawAmount(gui,entry.stack,x,y,count(entry.count));}
            }
            if(!recipes.isEmpty()){
                gui.text(font,Component.translatable(menu.craftingCart.order().orderedCrafts().isEmpty()?"create.stock_ticker.mixed_order":"create.stock_ticker.crafting_order"),leftPos+38,topPos+122,0xFF40382F,false);
                for(int i=basketOffset;i<Math.min(recipes.size(),basketOffset+9);i++){var recipe=recipes.get(i);var choice=choice(recipe.recipe());if(choice==null)continue;int x=leftPos+38+(i-basketOffset)*20;gui.item(choice.result(),x,topPos+136);drawAmount(gui,choice.result(),x,topPos+136,count((int)Math.min(BigItemStack.INF,(long)recipe.crafts()*choice.result().getCount())));}
                if(recipes.size()>9){gui.text(font,"‹",leftPos+24,topPos+140,0xFF40382F,false);gui.text(font,"›",leftPos+224,topPos+140,0xFF40382F,false);}
            }
            var ingredients=displayedOrder();for(int i=0;i<ingredients.size();i++){var entry=ingredients.get(i);gui.item(entry.stack,leftPos+38+i*20,topPos+164);drawAmount(gui,entry.stack,leftPos+38+i*20,topPos+164,count(entry.count));}
            var hovered=entryAt(mouseX,mouseY);int selected=orderAt(mouseX,mouseY);if(selected>=0)hovered=ingredients.get(selected);if(hovered!=null)gui.setTooltipForNextFrame(font,hovered.stack,mouseX,mouseY);
            var recipe=recipeAt(mouseX,mouseY);int queued=recipeOrderAt(mouseX,mouseY);if(queued>=0)recipe=choice(recipes.get(queued).recipe());if(recipe!=null){gui.setTooltipForNextFrame(font,recipe.result(),mouseX,mouseY);int x=leftPos+260;if(x+58<=width){gui.fill(x,topPos+40,x+58,topPos+98,0xFFB4AAA1);for(int i=0;i<9;i++){int px=x+2+i%3*18,py=topPos+42+i/3*18;gui.fill(px,py,px+16,py+16,0xFFCEC6BD);gui.item(recipe.grid().get(i),px,py);}}}
        }
    }
    private static String count(int value) { return value>=BigItemStack.INF?"∞":value>=1_000_000?value/1_000_000+"m":value>=10_000?value/1000+"k":Integer.toString(value); }
    private void drawAmount(GuiGraphicsExtractor gui,ItemStack item,int x,int y,String text){gui.itemDecorations(font,item,x,y,"");float scale=text.length()>2?.65f:.75f;gui.pose().pushMatrix();gui.pose().translate(x+17-font.width(text)*scale,y+10);gui.pose().scale(scale,scale);gui.text(font,text,0,0,0xFFFFFFFF,true);gui.pose().popMatrix();}
    @Override protected void extractLabels(GuiGraphicsExtractor gui,int mouseX,int mouseY) { var heading=recipeMode?Component.translatable("create.stock_ticker.recipes"):title;gui.text(font,heading,(imageWidth-font.width(heading))/2,5,0xFF3D3C48,false);if(menu.categoryMode&&menu.editing!=StockTickerMenu.IDLE)gui.text(font,playerInventoryTitle,16,130,0xFF303030,false); }
    public EditBox searchBox(){return search;} public EditBox addressBox(){return address;} public EditBox categoryNameBox(){return categoryName;}
    public Vec2 stockPosition(int row,int column){return new Vec2(leftPos+46+column*20,topPos+48+row*20);}
    public Vec2 categoryPosition(int index){return new Vec2(leftPos+80,topPos+74+(index-scroll)*20);}
    public int selectedAmount(int index){return order.get(index).count;}
    public Vec2 recipeModePosition(){return new Vec2(leftPos+49,topPos+27);} public int recipeIndex(Identifier id){for(int i=0;i<recipeChoices.size();i++)if(recipeChoices.get(i).recipe().equals(id))return i;return -1;}
    public Vec2 recipePosition(int index){return new Vec2(leftPos+46+index%9*20,topPos+48+(index/9-scroll)*20);} public boolean hasCraftingContext(){return !menu.craftingCart.order().orderedCrafts().isEmpty();}
    public Vec2 categoryAddPosition(){return new Vec2(leftPos+24,topPos+27);} public Vec2 categorySavePosition(){return new Vec2(leftPos+163,topPos+27);}
    public Vec2 slotPosition(int index){var slot=menu.getSlot(index);return new Vec2(leftPos+slot.x+8,topPos+slot.y+8);} public Vec2 sendPosition(){return new Vec2(leftPos+197,topPos+206);}
    public static void register() {
        net.minecraft.client.gui.screens.MenuScreens.register(StockTickerMenu.REQUEST_TYPE,StockTickerScreen::new);net.minecraft.client.gui.screens.MenuScreens.register(StockTickerMenu.CATEGORY_TYPE,StockTickerScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(StockTickerMenu.State.TYPE,(packet,context)->{if(context.player().containerMenu instanceof StockTickerMenu menu&&menu.containerId==packet.menu())menu.receive(packet);});
        ClientPlayNetworking.registerGlobalReceiver(StockTickerMenu.Snapshot.TYPE,(packet,context)->{if(context.player().containerMenu instanceof StockTickerMenu menu&&menu.containerId==packet.menu())menu.receive(packet);});
        ClientPlayNetworking.registerGlobalReceiver(StockTickerCraftingPackets.Catalog.TYPE,(packet,context)->{if(context.player().containerMenu instanceof StockTickerMenu menu&&menu.containerId==packet.menu())menu.receive(packet);});
        ClientPlayNetworking.registerGlobalReceiver(StockTickerCraftingPackets.Preview.TYPE,(packet,context)->{if(context.player().containerMenu instanceof StockTickerMenu menu&&menu.containerId==packet.menu())menu.receive(packet);});
    }
}
