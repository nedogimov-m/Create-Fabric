package com.simibubi.create.test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Reads real screenshot pixels in the source label band; a queued string or empty dark bar cannot pass. */
public final class StationMapPixelAudit {
    public record Receipt(String label,int mapLeft,int mapTop,int mapRight,int mapBottom,
        int labelLeft,int labelTop,int labelRight,int labelBottom,int actualContrastingGlyphPixels,int occupiedHorizontalBins,
        int occupiedTextRows,int minimumGlyphPixels,int minimumPixelsPerBin,int minimumTextRows) {}

    public static Receipt inspect(Path screenshot,String label,int decorationX,int decorationY,int fontWidth){
        final BufferedImage image;
        try{image=ImageIO.read(screenshot.toFile());}catch(IOException error){throw new IllegalStateException(error);}
        require(image!=null,"Actual native screenshot must decode as PNG");
        int width=image.getWidth(),height=image.getHeight(),left=width/4,right=3*width/4,top=height/16,bottom=15*height/16;
        var visited=new boolean[width*height];var queue=new int[width*height];int[] map=null;int largest=0;
        // This fixture uses a real map of its flat grassy world. Its connected green map plane
        // locates the actual projected map independently of HUD pixels or the requested text pose.
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){
            int first=y*width+x;if(visited[first]||!mapGreen(image.getRGB(x,y)))continue;
            int head=0,tail=0,minX=x,maxX=x,minY=y,maxY=y;queue[tail++]=first;visited[first]=true;
            while(head<tail){
                int at=queue[head++],px=at%width,py=at/width;minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
                for(int direction=0;direction<4;direction++){
                    int nx=px+(direction==0?-1:direction==1?1:0),ny=py+(direction==2?-1:direction==3?1:0);
                    if(nx<left||nx>=right||ny<top||ny>=bottom)continue;int next=ny*width+nx;
                    if(!visited[next]&&mapGreen(image.getRGB(nx,ny))){visited[next]=true;queue[tail++]=next;}
                }
            }
            if(tail>largest&&minX<width/2&&maxX>width/2&&minY<height/2&&maxY>height/2&&maxX-minX>=50&&maxY-minY>=50){largest=tail;map=new int[]{minX,minY,maxX+1,maxY+1};}
        }
        require(map!=null,"Actual screenshot must contain the fixture's real projected grassy map plane");
        float sx=(map[2]-map[0])/128f,sy=(map[3]-map[1])/128f;
        float labelX=decorationX/2f+64+.8f*(-fontWidth/2f+.5f),labelY=decorationY/2f+70;
        int x0=Math.max(0,(int)Math.floor(map[0]+(labelX-.8f)*sx)),x1=Math.min(width,(int)Math.ceil(map[0]+(labelX+.8f*fontWidth+.8f)*sx));
        int y0=Math.max(0,(int)Math.floor(map[1]+(labelY-.8f)*sy)),y1=Math.min(height,(int)Math.ceil(map[1]+(labelY+7.2f)*sy));
        require(x1>x0&&y1>y0,"Actual native projected source label region cannot be empty");
        // Native world text is light-map tinted (silver/blue here), not an unlit RGB-white mask.
        // Compare each column with its actual margins outside the glyph body. The higher margin
        // also rejects the station's bright icon tile where it overlaps the top of the label band.
        var background=new int[x1-x0];for(int x=x0;x<x1;x++)background[x-x0]=Math.max(luminance(image.getRGB(x,y0)),luminance(image.getRGB(x,y1-1)));
        int contrasting=0;var bins=new int[4];var textRows=new boolean[y1-y0];
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){
            int reference=background[x-x0],contrast=luminance(image.getRGB(x,y))-reference;
            if(contrast<Math.max(10,reference/12))continue;
            contrasting++;bins[Math.min(3,(x-x0)*4/(x1-x0))]++;textRows[y-y0]=true;
        }
        int minimum=Math.max(12,Math.round(fontWidth*sx*sy*.12f)),minimumPerBin=Math.max(2,minimum/16),minimumRows=Math.max(2,(y1-y0)/4);
        int occupied=0;for(int count:bins)if(count>=minimumPerBin)occupied++;
        int occupiedRows=0;for(boolean row:textRows)if(row)occupiedRows++;
        require(contrasting>=minimum&&occupied>=3&&occupiedRows>=minimumRows,"Actual screenshot station label '"+label+"' has no readable glyph contrast across its source text band: pixels="+contrasting+", required="+minimum+", bins="+occupied+", rows="+occupiedRows+", ROI=["+x0+","+y0+","+x1+","+y1+"]");
        return new Receipt(label,map[0],map[1],map[2],map[3],x0,y0,x1,y1,contrasting,occupied,occupiedRows,minimum,minimumPerBin,minimumRows);
    }
    private static int luminance(int rgb){return ((rgb>>16&255)*3+(rgb>>8&255)*6+(rgb&255))/10;}
    private static boolean mapGreen(int rgb){int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;return g>40&&r>20&&g>r*1.2&&g>b*1.4;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private StationMapPixelAudit(){}
}
