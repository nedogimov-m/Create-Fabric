package com.simibubi.create.foundation.item;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.toolbox.ToolboxInventory;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem.PackageOrderData;
import com.simibubi.create.content.logistics.redstoneRequester.AutoRequestData;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.utility.NeoForgePackedValueSemantics;
import java.util.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.*;

/**
 * Explicit sample/predicate equality. Physical stacking, extraction variants, payments and custody
 * continue to compare their exact native components. Keys never replace a real item or component.
 */
public final class ItemSampleEquality {
    public record Key(Item item,Map<DataComponentType<?>,Object> components){}
    private record Counted(Key value,int count){}
    private record LegacyValue(String originalValue){}
    private record Tuple(String type,List<Object> values){}
    public static boolean same(ItemStack left,ItemStack right){return key(left).equals(key(right));}
    public static int hash(ItemStack actual){return key(actual).hashCode();}
    public static Key key(ItemStack actual){return key(actual,0);}
    private static Key key(ItemStack actual,int depth){
        checkDepth(depth);
        if(actual.isEmpty())return new Key(ItemStack.EMPTY.getItem(),Map.of());
        var values=new HashMap<DataComponentType<?>,Object>();
        for(var entry:actual.getComponents())values.put(entry.type(),component(entry.type(),entry.value(),depth+1));
        return new Key(actual.getItem(),Collections.unmodifiableMap(values));
    }
    private static Counted counted(ItemStack actual,int depth){return new Counted(key(actual,depth+1),actual.isEmpty()?0:actual.getCount());}
    private static Object component(DataComponentType<?> type,Object actual,int depth){
        checkDepth(depth);
        if(type==AllDataComponents.MINECRAFT_CONTRAPTION_DATA){
            var tag=(CompoundTag)actual;var sourceValue=NeoForgePackedValueSemantics.validOriginalValue(tag);
            return sourceValue==null?tag.copy():new LegacyValue(sourceValue);
        }
        // The exact registered component supplies the schema. No item-shaped arbitrary NBT search.
        if(type==DataComponents.CONTAINER||type==DataComponents.BUNDLE_CONTENTS||type==DataComponents.CHARGED_PROJECTILES
                ||type==AllDataComponents.FILTER_ITEMS||type==AllDataComponents.PACKAGE_CONTENTS||type==AllDataComponents.LINKED_CONTROLLER_ITEMS)
            return ((ContainerComponent<?>)actual).itemCopies().map(item->counted(item,depth+1)).toList();
        if(type==DataComponents.USE_REMAINDER)return counted(((UseRemainder)actual).convertInto().create(),depth+1);
        if(type==AllDataComponents.SAND_PAPER_POLISHING)return counted((ItemStack)actual,depth+1);
        if(type==DataComponents.CUSTOM_NAME||type==DataComponents.ITEM_NAME)return text((Component)actual,depth+1);
        if(type==DataComponents.LORE){var lore=(ItemLore)actual;return tuple("lore",texts(lore.lines(),depth+1),texts(lore.styledLines(),depth+1));}
        if(type==DataComponents.WRITTEN_BOOK_CONTENT){
            var book=(WrittenBookContent)actual;
            return tuple("book",book.title(),book.author(),book.generation(),book.pages().stream().map(page->tuple("page",text(page.raw(),depth+1),page.filtered().map(value->text(value,depth+1)))).toList(),book.resolved());
        }
        if(type==AllDataComponents.CLIPBOARD_CONTENT){
            var board=(ClipboardContent)actual;
            return tuple("clipboard",board.type(),board.readOnly(),board.previouslyOpenedPage(),board.copiedValues().map(value->NativeItemSampleKeys.clipboardValues(value,depth+1)),
                board.pages().stream().map(page->page.stream().map(entry->tuple("entry",entry.checked,text(entry.text,depth+1),key(entry.icon,depth+1))).toList()).toList());
        }
        if(type==DataComponents.ENTITY_DATA)return entityData((TypedEntityData<?>)actual,depth+1);
        if(type==DataComponents.BLOCK_ENTITY_DATA){
            var data=(TypedEntityData<?>)actual;
            if(data.type() instanceof BlockEntityType<?> nativeType){var id=BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(nativeType);if(id!=null)return tuple("block_entity_data",nativeType,NativeItemSampleKeys.blockEntity(id,data.copyTagWithoutId(),depth+1));}
            return actual;
        }
        if(type==DataComponents.BEES)return ((Bees)actual).bees().stream().map(bee->tuple("bee",entityData(bee.entityData(),depth+1),bee.ticksInHive(),bee.minTicksInHive())).toList();
        if(type==AllDataComponents.TOOLBOX_INVENTORY){
            var inventory=(ToolboxInventory)actual;var stock=new ArrayList<Object>();
            for(int slot=0;slot<inventory.getContainerSize();slot++)stock.add(counted(inventory.getStackInSlot(slot),depth+1));
            return tuple("toolbox",List.copyOf(stock),inventory.getFilters().stream().map(item->counted(item,depth+1)).toList());
        }
        if(type==AllDataComponents.PACKAGE_ORDER_CONTEXT)return order((PackageOrderWithCrafts)actual,depth+1);
        if(type==AllDataComponents.PACKAGE_ORDER_DATA){var data=(PackageOrderData)actual;return tuple("package_order",data.orderId(),data.linkIndex(),data.isFinalLink(),data.fragmentIndex(),data.isFinal(),order(data.orderContext(),depth+1));}
        if(type==AllDataComponents.AUTO_REQUEST_DATA){var data=(AutoRequestData)actual;return tuple("request",order(data.encodedRequest(),depth+1),data.encodedTargetAddress(),data.targetOffset(),data.targetDim(),data.isValid());}
        return actual;
    }
    private static Object entityData(TypedEntityData<?> data,int depth){
        if(data.type() instanceof EntityType<?> nativeType){var id=BuiltInRegistries.ENTITY_TYPE.getKey(nativeType);if(id!=null)return tuple("entity_data",nativeType,NativeItemSampleKeys.entity(id,data.copyTagWithoutId(),depth+1));}
        return data;
    }
    private static Object order(PackageOrderWithCrafts order,int depth){
        if(order==null)return null;checkDepth(depth);
        return tuple("order",order.stacks().stream().map(entry->big(entry,depth+1)).toList(),order.orderedCrafts().stream().map(craft->tuple("craft",craft.count(),craft.pattern().stacks().stream().map(entry->big(entry,depth+1)).toList())).toList());
    }
    private static Object big(BigItemStack entry,int depth){return tuple("big",key(entry.stack,depth+1),entry.count);}
    private static List<Object> texts(List<Component> values,int depth){return values.stream().map(value->text(value,depth+1)).toList();}
    private static Object text(Component actual,int depth){
        checkDepth(depth);Object contents=actual.getContents();
        if(contents instanceof TranslatableContents translation)contents=tuple("translate",translation.getKey(),translation.getFallback(),Arrays.stream(translation.getArgs()).map(arg->arg instanceof Component child?text(child,depth+1):arg).toList());
        else if(contents instanceof SelectorContents selector)contents=tuple("selector",selector.selector(),selector.separator().map(child->text(child,depth+1)));
        else if(contents instanceof NbtContents nbt)contents=tuple("nbt",nbt.nbtPath(),nbt.interpreting(),nbt.plain(),nbt.dataSource(),nbt.separator().map(child->text(child,depth+1)));
        else if(contents instanceof ObjectContents object)contents=tuple("object",object.contents(),object.fallback().map(child->text(child,depth+1)));
        var style=actual.getStyle();var hover=style.getHoverEvent();Object hoverValue=hover;
        if(hover instanceof HoverEvent.ShowItem item)hoverValue=tuple("show_item",counted(item.item().create(),depth+1));
        else if(hover instanceof HoverEvent.ShowText show)hoverValue=tuple("show_text",text(show.value(),depth+1));
        else if(hover instanceof HoverEvent.ShowEntity show){var entity=show.entity();hoverValue=tuple("show_entity",entity.type,entity.uuid,entity.name.map(name->text(name,depth+1)));}
        return tuple("text",contents,style.withHoverEvent(null),hoverValue,texts(actual.getSiblings(),depth+1));
    }
    private static Tuple tuple(String type,Object... values){return new Tuple(type,Collections.unmodifiableList(Arrays.asList(values)));}
    private static void checkDepth(int depth){if(depth>256)throw new IllegalArgumentException("Typed item sample exceeds native nesting bounds");}
    private ItemSampleEquality(){}
}
