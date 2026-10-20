package com.simibubi.create.foundation.item;

import com.simibubi.create.foundation.utility.NeoForgePackedValueSemantics;
import java.util.*;
import java.util.function.Function;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;

/**
 * Exact native serialized item leaves under known clipboard/entity/block-entity schemas.
 * Unrecognized fields keep their exact typed NBT equality. This is never a loader or migration.
 */
final class NativeItemSampleKeys {
    private record Fields(Map<String,Object> values){}
    private record Rows(List<Object> values){}
    private record OriginalPackedValue(String value){}

    static Object clipboardValues(CompoundTag actual,int depth){
        var values=fields(actual,depth);
        rewrite(actual,values,"Filtering",value->field(value,"Filter",item->item(item,depth+1),depth+1));
        rewrite(actual,values,"Frequencies",value->{var row=compound(value,depth+1);if(row==null)return value.copy();var result=fields(row,depth+1);for(var key:List.of("First","Last"))rewrite(row,result,key,item->item(item,depth+1));return wrap(result);});
        return wrap(values);
    }

    static Object entity(Identifier actualType,CompoundTag actual,int depth){
        check(depth);
        if(!actualType.getNamespace().equals("minecraft"))return actual.copy();
        var values=fields(actual,depth);
        rewrite(actual,values,"equipment",value->{var row=compound(value,depth+1);if(row==null)return value.copy();var result=fields(row,depth+1);for(var slot:List.of("mainhand","offhand","feet","legs","chest","head","body","saddle"))rewrite(row,result,slot,item->item(item,depth+1));return wrap(result);});
        for(var key:List.of("Item","item","weapon","FireworksItem"))rewrite(actual,values,key,item->item(item,depth+1));
        for(var key:List.of("Items","Inventory"))rewrite(actual,values,key,items->rows(items,item->item(item,depth+1),depth+1));
        rewrite(actual,values,"Offers",offers->field(offers,"Recipes",recipes->rows(recipes,offer->{var row=compound(offer,depth+1);if(row==null)return offer.copy();var result=fields(row,depth+1);for(var key:List.of("buy","buyB","sell"))rewrite(row,result,key,item->item(item,depth+1));return wrap(result);},depth+1),depth+1));
        rewrite(actual,values,"Passengers",passengers->rows(passengers,child->{var row=compound(child,depth+1);if(row==null||!(row.get("id") instanceof StringTag id))return child.copy();var type=Identifier.tryParse(id.value());return type==null?child.copy():entity(type,row,depth+1);},depth+1));
        rewrite(actual,values,"CustomName",text->text(text,depth+1));
        return wrap(values);
    }

    static Object blockEntity(Identifier actualType,CompoundTag actual,int depth){
        check(depth);var namespace=actualType.getNamespace();
        if(!namespace.equals("minecraft")&&!namespace.equals("create"))return actual.copy();
        var values=fields(actual,depth);
        rewrite(actual,values,"components",components->components(components,depth+1));
        if(namespace.equals("minecraft")){
            if(Set.of("chest","trapped_chest","barrel","hopper","dispenser","dropper","shulker_box","brewing_stand","furnace","smoker","blast_furnace","campfire","crafter","chiseled_bookshelf").contains(actualType.getPath()))
                rewrite(actual,values,"Items",items->rows(items,item->item(item,depth+1),depth+1));
            if(actualType.getPath().equals("lectern"))rewrite(actual,values,"Book",item->item(item,depth+1));
            if(actualType.getPath().equals("jukebox"))rewrite(actual,values,"RecordItem",item->item(item,depth+1));
            if(actualType.getPath().equals("decorated_pot"))rewrite(actual,values,"item",item->item(item,depth+1));
            if(actualType.getPath().equals("beehive"))rewrite(actual,values,"bees",bees->bees(bees,depth+1));
            if(actualType.getPath().equals("sign")||actualType.getPath().equals("hanging_sign"))for(var side:List.of("front_text","back_text"))rewrite(actual,values,side,tag->{var row=compound(tag,depth+1);if(row==null)return tag.copy();var result=fields(row,depth+1);for(var key:List.of("messages","filtered_messages"))rewrite(row,result,key,messages->rows(messages,line->text(line,depth+1),depth+1));return wrap(result);});
        }else{
            // Actual common SmartBlockEntity behavior serializers. Machine-specific
            // payloads remain exact unless their native schema is explicitly listed.
            for(var key:List.of("Filter","FrequencyFirst","FrequencyLast"))rewrite(actual,values,key,item->item(item,depth+1));
            for(var side:List.of("DOWN","UP","NORTH","SOUTH","WEST","EAST"))rewrite(actual,values,"Filter"+side,value->field(value,"Filter",item->item(item,depth+1),depth+1));
            switch(actualType.getPath()){
                case "clipboard"->rewrite(actual,values,"Clipboard",item->item(item,depth+1));
                case "redstone_link"->{rewrite(actual,values,"FrequencyFirst",item->item(item,depth+1));rewrite(actual,values,"FrequencyLast",item->item(item,depth+1));}
                case "smart_observer","content_observer","creative_crate"->rewrite(actual,values,"Item",item->item(item,depth+1));
                case "saw"->rewrite(actual,values,"Inventory",value->{var row=compound(value,depth+1);if(row==null)return value.copy();var result=fields(row,depth+1);rewrite(row,result,"Input",item->item(item,depth+1));rewrite(row,result,"Outputs",items->rows(items,item->item(item,depth+1),depth+1));return wrap(result);});
                default->{}
            }
        }
        return wrap(values);
    }

    private static Object item(Tag actual,int depth){
        var item=compound(actual,depth);if(item==null)return actual.copy();var values=fields(item,depth);
        rewrite(item,values,"components",components->components(components,depth+1));return wrap(values);
    }
    private static Object components(Tag actual,int depth){
        var components=compound(actual,depth);if(components==null)return actual.copy();var values=fields(components,depth);
        for(var key:components.keySet()){
            var value=components.get(key);Object semantic=switch(key){
                case "create:minecart_contraption_data"->{var plan=compound(value,depth+1);var original=plan==null?null:NeoForgePackedValueSemantics.validOriginalValue(plan);yield original==null?value.copy():new OriginalPackedValue(original);}
                case "minecraft:custom_name","minecraft:item_name"->text(value,depth+1);
                case "minecraft:lore"->rows(value,line->text(line,depth+1),depth+1);
                case "minecraft:container","create:filter_items","create:package_contents","create:linked_controller_items"->rows(value,row->field(row,"item",item->item(item,depth+1),depth+1),depth+1);
                case "minecraft:bundle_contents","minecraft:charged_projectiles"->rows(value,item->item(item,depth+1),depth+1);
                case "minecraft:use_remainder"->item(value,depth+1);
                case "create:sand_paper_polishing"->field(value,"item",item->item(item,depth+1),depth+1);
                case "minecraft:written_book_content"->field(value,"pages",pages->rows(pages,page->{if(page instanceof StringTag)return text(page,depth+1);var row=compound(page,depth+1);if(row==null)return page.copy();var result=fields(row,depth+1);for(var name:List.of("raw","filtered"))rewrite(row,result,name,text->text(text,depth+1));return wrap(result);},depth+1),depth+1);
                case "create:clipboard_content"->clipboard(value,depth+1);
                case "create:toolbox_inventory"->toolbox(value,depth+1);
                case "create:package_order_context"->order(value,depth+1);
                case "create:package_order_data"->field(value,"order_context",order->order(order,depth+1),depth+1);
                case "create:auto_request_data"->field(value,"encoded_request",order->order(order,depth+1),depth+1);
                case "minecraft:entity_data"->{var row=compound(value,depth+1);var type=type(row);yield type==null?value.copy():entity(type,row,depth+1);}
                case "minecraft:block_entity_data"->{var row=compound(value,depth+1);var type=type(row);yield type==null?value.copy():blockEntity(type,row,depth+1);}
                case "minecraft:bees"->bees(value,depth+1);
                default->value.copy();
            };values.put(key,semantic);
        }
        return wrap(values);
    }
    private static Object clipboard(Tag actual,int depth){
        var row=compound(actual,depth);if(row==null)return actual.copy();var values=fields(row,depth);
        rewrite(row,values,"copied_values",value->{var source=compound(value,depth+1);return source==null?value.copy():clipboardValues(source,depth+1);});
        rewrite(row,values,"pages",pages->rows(pages,page->rows(page,entry->{var original=compound(entry,depth+1);if(original==null)return entry.copy();var result=fields(original,depth+1);rewrite(original,result,"icon",item->item(item,depth+1));rewrite(original,result,"text",text->text(text,depth+1));return wrap(result);},depth+1),depth+1));return wrap(values);
    }
    private static Object toolbox(Tag actual,int depth){
        var row=compound(actual,depth);if(row==null)return actual.copy();var values=fields(row,depth);
        rewrite(row,values,"filters",items->rows(items,item->item(item,depth+1),depth+1));
        rewrite(row,values,"items",value->field(value,"items",items->{var slots=compound(items,depth+1);if(slots==null)return items.copy();var result=fields(slots,depth+1);for(int slot=0;slot<32;slot++)rewrite(slots,result,Integer.toString(slot),item->item(item,depth+1));return wrap(result);},depth+1));return wrap(values);
    }
    private static Object order(Tag actual,int depth){
        var row=compound(actual,depth);if(row==null)return actual.copy();var values=fields(row,depth);
        rewrite(row,values,"ordered_stacks",value->field(value,"entries",items->rows(items,entry->field(entry,"item_stack",item->item(item,depth+1),depth+1),depth+1),depth+1));
        rewrite(row,values,"ordered_crafts",crafts->rows(crafts,craft->field(craft,"pattern",pattern->field(pattern,"entries",entries->rows(entries,entry->field(entry,"item_stack",item->item(item,depth+1),depth+1),depth+1),depth+1),depth+1),depth+1));return wrap(values);
    }
    private static Object bees(Tag actual,int depth){return rows(actual,bee->field(bee,"entity_data",value->{var data=compound(value,depth+1);var type=type(data);return type==null?value.copy():entity(type,data,depth+1);},depth+1),depth+1);}
    private static Object text(Tag actual,int depth){
        check(depth);if(actual instanceof ListTag)return rows(actual,value->text(value,depth+1),depth+1);var row=compound(actual,depth);if(row==null)return actual.copy();var values=fields(row,depth);
        for(var key:List.of("extra","with"))rewrite(row,values,key,children->rows(children,value->text(value,depth+1),depth+1));
        for(var key:List.of("separator","fallback"))rewrite(row,values,key,value->text(value,depth+1));
        rewrite(row,values,"hover_event",hover->{var data=compound(hover,depth+1);if(data==null)return hover.copy();var result=fields(data,depth+1);switch(data.getStringOr("action","")){
            case "show_item"->rewrite(data,result,"item",item->item(item,depth+1));
            case "show_text"->rewrite(data,result,"value",value->text(value,depth+1));
            case "show_entity"->rewrite(data,result,"entity",entity->field(entity,"name",name->text(name,depth+1),depth+1));
            default->{}
        }return wrap(result);});return wrap(values);
    }
    private static Object field(Tag actual,String name,Function<Tag,Object> transform,int depth){var row=compound(actual,depth);if(row==null)return actual.copy();var values=fields(row,depth);rewrite(row,values,name,transform);return wrap(values);}
    private static Identifier type(CompoundTag actual){return actual!=null&&actual.get("id") instanceof StringTag id?Identifier.tryParse(id.value()):null;}
    private static CompoundTag compound(Tag actual,int depth){check(depth);return actual instanceof CompoundTag value?value:null;}
    private static Object rows(Tag actual,Function<Tag,Object> transform,int depth){check(depth);if(!(actual instanceof ListTag list))return actual.copy();return new Rows(list.stream().map(transform).toList());}
    private static Map<String,Object> fields(CompoundTag actual,int depth){check(depth);var values=new HashMap<String,Object>();for(var key:actual.keySet())values.put(key,actual.get(key).copy());return values;}
    private static void rewrite(CompoundTag actual,Map<String,Object> values,String name,Function<Tag,Object> transform){if(actual.contains(name))values.put(name,transform.apply(actual.get(name)));}
    private static Fields wrap(Map<String,Object> values){return new Fields(Collections.unmodifiableMap(values));}
    private static void check(int depth){if(depth>256)throw new IllegalArgumentException("Known native serialized sample exceeds nesting bounds");}
    private NativeItemSampleKeys(){}
}
