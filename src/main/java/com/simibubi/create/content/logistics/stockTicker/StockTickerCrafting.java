package com.simibubi.create.content.logistics.stockTicker;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import java.util.*;
import com.simibubi.create.foundation.item.ItemSampleEquality;
import com.simibubi.create.foundation.item.ItemSampleEquality.Key;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Real server recipes and component-sensitive stock allocation; no per-item iteration. */
public final class StockTickerCrafting {
    public record Selection(Identifier recipe, int crafts) {}
    public record Adjustment(Identifier recipe, int crafts) {}
    public record Choice(Identifier recipe, ItemStack result, List<ItemStack> grid, int available) {}
    public record RecipePlan(ItemStack result, List<BigItemStack> materials, List<PackageOrderWithCrafts.CraftingEntry> patterns, int crafts) {
        static RecipePlan empty() { return new RecipePlan(ItemStack.EMPTY,List.of(),List.of(),0); }
    }
    public record Cart(PackageOrderWithCrafts order, List<Selection> selections, List<Choice> results, List<BigItemStack> manual) {
        public Cart(PackageOrderWithCrafts order,List<Selection> selections,List<Choice> results){this(order,selections,results,List.of());}
    }
    private record Slot(int index, Ingredient ingredient) {}
    private record Template(Identifier id, CraftingRecipe recipe, List<Slot> slots) {}
    private record Stock(ItemStack sample, long count) {}
    private static final TagKey<RecipeSerializer<?>> IGNORED=TagKey.create(Registries.RECIPE_SERIALIZER,Create.asResource("automation_ignore"));

    private static Template template(RecipeHolder<?> holder) {
        if(!(holder.value() instanceof CraftingRecipe recipe)||holder.id().identifier().getPath().endsWith("_manual_only")||BuiltInRegistries.RECIPE_SERIALIZER.wrapAsHolder(recipe.getSerializer()).is(IGNORED))return null;
        var placement=recipe.placementInfo();if(placement.isImpossibleToPlace()||placement.ingredients().isEmpty()||placement.slotsToIngredientIndex().size()>9)return null;
        int size=placement.slotsToIngredientIndex().size();
        int width=recipe instanceof ShapedRecipe shaped?shaped.getWidth():Math.min(3,size);
        // Retain the source conversion's positioning, including a full shapeless row.
        int height=recipe instanceof ShapedRecipe shaped?shaped.getHeight():Math.min(3,size/3+1);
        if(width<1||width>3||height<1||height>3)return null;
        var slots=new ArrayList<Slot>();
        for(int i=0;i<size;i++){
            int ingredient=placement.slotsToIngredientIndex().getInt(i);if(ingredient<0)continue;
            int index=(i/width+(height==1?1:0))*3+i%width+(width==1?1:0);
            if(index>=9)return null;slots.add(new Slot(index,placement.ingredients().get(ingredient)));
        }
        return slots.isEmpty()?null:new Template(holder.id().identifier(),recipe,List.copyOf(slots));
    }
    private static Template find(Level level,Identifier id) {
        if(level.getServer()==null)return null;
        return level.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,id)).map(StockTickerCrafting::template).orElse(null);
    }
    private static LinkedHashMap<Key,Stock> stock(List<BigItemStack> entries) {
        var result=new LinkedHashMap<Key,Stock>();
        for(var entry:entries)if(!entry.stack.isEmpty()&&entry.count>0){var variant=ItemSampleEquality.key(entry.stack);var previous=result.get(variant);result.put(variant,new Stock(entry.stack.copyWithCount(1),Math.min(BigItemStack.INF,(previous==null?0:previous.count)+(long)entry.count)));}
        return result;
    }
    private static List<ItemStack> representative(Template template,List<Stock> stock) {
        var grid=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));
        for(var slot:template.slots){var sample=stock.stream().filter(entry->slot.ingredient.test(entry.sample)).findFirst().map(Stock::sample).orElseGet(()->slot.ingredient.items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY));grid.set(slot.index,sample.copyWithCount(1));}
        return grid;
    }
    public static List<Choice> catalog(Level level,InventorySummary summary) {
        if(level.getServer()==null)return List.of();var stocks=stock(summary.getStacksByCount());var entries=new ArrayList<>(stocks.values());var result=new ArrayList<Choice>();
        for(var holder:level.getServer().getRecipeManager().getRecipes()){
            var template=template(holder);if(template==null)continue;var grid=representative(template,entries);var input=CraftingInput.of(3,3,grid);if(!template.recipe.matches(input,level))continue;var output=template.recipe.assemble(input);if(output.isEmpty())continue;
            var plan=plan(level,template,stocks,Integer.MAX_VALUE,Set.of(),9);result.add(new Choice(template.id,plan.crafts>0?plan.result:output,List.copyOf(plan.crafts>0?plan.patterns.getFirst().pattern().stacks().stream().map(entry->entry.stack.copy()).toList():grid),plan.crafts));
        }
        result.sort(Comparator.comparing(choice->choice.recipe.toString()));return List.copyOf(result);
    }
    public static RecipePlan plan(Level level,Identifier recipe,List<BigItemStack> available,int requested,Set<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> existingTypes,int newTypeLimit) {
        var template=find(level,recipe);return template==null?RecipePlan.empty():plan(level,template,stock(available),requested,existingTypes.stream().map(value->ItemSampleEquality.key(value.toStack())).collect(java.util.stream.Collectors.toSet()),newTypeLimit);
    }
    private static RecipePlan plan(Level level,Template template,Map<Key,Stock> stock,int requested,Set<Key> existingTypes,int newTypeLimit) {
        if(requested<=0)return RecipePlan.empty();
        var candidates=new ArrayList<List<Key>>();
        var sorted=stock.entrySet().stream().filter(entry->entry.getValue().count>0).sorted(Comparator.<Map.Entry<Key,Stock>>comparingLong(entry->-entry.getValue().count).thenComparing(entry->BuiltInRegistries.ITEM.getKey(entry.getKey().item()).toString()).thenComparingInt(entry->entry.getKey().hashCode())).toList();
        for(var slot:template.slots){var matches=new ArrayList<Key>();for(var entry:sorted)if(slot.ingredient.test(entry.getValue().sample))matches.add(entry.getKey());if(matches.isEmpty())return RecipePlan.empty();candidates.add(matches);}
        if(newTypeLimit>=0)while(newTypes(candidates,existingTypes)>newTypeLimit){
            List<Key> longest=null;long most=-1;
            for(var list:candidates){long count=list.stream().filter(item->!existingTypes.contains(item)).count();if(count>most){longest=list;most=count;}}
            if(longest==null||most==0)return RecipePlan.empty();Key remove=null;
            for(int i=longest.size()-1;i>=0;i--){var candidate=longest.get(i);if(!existingTypes.contains(candidate)&&candidates.stream().noneMatch(list->list.size()==1&&list.contains(candidate))){remove=candidate;break;}}
            if(remove==null){for(var list:candidates)for(int i=list.size()-1;i>=0;i--){var candidate=list.get(i);if(!existingTypes.contains(candidate)&&candidates.stream().noneMatch(other->other.size()==1&&other.contains(candidate))){remove=candidate;break;}}}
            if(remove==null)return RecipePlan.empty();
            for(var list:candidates)list.remove(remove);if(candidates.stream().anyMatch(List::isEmpty))return RecipePlan.empty();
        }
        var variants=sorted.stream().map(Map.Entry::getKey).filter(item->candidates.stream().anyMatch(list->list.contains(item))).toList();
        var sampleGrid=representative(template,variants.stream().map(stock::get).toList());var input=CraftingInput.of(3,3,sampleGrid);
        if(!template.recipe.matches(input,level))return RecipePlan.empty();var result=template.recipe.assemble(input);if(result.isEmpty())return RecipePlan.empty();
        long upper=Math.min(requested,BigItemStack.INF/Math.max(1,result.getCount()));
        for(var list:candidates){long total=0;for(var item:list)total+=stock.get(item).count;upper=Math.min(upper,total);}
        long low=0,high=upper;
        while(low<high){long middle=(low+high+1)/2;if(allocate(variants,candidates,stock,middle)!=null)low=middle;else high=middle-1;}
        if(low==0)return RecipePlan.empty();var allocation=allocate(variants,candidates,stock,low);
        var used=new LinkedHashMap<Key,Long>();var patterns=new LinkedHashMap<PackageOrder,Integer>();long left=low;ItemStack actualResult=ItemStack.EMPTY;
        while(left>0){
            var grid=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));var chosen=new int[candidates.size()];long batch=left;
            for(int slot=0;slot<chosen.length;slot++)for(int item=0;item<variants.size();item++)if(allocation[item][slot]>0){chosen[slot]=item;batch=Math.min(batch,allocation[item][slot]);grid.set(template.slots.get(slot).index,stock.get(variants.get(item)).sample.copyWithCount(1));break;}
            var crafting=CraftingInput.of(3,3,grid);if(!template.recipe.matches(crafting,level)||template.recipe.assemble(crafting).isEmpty())return RecipePlan.empty();if(actualResult.isEmpty())actualResult=template.recipe.assemble(crafting).copy();
            var pattern=new PackageOrder(grid.stream().map(stack->new BigItemStack(stack.copy(),1)).toList());patterns.merge(pattern,(int)batch,Integer::sum);
            for(int slot=0;slot<chosen.length;slot++){allocation[chosen[slot]][slot]-=batch;used.merge(variants.get(chosen[slot]),batch,Long::sum);}left-=batch;
        }
        return new RecipePlan(actualResult,used.entrySet().stream().map(entry->new BigItemStack(stock.get(entry.getKey()).sample.copy(),entry.getValue().intValue())).toList(),patterns.entrySet().stream().map(entry->new PackageOrderWithCrafts.CraftingEntry(entry.getKey(),entry.getValue())).toList(),(int)low);
    }
    private static long newTypes(List<List<Key>> choices,Set<Key> existing){return choices.stream().flatMap(List::stream).distinct().filter(item->!existing.contains(item)).count();}
    private static long[][] allocate(List<Key> variants,List<List<Key>> choices,Map<Key,Stock> stock,long crafts) {
        int items=variants.size(),slots=choices.size(),sink=1+items+slots;var flow=new Flow(sink+1);var edges=new Flow.Edge[items][slots];
        for(int i=0;i<items;i++){flow.edge(0,1+i,stock.get(variants.get(i)).count);for(int s=0;s<slots;s++)if(choices.get(s).contains(variants.get(i)))edges[i][s]=flow.edge(1+i,1+items+s,crafts);}
        for(int s=0;s<slots;s++)flow.edge(1+items+s,sink,crafts);
        if(flow.max(0,sink)!=(long)slots*crafts)return null;var result=new long[items][slots];for(int i=0;i<items;i++)for(int s=0;s<slots;s++)if(edges[i][s]!=null)result[i][s]=crafts-edges[i][s].capacity;return result;
    }
    public static Cart cart(Level level,InventorySummary summary,List<BigItemStack> manual,List<Selection> requested) {
        var remaining=stock(summary.getStacksByCount());var ordered=new LinkedHashMap<Key,BigItemStack>();
        for(var entry:manual){if(entry.stack.isEmpty()||entry.count<1)continue;var variant=ItemSampleEquality.key(entry.stack);var available=remaining.get(variant);if(available==null||ordered.size()>=9&&!ordered.containsKey(variant))continue;int count=(int)Math.min(entry.count,available.count);if(count==0)continue;ordered.computeIfAbsent(variant,key->new BigItemStack(available.sample.copy(),0)).count+=count;remaining.put(variant,new Stock(available.sample,available.count-count));}
        var extras=ordered.values().stream().map(entry->new BigItemStack(entry.stack.copy(),entry.count)).toList();boolean extraItems=!ordered.isEmpty();var patterns=new ArrayList<PackageOrderWithCrafts.CraftingEntry>();var accepted=new ArrayList<Selection>();var results=new ArrayList<Choice>();
        for(var request:requested){var template=find(level,request.recipe);if(template==null)continue;var plan=plan(level,template,remaining,request.crafts,ordered.keySet(),9-ordered.size());if(plan.crafts==0)continue;
            for(var entry:plan.materials){var variant=ItemSampleEquality.key(entry.stack);var available=remaining.get(variant);ordered.computeIfAbsent(variant,key->new BigItemStack(entry.stack.copy(),0)).count+=entry.count;remaining.put(variant,new Stock(available.sample,available.count-entry.count));}
            patterns.addAll(plan.patterns);accepted.add(new Selection(request.recipe,plan.crafts));results.add(new Choice(request.recipe,plan.result.copy(),plan.patterns.getFirst().pattern().stacks().stream().map(entry->entry.stack.copy()).toList(),plan.crafts));
        }
        var order=new PackageOrderWithCrafts(new PackageOrder(List.copyOf(ordered.values())),extraItems?List.of():List.copyOf(patterns));return new Cart(order,List.copyOf(accepted),List.copyOf(results),extras);
    }
    /** Source updateCraftableAmounts: keep the edited materials and only reduce queued recipes. */
    public static Cart reconcile(Level level,InventorySummary summary,List<BigItemStack> materials,List<Selection> requested) {
        var available=stock(summary.getStacksByCount());var remaining=new LinkedHashMap<Key,Stock>();
        for(var entry:stock(materials).entrySet()){
            var supply=available.get(entry.getKey());if(supply==null||remaining.size()>=9)continue;
            long count=Math.min(supply.count,entry.getValue().count);if(count>0)remaining.put(entry.getKey(),new Stock(supply.sample.copy(),count));
        }
        var ordered=remaining.values().stream().map(entry->new BigItemStack(entry.sample.copy(),(int)entry.count)).toList();
        var patterns=new ArrayList<PackageOrderWithCrafts.CraftingEntry>();var accepted=new ArrayList<Selection>();var results=new ArrayList<Choice>();
        for(var request:requested){
            var template=find(level,request.recipe);if(template==null)continue;var plan=plan(level,template,remaining,request.crafts,remaining.keySet(),0);if(plan.crafts==0)continue;
            for(var entry:plan.materials){var variant=ItemSampleEquality.key(entry.stack);var supply=remaining.get(variant);remaining.put(variant,new Stock(supply.sample,supply.count-entry.count));}
            patterns.addAll(plan.patterns);accepted.add(new Selection(request.recipe,plan.crafts));results.add(new Choice(request.recipe,plan.result.copy(),plan.patterns.getFirst().pattern().stacks().stream().map(entry->entry.stack.copy()).toList(),plan.crafts));
        }
        var extras=remaining.values().stream().filter(entry->entry.count>0).map(entry->new BigItemStack(entry.sample.copy(),(int)entry.count)).toList();
        var order=new PackageOrderWithCrafts(new PackageOrder(ordered),extras.isEmpty()?List.copyOf(patterns):List.of());
        return new Cart(order,List.copyOf(accepted),List.copyOf(results),extras);
    }
    /** Source requestCraftable: add/remove ingredients without replacing the existing material choices. */
    public static Cart adjust(Level level,InventorySummary summary,List<BigItemStack> materials,List<Selection> requested,Adjustment change) {
        var current=reconcile(level,summary,materials,requested);var template=find(level,change.recipe);if(template==null||change.crafts==0)return current;
        var selected=new LinkedHashMap<Identifier,Integer>();for(var request:current.selections)selected.merge(request.recipe,request.crafts,(a,b)->(int)Math.min(BigItemStack.INF,(long)a+b));
        int previous=selected.getOrDefault(change.recipe,0);boolean remove=change.crafts<0;
        int difference=(int)Math.min(remove?previous:BigItemStack.INF-previous,Math.abs((long)change.crafts));if(difference==0)return current;
        var ordered=stock(current.order.stacks());var available=remove?new LinkedHashMap<>(ordered):stock(summary.getStacksByCount());
        if(!remove)for(var entry:ordered.entrySet()){var supply=available.get(entry.getKey());available.put(entry.getKey(),new Stock(supply.sample,Math.max(0,supply.count-entry.getValue().count)));}
        var plan=plan(level,template,available,difference,ordered.keySet(),remove?0:9-ordered.size());
        if(!remove&&plan.crafts>0&&(long)(previous+plan.crafts)*plan.result.getCount()>BigItemStack.INF)plan=plan(level,template,available,Math.max(0,BigItemStack.INF/plan.result.getCount()-previous),ordered.keySet(),9-ordered.size());
        if(plan.crafts==0)return current;
        for(var entry:plan.materials){var variant=ItemSampleEquality.key(entry.stack);var old=ordered.get(variant);long count=(old==null?0:old.count)+(remove?-entry.count:entry.count);if(count==0)ordered.remove(variant);else ordered.put(variant,new Stock(entry.stack.copy(),count));}
        int count=previous+(remove?-plan.crafts:plan.crafts);if(count==0)selected.remove(change.recipe);else selected.put(change.recipe,count);
        return reconcile(level,summary,ordered.values().stream().map(entry->new BigItemStack(entry.sample.copy(),(int)entry.count)).toList(),selected.entrySet().stream().map(entry->new Selection(entry.getKey(),entry.getValue())).toList());
    }
    /** Small capacitated graph: variants feed occupied recipe slots. */
    private static final class Flow {
        private static final class Edge { final int to,reverse;long capacity;Edge(int to,int reverse,long capacity){this.to=to;this.reverse=reverse;this.capacity=capacity;} }
        private final List<List<Edge>> graph=new ArrayList<>();private final int[] level,next;
        Flow(int nodes){for(int i=0;i<nodes;i++)graph.add(new ArrayList<>());level=new int[nodes];next=new int[nodes];}
        Edge edge(int from,int to,long capacity){var edge=new Edge(to,graph.get(to).size(),capacity);graph.get(from).add(edge);graph.get(to).add(new Edge(from,graph.get(from).size()-1,0));return edge;}
        long max(int source,int sink){long total=0;while(bfs(source,sink)){Arrays.fill(next,0);long amount;while((amount=send(source,sink,Long.MAX_VALUE))>0)total+=amount;}return total;}
        boolean bfs(int source,int sink){Arrays.fill(level,-1);var queue=new ArrayDeque<Integer>();queue.add(source);level[source]=0;while(!queue.isEmpty()){int node=queue.removeFirst();for(var edge:graph.get(node))if(edge.capacity>0&&level[edge.to]<0){level[edge.to]=level[node]+1;queue.add(edge.to);}}return level[sink]>=0;}
        long send(int node,int sink,long amount){if(node==sink)return amount;for(;next[node]<graph.get(node).size();next[node]++){var edge=graph.get(node).get(next[node]);if(edge.capacity<=0||level[edge.to]!=level[node]+1)continue;long sent=send(edge.to,sink,Math.min(amount,edge.capacity));if(sent>0){edge.capacity-=sent;graph.get(edge.to).get(edge.reverse).capacity+=sent;return sent;}}return 0;}
    }
    private StockTickerCrafting(){}
}
