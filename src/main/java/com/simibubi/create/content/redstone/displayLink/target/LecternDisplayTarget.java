package com.simibubi.create.content.redstone.displayLink.target;

import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

/** Actual book conversion/page writes with target-owned absolute page reservations, including newly added pages. */
public final class LecternDisplayTarget extends DisplayTarget {
    @Override public void acceptText(int line,List<MutableComponent> text,DisplayLinkContext context){
        if(line<0||line>=50||text.isEmpty()||!(context.getTargetBlockEntity()instanceof LecternBlockEntity lectern))return;var book=lectern.getBook();if(book.is(Items.WRITABLE_BOOK))book=signBook(book);if(!book.is(Items.WRITTEN_BOOK))return;var content=book.getOrDefault(DataComponents.WRITTEN_BOOK_CONTENT,WrittenBookContent.EMPTY);var pages=new ArrayList<>(content.pages());boolean changed=false;
        for(int offset=0;offset<text.size()&&line+offset<50;offset++){int page=line+offset;if(offset==0)reserve(page,lectern,context);else if(isReserved(page,lectern,context))break;while(pages.size()<=page)pages.add(Filterable.passThrough(Component.empty()));pages.set(page,Filterable.passThrough((Component)text.get(offset).copy()));changed=true;}
        if(changed){book.set(DataComponents.WRITTEN_BOOK_CONTENT,content.withReplacedPages(pages));lectern.setBook(book);lectern.setChanged();context.level().sendBlockUpdated(context.getTargetPos(),lectern.getBlockState(),lectern.getBlockState(),Block.UPDATE_CLIENTS);}
    }
    private ItemStack signBook(ItemStack book){var pages=book.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT,WritableBookContent.EMPTY).pages().stream().map(page->page.<Component>map(Component::literal)).toList();var result=new ItemStack(Items.WRITTEN_BOOK);result.set(DataComponents.WRITTEN_BOOK_CONTENT,new WrittenBookContent(Filterable.passThrough("Printed Book"),"Data Gatherer",0,pages,true));return result;}
    @Override public DisplayTargetStats provideStats(DisplayLinkContext context){return new DisplayTargetStats(context.getTargetBlockEntity()instanceof LecternBlockEntity?50:0,256,this);}
    @Override public Component getLineOptionText(int line){return Component.translatable("create.display_target.page",line+1);}
    @Override public boolean requiresComponentSanitization(){return true;}
}
