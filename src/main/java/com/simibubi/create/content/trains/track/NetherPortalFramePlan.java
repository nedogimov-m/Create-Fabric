package com.simibubi.create.content.trains.track;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Read-only native26.3 PortalForcer.createPortal selection/write-order plan over actual loaded chunks.
 * This class neither installs terrain nor claims that a planned portal/exit already exists.
 */
public final class NetherPortalFramePlan {
    public record Write(BlockPos position,BlockState state,int flags){}
    private record Column(int x,int z,int firstAvailable){}
    private record Border(double minX,double minZ,double maxX,double maxZ){
        static Border actual(ServerLevel level){var b=level.getWorldBorder();return new Border(b.getMinX(),b.getMinZ(),b.getMaxX(),b.getMaxZ());}
    }
    private final ServerLevel level;
    private final BlockPos search;
    private final Direction.Axis axis;
    private final BlockUtil.FoundRectangle rectangle;
    private final boolean fallback;
    private final List<Write> writes;
    private final Map<BlockPos,BlockState> readStates;
    private final Map<ChunkPos,LevelChunk> readChunks;
    private final List<Column> readHeights;
    private final Border border;
    private final int minY,maxY,logicalHeight;
    private NetherPortalFramePlan(Reader reader,BlockPos search,Direction.Axis axis,BlockPos corner,boolean fallback,List<Write> writes){
        this.level=reader.level;this.search=search.immutable();this.axis=axis;this.rectangle=new BlockUtil.FoundRectangle(corner.immutable(),2,3);this.fallback=fallback;this.writes=List.copyOf(writes);this.readStates=Collections.unmodifiableMap(new LinkedHashMap<>(reader.states));this.readChunks=Collections.unmodifiableMap(new LinkedHashMap<>(reader.chunks));this.readHeights=List.copyOf(reader.heights);this.border=reader.border;minY=level.getMinY();maxY=level.getMaxY();logicalHeight=level.getLogicalHeight();
    }
    public ServerLevel level(){return level;}
    public BlockPos search(){return search;}
    public Direction.Axis axis(){return axis;}
    public BlockUtil.FoundRectangle rectangle(){return rectangle;}
    public boolean fallback(){return fallback;}
    /** Exact native sequence, including repeated fallback air/interior writes and actual flags. */
    public List<Write> writes(){return writes;}
    public Map<BlockPos,BlockState> readStates(){return readStates;}
    public Set<BlockPos> writePositions(){var result=new LinkedHashSet<BlockPos>();for(var write:writes)result.add(write.position());return Collections.unmodifiableSet(result);}
    /** Revalidate actual geometry/heightmap/chunk/border inputs before a separate genuine transaction. */
    public void requireUnchanged(){
        if(minY!=level.getMinY()||maxY!=level.getMaxY()||logicalHeight!=level.getLogicalHeight()||!border.equals(Border.actual(level)))throw new IllegalStateException("Actual native portal search bounds changed");
        for(var entry:readChunks.entrySet()){var cp=entry.getKey();var current=level.getChunkSource().getChunkNow(cp.x(),cp.z());if(current==null)throw new UnloadedTrackException(level.dimension(),List.of(cp.getMiddleBlockPosition(search.getY())));if(current!=entry.getValue())throw new IllegalStateException("Actual native portal read chunk identity changed at "+cp);}
        for(var entry:readStates.entrySet())if(TrackPortalAccess.state(level,entry.getKey())!=entry.getValue())throw new IllegalStateException("Actual native portal read state changed at "+entry.getKey());
        for(var column:readHeights){var chunk=level.getChunkSource().getChunkNow(column.x()>>4,column.z()>>4);if(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING,column.x()&15,column.z()&15)+1!=column.firstAvailable())throw new IllegalStateException("Actual native portal heightmap changed at "+column.x()+","+column.z());}
    }
    /** Cargo/conditional native owner preflight; never deletes, clones or reassigns an actual owner. */
    public void requireNoBlockEntityWrites(){
        requireUnchanged();for(var pos:writePositions()){
            if(!level.isInValidBounds(pos))throw new IllegalStateException("Actual native portal write is outside world bounds at "+pos);
            var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);var state=TrackPortalAccess.state(level,pos);
            if(state.hasBlockEntity()||state.getBlock() instanceof EntityBlock||TrackBlockEntityAccess.existing(level,pos)!=null||chunk.getBlockEntityNbt(pos)!=null)throw new IllegalStateException("Actual native portal write is obstructed by owner/cargo at "+pos);
        }
    }
    /** Native search only. Empty is native insufficient vertical bounds; unknown terrain always throws. */
    public static Optional<NetherPortalFramePlan> find(ServerLevel level,BlockPos search,Direction.Axis axis){
        if(!axis.isHorizontal())throw new IllegalArgumentException("Native portal axis must be horizontal");
        var reader=new Reader(level);var along=Direction.get(Direction.AxisDirection.POSITIVE,axis);var border=level.getWorldBorder();int ceiling=Math.min(level.getMaxY(),level.getMinY()+level.getLogicalHeight()-1);
        double best=-1,secondary=-1;BlockPos chosen=null,second=null;
        for(var mutable:BlockPos.spiralAround(search,16,Direction.EAST,Direction.SOUTH)){
            var column=mutable.immutable();
            // Heightmap reads on native out-of-border columns cannot affect selection. Skip their
            // derived cache read rather than forcing irrelevant unknown chunks at the border.
            if(!border.isWithinBounds(column)||!border.isWithinBounds(column.relative(along)))continue;
            int top=Math.min(ceiling,reader.height(column.getX(),column.getZ()));
            for(int y=top;y>=level.getMinY();y--){
                if(!reader.replaceable(column.atY(y)))continue;
                int upper=y;while(y>level.getMinY()&&reader.replaceable(column.atY(y-1)))y--;
                if(y+4>ceiling)continue;int gap=upper-y;if(gap>0&&gap<3)continue;
                var candidate=column.atY(y);if(!reader.canHost(candidate,along,0))continue;
                double distance=search.distSqr(candidate);
                if(reader.canHost(candidate,along,-1)&&reader.canHost(candidate,along,1)&&(best==-1||best>distance)){best=distance;chosen=candidate;}
                if(best==-1&&(secondary==-1||secondary>distance)){secondary=distance;second=candidate;}
            }
        }
        if(best==-1&&secondary!=-1){chosen=second;best=secondary;}
        boolean fallback=best==-1;var writes=new ArrayList<Write>();
        if(fallback){
            int lower=Math.max(level.getMinY()+1,70),upper=ceiling-9;if(upper<lower)return Optional.empty();
            chosen=border.clampToBounds(new BlockPos(search.getX()-along.getStepX(),Mth.clamp(search.getY(),lower,upper),search.getZ()-along.getStepZ()));
            var across=along.getClockWise();for(int depth=-1;depth<2;depth++)for(int width=0;width<2;width++)for(int height=-1;height<3;height++)writes.add(new Write(offset(chosen,along,across,width,height,depth),height<0?Blocks.OBSIDIAN.defaultBlockState():Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL));
        }
        var across=along.getClockWise();for(int width=-1;width<3;width++)for(int height=-1;height<4;height++)if(width==-1||width==2||height==-1||height==3)writes.add(new Write(offset(chosen,along,across,width,height,0),Blocks.OBSIDIAN.defaultBlockState(),Block.UPDATE_ALL));
        var portal=Blocks.NETHER_PORTAL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS,axis);for(int width=0;width<2;width++)for(int height=0;height<3;height++)writes.add(new Write(offset(chosen,along,across,width,height,0),portal,18));
        // Actual native fallback can overwrite solids, including BE/cargo. Snapshot every real
        // written cell now; cargo rejection belongs to the explicit separate preflight above.
        for(var write:writes)reader.state(write.position());
        return Optional.of(new NetherPortalFramePlan(reader,search,axis,chosen,fallback,writes));
    }
    private static BlockPos offset(BlockPos corner,Direction along,Direction across,int width,int height,int depth){return corner.offset(width*along.getStepX()+depth*across.getStepX(),height,width*along.getStepZ()+depth*across.getStepZ());}
    private static final class Reader {
        final ServerLevel level;final Border border;final Map<BlockPos,BlockState> states=new LinkedHashMap<>();final Map<ChunkPos,LevelChunk> chunks=new LinkedHashMap<>();final List<Column> heights=new ArrayList<>();
        Reader(ServerLevel level){this.level=level;border=Border.actual(level);}
        LevelChunk chunk(BlockPos pos){var cp=ChunkPos.containing(pos);var current=level.getChunkSource().getChunkNow(cp.x(),cp.z());if(current==null)throw new UnloadedTrackException(level,pos);var previous=chunks.putIfAbsent(cp,current);if(previous!=null&&previous!=current)throw new IllegalStateException("Actual native portal chunk identity changed during selection");return current;}
        BlockState state(BlockPos pos){if(!level.isInValidBounds(pos))return TrackPortalAccess.state(level,pos);var actual=chunk(pos).getBlockState(pos);var previous=states.putIfAbsent(pos.immutable(),actual);if(previous!=null&&previous!=actual)throw new IllegalStateException("Actual native portal world changed during selection");return actual;}
        int height(int x,int z){if(x < -30_000_000||z < -30_000_000||x>=30_000_000||z>=30_000_000)return level.getSeaLevel()+1;int value=chunk(new BlockPos(x,level.getMinY(),z)).getHeight(Heightmap.Types.MOTION_BLOCKING,x&15,z&15)+1;heights.add(new Column(x,z,value));return value;}
        boolean replaceable(BlockPos pos){var state=state(pos);return state.canBeReplaced()&&state.getFluidState().isEmpty();}
        boolean canHost(BlockPos corner,Direction along,int depth){var across=along.getClockWise();for(int width=-1;width<3;width++)for(int height=-1;height<4;height++){var pos=offset(corner,along,across,width,height,depth);if(height<0&&!state(pos).isSolid()||height>=0&&!replaceable(pos))return false;}return true;}
    }
}
