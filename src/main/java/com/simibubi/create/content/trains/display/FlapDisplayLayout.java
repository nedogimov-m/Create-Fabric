package com.simibubi.create.content.trains.display;

import java.util.*;
import net.minecraft.world.level.storage.*;

/** Source Key/Sections layout with guarded structural replacement on native data updates. */
public final class FlapDisplayLayout {
    private List<FlapDisplaySection> sections;
    private String layoutKey;
    public FlapDisplayLayout(int maxCharCount){loadDefault(maxCharCount);}
    public void loadDefault(int count){configure("Default",List.of(new FlapDisplaySection(Math.max(0,count)*FlapDisplaySection.MONOSPACE,"alphabet",false,false)));}
    public boolean isLayout(String key){return layoutKey.equals(key);}
    public String getKey(){return layoutKey;}
    public void configure(String key,List<FlapDisplaySection> sections){if(sections.isEmpty()||sections.size()>256)throw new IllegalArgumentException("A display layout needs1..256 actual sections");this.layoutKey=Objects.requireNonNull(key);this.sections=new ArrayList<>(sections);}
    public List<FlapDisplaySection> getSections(){return Collections.unmodifiableList(sections);}
    public void write(ValueOutput out){out.putString("Key",layoutKey);var values=out.childrenList("Sections");for(var section:sections)section.write(values.addChild());}
    public void read(ValueInput in){
        var key=in.getStringOr("Key",layoutKey);var incoming=in.childrenListOrEmpty("Sections").stream().limit(256).toList();if(incoming.isEmpty())return;
        boolean replace=!key.equals(layoutKey)||incoming.size()!=sections.size();if(!replace)for(int i=0;i<incoming.size();i++)if(!sections.get(i).sameStructure(incoming.get(i))){replace=true;break;}
        layoutKey=key;if(replace){sections=incoming.stream().map(FlapDisplaySection::load).toList();return;}for(int i=0;i<incoming.size();i++)sections.get(i).update(incoming.get(i));
    }
}
