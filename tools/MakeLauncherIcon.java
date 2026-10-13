import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 生成自适应启动图标的前景层 PNG（tools/MakeLauncherIcon.java）。
 *
 * 设计（墨绿金 · 静奢记账，与原型 brand-mark / 我的页头像同源）：
 * - 透明底，居中黄铜金衬线「账」字（亮档 #C9A96E，深墨绿底上更清晰）；
 * - 432×432 = 108dp @ 4x，字形约 210px，落在 66dp 安全区（264px 圆）内。
 * - 同一 PNG 兼作 Android 13+ monochrome 层（系统取 alpha 着色）。
 *
 * 用法：java tools/MakeLauncherIcon.java <输出路径.png>
 * 字体：Windows 自带 Noto Serif SC（可变字重，默认 Regular）。
 */
public class MakeLauncherIcon {
    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "ic_launcher_foreground.png";
        int size = 432;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Font base = Font.createFont(Font.TRUETYPE_FONT, new File("C:/Windows/Fonts/NotoSerifSC-VF.ttf"));
        Font font = base.deriveFont(Font.PLAIN, 210f);
        FontRenderContext frc = g.getFontRenderContext();
        TextLayout layout = new TextLayout("账", font, frc);
        Rectangle2D bounds = layout.getPixelBounds(frc, 0, 0);

        float x = (float) ((size - bounds.getWidth()) / 2 - bounds.getX());
        float y = (float) ((size - bounds.getHeight()) / 2 - bounds.getY());
        g.setColor(new Color(0xC9, 0xA9, 0x6E, 0xFF));
        layout.draw(g, x, y);
        g.dispose();
        ImageIO.write(img, "png", new File(out));
        System.out.println("written: " + out + " (" + size + "x" + size + ")");
    }
}
