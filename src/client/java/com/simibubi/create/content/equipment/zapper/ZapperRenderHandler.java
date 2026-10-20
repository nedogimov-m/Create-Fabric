package com.simibubi.create.content.equipment.zapper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllSoundEvents;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
public class ZapperRenderHandler extends ShootableGadgetRenderHandler {
    public List<LaserBeam> cachedBeams=new LinkedList<>();
    @Override protected boolean appliesTo(ItemStack stack){return stack.getItem() instanceof ZapperItem;}
    @Override public void tick(){super.tick();cachedBeams.removeIf(beam->beam.itensity<.1f);cachedBeams.forEach(beam->beam.itensity*=.6f);}
    @Override protected void transformTool(PoseStack pose,float flip,float equip,float recoil,float partial){pose.translate(flip*-.1,.1,-.4);pose.rotateDegrees(Axis.YP,flip*5);}
    @Override protected void transformHand(PoseStack pose,float flip,float equip,float recoil,float partial){}
    @Override public void playSound(InteractionHand hand,Vec3 position){var client=Minecraft.getInstance();if(client.level!=null)client.level.playLocalSound(position.x,position.y,position.z,AllSoundEvents.WORLDSHAPER_PLACE,net.minecraft.sounds.SoundSource.PLAYERS,.1f,hand==InteractionHand.MAIN_HAND?.1f:.9f,false);}
    public void addBeam(LaserBeam beam){var level=Minecraft.getInstance().level;if(level==null)return;for(int i=0;i<10;i++){var random=level.getRandom();level.addParticle(ParticleTypes.END_ROD,beam.end.x,beam.end.y,beam.end.z,(random.nextDouble()-.5)*.2,(random.nextDouble()-.5)*.2,(random.nextDouble()-.5)*.2);level.addParticle(ParticleTypes.FIREWORK,beam.end.x+(random.nextDouble()-.5)*.2,beam.end.y+(random.nextDouble()-.5)*.2,beam.end.z+(random.nextDouble()-.5)*.2,0,0,0);}cachedBeams.add(beam);}
    @Override public void reset(){super.reset();cachedBeams.clear();}
    public static class LaserBeam {float itensity=1;Vec3 start,end;public LaserBeam(Vec3 start,Vec3 end){this.start=start;this.end=end;}}
    public List<Beam> extract(){return cachedBeams.stream().map(beam->new Beam(beam.start,beam.end,beam.itensity)).toList();}
    public record Beam(Vec3 start,Vec3 end,float intensity){}
}
